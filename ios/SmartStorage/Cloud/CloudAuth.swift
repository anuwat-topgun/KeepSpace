import AuthenticationServices
import CryptoKit
import Foundation
import Security
import UIKit

enum CloudConfiguration {
    static func clientID(for provider: CloudProvider) -> String {
        let key = provider == .googleDrive ? "KEEP_SPACE_GOOGLE_CLIENT_ID" : "KEEP_SPACE_MICROSOFT_CLIENT_ID"
        let value = (Bundle.main.object(forInfoDictionaryKey: key) as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.contains("$(") ? "" : value
    }

    static func redirectURI(for provider: CloudProvider) -> String {
        provider == .googleDrive ? "com.keepspace.app:/oauth2redirect" : "msauth.com.keepspace.app://auth"
    }

    static func endpoints(for provider: CloudProvider) -> (authorize: URL, token: URL, revoke: URL?) {
        switch provider {
        case .googleDrive:
            (URL(string: "https://accounts.google.com/o/oauth2/v2/auth")!, URL(string: "https://oauth2.googleapis.com/token")!, URL(string: "https://oauth2.googleapis.com/revoke")!)
        case .oneDrive:
            (URL(string: "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize")!, URL(string: "https://login.microsoftonline.com/consumers/oauth2/v2.0/token")!, nil)
        }
    }

    static func scope(for provider: CloudProvider) -> String {
        switch provider {
        case .googleDrive: "openid email profile https://www.googleapis.com/auth/drive.file"
        case .oneDrive: "openid profile email offline_access Files.ReadWrite.AppFolder"
        }
    }
}

@MainActor
final class CloudAuthenticator: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    func connect(_ provider: CloudProvider) async throws -> (OAuthToken, String?) {
        let clientID = CloudConfiguration.clientID(for: provider)
        guard !clientID.isEmpty else { throw CloudError.notConfigured(provider) }
        let verifier = Self.randomURLSafe(bytes: 32)
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64URLEncoded
        let state = Self.randomURLSafe(bytes: 24)
        let endpoint = CloudConfiguration.endpoints(for: provider)
        var components = URLComponents(url: endpoint.authorize, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: CloudConfiguration.redirectURI(for: provider)),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: CloudConfiguration.scope(for: provider)),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
        ]
        if provider == .googleDrive {
            components.queryItems?.append(contentsOf: [URLQueryItem(name: "access_type", value: "offline"), URLQueryItem(name: "prompt", value: "consent")])
        }
        let callback = try await callbackURL(from: components.url!, scheme: provider == .googleDrive ? "com.keepspace.app" : "msauth.com.keepspace.app")
        let callbackItems = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        guard callbackItems.first(where: { $0.name == "state" })?.value == state else {
            throw CloudError.invalidResponse("The cloud sign-in response could not be verified.")
        }
        if let message = callbackItems.first(where: { $0.name == "error_description" })?.value
            ?? callbackItems.first(where: { $0.name == "error" })?.value {
            throw CloudError.invalidResponse(message)
        }
        guard let code = callbackItems.first(where: { $0.name == "code" })?.value else {
            throw CloudError.invalidResponse("The cloud provider returned no authorization code.")
        }
        let token = try await exchange(code: code, verifier: verifier, provider: provider, clientID: clientID)
        return (token, Self.accountName(from: token.idToken) ?? provider.title)
    }

    func refresh(_ token: OAuthToken, provider: CloudProvider) async throws -> OAuthToken {
        guard token.expiresAt.timeIntervalSinceNow < 120 else { return token }
        guard let refreshToken = token.refreshToken else { throw CloudError.disconnected(provider) }
        let clientID = CloudConfiguration.clientID(for: provider)
        let response = try await tokenRequest(provider: provider, fields: [
            "client_id": clientID, "grant_type": "refresh_token", "refresh_token": refreshToken,
            "scope": CloudConfiguration.scope(for: provider),
        ])
        return OAuthToken(accessToken: response.accessToken, refreshToken: response.refreshToken ?? refreshToken,
                          expiresAt: Date().addingTimeInterval(response.expiresIn), idToken: response.idToken ?? token.idToken)
    }

    func revoke(_ token: OAuthToken, provider: CloudProvider) async {
        guard let endpoint = CloudConfiguration.endpoints(for: provider).revoke else { return }
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Self.form(["token": token.refreshToken ?? token.accessToken])
        _ = try? await URLSession.shared.data(for: request)
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }

    private func callbackURL(from url: URL, scheme: String) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: scheme) { callback, error in
                if let callback { continuation.resume(returning: callback) }
                else { continuation.resume(throwing: error ?? CloudError.invalidResponse("Cloud sign-in was cancelled.")) }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.session = session
            guard session.start() else {
                continuation.resume(throwing: CloudError.invalidResponse("Could not open the cloud sign-in page."))
                return
            }
        }
    }

    private func exchange(code: String, verifier: String, provider: CloudProvider, clientID: String) async throws -> OAuthToken {
        let response = try await tokenRequest(provider: provider, fields: [
            "client_id": clientID, "code": code, "code_verifier": verifier,
            "redirect_uri": CloudConfiguration.redirectURI(for: provider), "grant_type": "authorization_code",
        ])
        return OAuthToken(accessToken: response.accessToken, refreshToken: response.refreshToken,
                          expiresAt: Date().addingTimeInterval(response.expiresIn), idToken: response.idToken)
    }

    private struct TokenResponse: Decodable {
        let accessToken: String
        let refreshToken: String?
        let expiresIn: TimeInterval
        let idToken: String?
        enum CodingKeys: String, CodingKey {
            case accessToken = "access_token", refreshToken = "refresh_token", expiresIn = "expires_in", idToken = "id_token"
        }
    }

    private func tokenRequest(provider: CloudProvider, fields: [String: String]) async throws -> TokenResponse {
        var request = URLRequest(url: CloudConfiguration.endpoints(for: provider).token)
        request.httpMethod = "POST"
        request.httpBody = Self.form(fields)
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw CloudError.invalidResponse("Cloud sign-in failed: \(String(data: data, encoding: .utf8)?.prefix(240) ?? "Unknown error")")
        }
        return try JSONDecoder().decode(TokenResponse.self, from: data)
    }

    private static func form(_ fields: [String: String]) -> Data {
        fields.map { key, value in
            let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
            return "\(key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key)=\(value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value)"
        }.sorted().joined(separator: "&").data(using: .utf8)!
    }

    private static func randomURLSafe(bytes: Int) -> String {
        var data = Data(count: bytes)
        data.withUnsafeMutableBytes { buffer in _ = SecRandomCopyBytes(kSecRandomDefault, bytes, buffer.baseAddress!) }
        return data.base64URLEncoded
    }

    private static func accountName(from idToken: String?) -> String? {
        guard let part = idToken?.split(separator: ".").dropFirst().first,
              let data = Data(base64URLEncoded: String(part)),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return (json["email"] as? String) ?? (json["preferred_username"] as? String) ?? (json["name"] as? String)
    }
}

private extension Data {
    var base64URLEncoded: String { base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
    init?(base64URLEncoded value: String) {
        var base64 = value.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
        self.init(base64Encoded: base64)
    }
}

enum TokenKeychain {
    static func save(_ token: OAuthToken, provider: CloudProvider) throws {
        let data = try JSONEncoder().encode(token)
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.keepspace.cloud", kSecAttrAccount as String: provider.rawValue]
        SecItemDelete(query as CFDictionary)
        var insert = query
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw CloudError.invalidResponse("Could not store cloud credentials securely.") }
    }

    static func load(_ provider: CloudProvider) -> OAuthToken? {
        var query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.keepspace.cloud", kSecAttrAccount as String: provider.rawValue]
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(OAuthToken.self, from: data)
    }

    static func remove(_ provider: CloudProvider) {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.keepspace.cloud", kSecAttrAccount as String: provider.rawValue]
        SecItemDelete(query as CFDictionary)
    }
}
