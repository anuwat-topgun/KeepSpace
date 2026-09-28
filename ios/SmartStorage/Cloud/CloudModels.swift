import Foundation

enum CloudBackupScope: String, CaseIterable, Codable, Sendable {
    case photos, screenshots, receipts
    var title: String { rawValue.capitalized }
}

enum CloudUploadStatus: String, Codable, Sendable {
    case waiting, uploading, verifying, backedUp, failed, cancelled
    var title: String {
        switch self {
        case .backedUp: "Backed up"
        default: rawValue.capitalized
        }
    }
}

struct CloudConnection: Identifiable, Codable, Sendable {
    var id: CloudProvider { provider }
    let provider: CloudProvider
    var isConnected = false
    var accountName: String?
    var isConfigured = false
}

struct UploadPreferences: Codable, Sendable {
    var renameAutomatically = true
    var wifiOnly = true
    /// Always true in v1.1. Safe deletion after verified backup is deliberately out of scope.
    var keepLocalCopies = true
}

struct CloudUploadItem: Identifiable, Codable, Sendable {
    var id = UUID()
    let provider: CloudProvider
    let scope: CloudBackupScope
    let assetID: String
    let sourceName: String
    let destinationFolder: String
    let bytes: Int64
    var status: CloudUploadStatus = .waiting
    var progress = 0
    var attempt = 0
    var error: String?
    var remoteID: String?
}

struct OAuthToken: Codable, Sendable {
    var accessToken: String
    var refreshToken: String?
    var expiresAt: Date
    var idToken: String?
}

enum CloudError: LocalizedError {
    case notConfigured(CloudProvider)
    case disconnected(CloudProvider)
    case invalidResponse(String)
    case sourceUnavailable

    var errorDescription: String? {
        switch self {
        case .notConfigured(let provider): "Add the \(provider.title) OAuth client ID to the release configuration first."
        case .disconnected(let provider): "\(provider.title) is disconnected."
        case .invalidResponse(let detail): detail
        case .sourceUnavailable: "The source file is no longer available."
        }
    }
}
