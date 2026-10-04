import Foundation
import Observation
import OSLog
import Photos
import CryptoKit

@MainActor
@Observable
final class CloudStore {
    private(set) var connections: [CloudConnection]
    private(set) var uploads: [CloudUploadItem]
    var preferences: UploadPreferences { didSet { preferences.keepLocalCopies = true; persist() } }
    var notice: String?

    private let authenticator = CloudAuthenticator()
    private let logger = Logger(subsystem: "com.keepspace.app", category: "CloudBackup")
    private var uploadTask: Task<Void, Never>?
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let savedConnections = Self.decode([CloudConnection].self, from: defaults.data(forKey: "cloud.connections")) ?? []
        connections = CloudProvider.allCases.map { provider in
            var saved = savedConnections.first(where: { $0.provider == provider }) ?? CloudConnection(provider: provider)
            saved.isConfigured = !CloudConfiguration.clientID(for: provider).isEmpty
            saved.isConnected = TokenKeychain.load(provider) != nil && saved.isConnected
            return saved
        }
        preferences = Self.decode(UploadPreferences.self, from: defaults.data(forKey: "cloud.preferences")) ?? UploadPreferences()
        var restored = (try? Data(contentsOf: Self.queueURL)).flatMap { try? JSONDecoder().decode([CloudUploadItem].self, from: $0) } ?? []
        restored = restored.map { item in
            var copy = item
            if copy.status == .uploading || copy.status == .verifying { copy.status = .waiting; copy.progress = 0 }
            return copy
        }
        uploads = restored
        if restored.contains(where: { $0.status == .waiting }) { resumePending() }
    }

    var activeCount: Int { uploads.count { [.waiting, .uploading, .verifying].contains($0.status) } }

    var activeProgress: Int {
        let active = uploads.filter { [.waiting, .uploading, .verifying].contains($0.status) }
        // Uploads run serially; waiting files stay at zero, so show the current file rather
        // than averaging it with the queue (which would misleadingly remain near 0%).
        return active.map(\.progress).max() ?? 0
    }

    func connect(_ provider: CloudProvider) {
        guard CloudConfiguration.clientID(for: provider).isEmpty == false else {
            notice = CloudError.notConfigured(provider).localizedDescription
            return
        }
        Task {
            do {
                let (token, account) = try await authenticator.connect(provider)
                try TokenKeychain.save(token, provider: provider)
                updateConnection(provider, connected: true, accountName: account)
                notice = localizedFormat("%@ connected. Backups go directly to this account.", provider.title)
                logger.info("Connected cloud provider: \(provider.rawValue, privacy: .public)")
            } catch {
                notice = error.localizedDescription
                logger.error("Cloud sign-in failed for \(provider.rawValue, privacy: .public): \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func disconnect(_ provider: CloudProvider) {
        let token = TokenKeychain.load(provider)
        TokenKeychain.remove(provider)
        updateConnection(provider, connected: false, accountName: nil)
        notice = localizedFormat("%@ disconnected. Existing cloud files were not changed.", provider.title)
        if let token { Task { await authenticator.revoke(token, provider: provider) } }
    }

    func enqueue(provider: CloudProvider, scope: CloudBackupScope, folder: String, items: [MediaItem]) {
        guard connections.contains(where: { $0.provider == provider && $0.isConnected }) else {
            notice = localizedFormat("Connect %@ before starting a backup.", provider.title)
            return
        }
        let cleanFolder = folder.trimmingCharacters(in: CharacterSet(charactersIn: "/ ")).isEmpty ? scope.title : folder.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        let queued = items.map { media in
            let suffix = Self.stableSuffix(media.id)
            let original = media.fileName ?? "\(scope.rawValue)-\(suffix)"
            let name: String
            if preferences.renameAutomatically {
                let ext = (original as NSString).pathExtension.isEmpty ? "jpg" : (original as NSString).pathExtension
                name = "KeepSpace-\(media.creationDate.formatted(.iso8601.year().month().day()))-\(suffix).\(ext)"
            } else { name = original }
            return CloudUploadItem(provider: provider, scope: scope, assetID: media.id, sourceName: name,
                                   destinationFolder: cleanFolder, bytes: media.bytes,
                                   sourceModifiedAt: media.modifiedAt)
        }
        guard !queued.isEmpty else { notice = localizedFormat("No %@ are available to back up.", scope.title.localizedUI.lowercased()); return }
        uploads.append(contentsOf: queued)
        notice = localizedFormat("Queued %d items for %@.", queued.count, provider.title)
        persist()
        resumePending()
    }

    func state(for item: MediaItem) -> AssetCloudState {
        AssetCloudState.resolve(for: item, uploads: uploads)
    }

    func cancelPending() {
        uploadTask?.cancel()
        for index in uploads.indices where [.waiting, .uploading, .verifying].contains(uploads[index].status) {
            uploads[index].status = .cancelled
        }
        notice = "Pending backup cancelled.".localizedUI
        persist()
    }

    func retryFailed() {
        for index in uploads.indices where uploads[index].status == .failed {
            uploads[index].status = .waiting; uploads[index].progress = 0; uploads[index].error = nil
        }
        persist()
        resumePending()
    }

    func resumePending() {
        guard uploadTask == nil else { return }
        uploadTask = Task {
            defer { uploadTask = nil }
            while !Task.isCancelled, let id = uploads.first(where: { $0.status == .waiting })?.id {
                do { try await upload(id: id) }
                catch is CancellationError { return }
                catch {
                    guard let index = uploads.firstIndex(where: { $0.id == id }) else { continue }
                    uploads[index].attempt += 1
                    uploads[index].error = error.localizedDescription
                    if uploads[index].attempt >= 3 { uploads[index].status = .failed }
                    else {
                        uploads[index].status = .waiting
                        try? await Task.sleep(for: .seconds(pow(2.0, Double(uploads[index].attempt))))
                    }
                    logger.error("Cloud upload attempt failed for \(self.uploads[index].provider.rawValue, privacy: .public): \(error.localizedDescription, privacy: .public)")
                    persist()
                }
            }
        }
    }

    private func upload(id: UUID) async throws {
        guard let initial = uploads.first(where: { $0.id == id }) else { return }
        updateUpload(id) { $0.status = .uploading; $0.progress = 5; $0.error = nil }
        var token = try requireToken(initial.provider)
        token = try await authenticator.refresh(token, provider: initial.provider)
        try TokenKeychain.save(token, provider: initial.provider)
        let file = try await exportAsset(initial.assetID, suggestedName: initial.sourceName)
        defer { try? FileManager.default.removeItem(at: file) }
        updateUpload(id) { $0.progress = 25 }
        let remoteID: String
        switch initial.provider {
        case .googleDrive: remoteID = try await uploadGoogle(initial, file: file, accessToken: token.accessToken)
        case .oneDrive: remoteID = try await uploadOneDrive(initial, file: file, accessToken: token.accessToken)
        }
        updateUpload(id) { $0.status = .verifying; $0.progress = 92; $0.remoteID = remoteID }
        try await verify(provider: initial.provider, remoteID: remoteID, accessToken: token.accessToken)
        updateUpload(id) { $0.status = .backedUp; $0.progress = 100; $0.remoteID = remoteID }
        logger.info("Cloud upload verified for \(initial.provider.rawValue, privacy: .public); remote ID: \(remoteID, privacy: .private(mask: .hash))")
    }

    private func uploadGoogle(_ item: CloudUploadItem, file: URL, accessToken: String) async throws -> String {
        let parent = try await ensureGoogleFolder(item.destinationFolder, accessToken: accessToken)
        let metadata: [String: Any] = ["name": item.sourceName, "parents": [parent]]
        let start = try await request(url: URL(string: "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")!,
                                      method: "POST", token: accessToken, body: try JSONSerialization.data(withJSONObject: metadata),
                                      contentType: "application/json; charset=UTF-8", headers: ["X-Upload-Content-Type": contentType(item.sourceName)])
        guard let location = start.response.value(forHTTPHeaderField: "Location"), let uploadURL = URL(string: location) else {
            throw CloudError.invalidResponse("Google Drive did not start an upload session.")
        }
        updateUpload(item.id) { $0.progress = 45 }
        let result = try await request(url: uploadURL, method: "PUT", token: accessToken, body: try await Self.readFile(file), contentType: contentType(item.sourceName))
        updateUpload(item.id) { $0.progress = 88 }
        return try json(result.data)["id"] as? String ?? { throw CloudError.invalidResponse("Google Drive returned no file ID.") }()
    }

    private func ensureGoogleFolder(_ folder: String, accessToken: String) async throws -> String {
        var parent = "root"
        for segment in (["KeepSpace"] + folder.split(separator: "/").map(String.init)) where !segment.isEmpty {
            let escaped = segment.replacingOccurrences(of: "'", with: "\\'")
            var components = URLComponents(string: "https://www.googleapis.com/drive/v3/files")!
            components.queryItems = [URLQueryItem(name: "q", value: "name='\(escaped)' and mimeType='application/vnd.google-apps.folder' and '\(parent)' in parents and trashed=false"),
                                     URLQueryItem(name: "fields", value: "files(id,name)"), URLQueryItem(name: "spaces", value: "drive")]
            let found = try await request(url: components.url!, method: "GET", token: accessToken)
            if let files = try json(found.data)["files"] as? [[String: Any]], let id = files.first?["id"] as? String { parent = id }
            else {
                let body = try JSONSerialization.data(withJSONObject: ["name": segment, "mimeType": "application/vnd.google-apps.folder", "parents": [parent]])
                let made = try await request(url: URL(string: "https://www.googleapis.com/drive/v3/files?fields=id")!, method: "POST", token: accessToken, body: body, contentType: "application/json")
                guard let id = try json(made.data)["id"] as? String else { throw CloudError.invalidResponse("Could not create a Google Drive folder.") }
                parent = id
            }
        }
        return parent
    }

    private func uploadOneDrive(_ item: CloudUploadItem, file: URL, accessToken: String) async throws -> String {
        try await ensureOneDriveFolder(item.destinationFolder, accessToken: accessToken)
        let path = (item.destinationFolder.split(separator: "/").map(String.init) + [item.sourceName]).map(pathEncode).joined(separator: "/")
        let url = URL(string: "https://graph.microsoft.com/v1.0/me/drive/special/approot:/\(path):/content?@microsoft.graph.conflictBehavior=rename")!
        updateUpload(item.id) { $0.progress = 45 }
        let result = try await request(url: url, method: "PUT", token: accessToken, body: try await Self.readFile(file), contentType: contentType(item.sourceName))
        updateUpload(item.id) { $0.progress = 88 }
        guard let id = try json(result.data)["id"] as? String else { throw CloudError.invalidResponse("OneDrive returned no file ID.") }
        return id
    }

    private func ensureOneDriveFolder(_ folder: String, accessToken: String) async throws {
        var path = ""
        for segment in folder.split(separator: "/").map(String.init) where !segment.isEmpty {
            let encodedPath = path.split(separator: "/").map { pathEncode(String($0)) }.joined(separator: "/")
            let checkURL = encodedPath.isEmpty
                ? URL(string: "https://graph.microsoft.com/v1.0/me/drive/special/approot")!
                : URL(string: "https://graph.microsoft.com/v1.0/me/drive/special/approot:/\(encodedPath)")!
            let childPath = encodedPath.isEmpty ? pathEncode(segment) : "\(encodedPath)/\(pathEncode(segment))"
            let childURL = URL(string: "https://graph.microsoft.com/v1.0/me/drive/special/approot:/\(childPath)")!
            if (try? await request(url: childURL, method: "GET", token: accessToken)) == nil {
                let childrenURL = URL(string: checkURL.absoluteString + "/children")!
                let body = try JSONSerialization.data(withJSONObject: ["name": segment, "folder": [:], "@microsoft.graph.conflictBehavior": "fail"])
                _ = try await request(url: childrenURL, method: "POST", token: accessToken, body: body, contentType: "application/json")
            }
            path = path.isEmpty ? segment : "\(path)/\(segment)"
        }
    }

    private func verify(provider: CloudProvider, remoteID: String, accessToken: String) async throws {
        let encoded = pathEncode(remoteID)
        let url = provider == .googleDrive
            ? URL(string: "https://www.googleapis.com/drive/v3/files/\(encoded)?fields=id,size,trashed")!
            : URL(string: "https://graph.microsoft.com/v1.0/me/drive/items/\(encoded)?select=id,size,deleted")!
        _ = try await request(url: url, method: "GET", token: accessToken)
    }

    private func exportAsset(_ id: String, suggestedName: String) async throws -> URL {
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject,
              let resource = PHAssetResource.assetResources(for: asset).first(where: { [.photo, .fullSizePhoto, .video, .fullSizeVideo].contains($0.type) }) else {
            throw CloudError.sourceUnavailable
        }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("KeepSpaceUploads", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("\(UUID().uuidString)-\(suggestedName)")
        let options = PHAssetResourceRequestOptions(); options.isNetworkAccessAllowed = true
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHAssetResourceManager.default().writeData(for: resource, toFile: url, options: options) { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            }
        }
        return url
    }

    private func requireToken(_ provider: CloudProvider) throws -> OAuthToken {
        guard let token = TokenKeychain.load(provider) else { throw CloudError.disconnected(provider) }
        return token
    }

    private func updateConnection(_ provider: CloudProvider, connected: Bool, accountName: String?) {
        guard let index = connections.firstIndex(where: { $0.provider == provider }) else { return }
        connections[index].isConnected = connected; connections[index].accountName = accountName
        persist()
    }

    private func updateUpload(_ id: UUID, change: (inout CloudUploadItem) -> Void) {
        guard let index = uploads.firstIndex(where: { $0.id == id }) else { return }
        change(&uploads[index]); persist()
    }

    private struct HTTPResult { let data: Data; let response: HTTPURLResponse }
    private func request(url: URL, method: String, token: String, body: Data? = nil, contentType: String? = nil,
                         headers: [String: String] = [:]) async throws -> HTTPResult {
        var request = URLRequest(url: url); request.httpMethod = method; request.httpBody = body
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        let configuration = URLSessionConfiguration.default
        configuration.allowsCellularAccess = !preferences.wifiOnly
        configuration.allowsExpensiveNetworkAccess = !preferences.wifiOnly
        let (data, response) = try await URLSession(configuration: configuration).data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw CloudError.invalidResponse("Cloud service rejected the request: \(String(data: data, encoding: .utf8)?.prefix(240) ?? "Unknown error")")
        }
        return HTTPResult(data: data, response: http)
    }

    private func json(_ data: Data) throws -> [String: Any] {
        guard let value = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw CloudError.invalidResponse("The cloud service returned invalid data.") }
        return value
    }
    private func pathEncode(_ value: String) -> String { value.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/?#"))) ?? value }
    private func contentType(_ name: String) -> String {
        switch (name as NSString).pathExtension.lowercased() {
        case "heic", "heif": "image/heic"
        case "png": "image/png"
        case "mov": "video/quicktime"
        case "mp4": "video/mp4"
        default: "image/jpeg"
        }
    }

    private func persist() {
        defaults.set(try? JSONEncoder().encode(connections), forKey: "cloud.connections")
        defaults.set(try? JSONEncoder().encode(preferences), forKey: "cloud.preferences")
        try? FileManager.default.createDirectory(at: Self.queueURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? JSONEncoder().encode(uploads).write(to: Self.queueURL, options: .atomic)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        var url = Self.queueURL; try? url.setResourceValues(values)
    }

    private static var queueURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("KeepSpace", isDirectory: true).appendingPathComponent("cloud-queue.json")
    }
    private static func decode<T: Decodable>(_ type: T.Type, from data: Data?) -> T? { data.flatMap { try? JSONDecoder().decode(type, from: $0) } }
    private static func stableSuffix(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).prefix(5).map { String(format: "%02x", $0) }.joined()
    }
    nonisolated private static func readFile(_ url: URL) async throws -> Data {
        try await Task.detached(priority: .utility) { try Data(contentsOf: url, options: .mappedIfSafe) }.value
    }
}
