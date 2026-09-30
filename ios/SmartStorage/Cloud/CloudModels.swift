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
    /// Used to distinguish a verified backup from an older version of an edited asset.
    var sourceModifiedAt: Date?
    var status: CloudUploadStatus = .waiting
    var progress = 0
    var attempt = 0
    var error: String?
    var remoteID: String?
}

/// The one status shown on a library tile. The newest job for the current asset version wins.
struct AssetCloudState: Equatable, Sendable {
    let status: CloudUploadStatus?
    let provider: CloudProvider?
    let progress: Int

    static let notBackedUp = AssetCloudState(status: nil, provider: nil, progress: 0)

    var isBackedUp: Bool { status == .backedUp }

    var title: String {
        switch status {
        case .waiting: "Waiting"
        case .uploading: "Uploading"
        case .verifying: "Verifying"
        case .backedUp: "Backed up"
        case .failed: "Failed"
        case .cancelled, nil: "Not backed up"
        }
    }

    static func resolve(for item: MediaItem, uploads: [CloudUploadItem]) -> AssetCloudState {
        let matching = uploads.filter { upload in
            guard upload.assetID == item.id else { return false }
            guard let uploadedVersion = upload.sourceModifiedAt else { return true }
            return abs(uploadedVersion.timeIntervalSince(item.modifiedAt)) < 1
        }
        guard let latest = matching.last else { return .notBackedUp }
        return AssetCloudState(status: latest.status, provider: latest.provider, progress: latest.progress)
    }
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
