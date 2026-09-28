@preconcurrency import AVFoundation
import CoreLocation
import Photos

/// Changes to the user's library. Every destructive change goes through PhotoKit, which always
/// shows the system confirmation and moves items to Recently Deleted (recoverable for 30 days).
enum LibraryActions {
    static func delete(ids: [String], bytes: Int64) async -> DeletionOutcome {
        guard !ids.isEmpty else { return .cancelled }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.deleteAssets(PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil))
            }
            return .deleted(count: ids.count, bytes: bytes)
        } catch {
            return isUserCancel(error) ? .cancelled : .failed(error.localizedDescription)
        }
    }

    // MARK: - Compression

    enum CompressionError: LocalizedError {
        case unavailable, notLocal, exportFailed(String?), notWorthIt(saved: Int64)

        var errorDescription: String? {
            switch self {
            case .unavailable: "This video is no longer in your library."
            case .notLocal: "This video is stored in iCloud only. Download it in Photos first."
            case .exportFailed(let reason): "Compression failed\(reason.map { ": \($0)" } ?? ".")"
            case .notWorthIt(let saved): "Compression would only save \(saved.formattedBytes), so the original was kept."
            }
        }
    }

    enum CompressionOutcome: Equatable, Sendable {
        case replaced(originalBytes: Int64, newBytes: Int64)
        case cancelled
    }

    /// Re-encodes a video, then — in one PhotoKit change the user confirms — saves the smaller copy
    /// (keeping date, location and favourite) and moves the original to Recently Deleted.
    static func compress(
        id: String,
        originalBytes: Int64,
        preset: CompressionPreset,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws -> CompressionOutcome {
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else {
            throw CompressionError.unavailable
        }
        let creationDate = asset.creationDate
        let isFavorite = asset.isFavorite
        let location = asset.location.map { LocationSnapshot($0) }

        guard let avAsset = await localAVAsset(for: asset) else { throw CompressionError.notLocal }
        let output = FileManager.default.temporaryDirectory.appending(path: "keepspace-\(UUID().uuidString).mp4")
        defer { try? FileManager.default.removeItem(at: output) }

        try await export(avAsset, preset: preset, to: output, progress: progress)

        let newBytes = Int64((try? output.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
        guard CompressionEstimator.shouldReplace(originalBytes: originalBytes, compressedBytes: newBytes) else {
            throw CompressionError.notWorthIt(saved: max(0, originalBytes - newBytes))
        }

        do {
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                let options = PHAssetResourceCreationOptions()
                options.shouldMoveFile = true
                request.addResource(with: .video, fileURL: output, options: options)
                request.creationDate = creationDate
                request.isFavorite = isFavorite
                request.location = location?.location
                PHAssetChangeRequest.deleteAssets(PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil))
            }
        } catch {
            if isUserCancel(error) { return .cancelled }
            throw CompressionError.exportFailed(error.localizedDescription)
        }
        return .replaced(originalBytes: originalBytes, newBytes: newBytes)
    }

    private static func export(_ asset: AVAsset, preset: CompressionPreset, to url: URL, progress: @escaping @Sendable (Double) -> Void) async throws {
        let presetName = preset == .hd1080 ? AVAssetExportPresetHEVC1920x1080 : AVAssetExportPreset1280x720
        guard let session = AVAssetExportSession(asset: asset, presetName: presetName) else {
            throw CompressionError.exportFailed("This video format isn't supported.")
        }
        session.outputURL = url
        session.outputFileType = .mp4
        session.shouldOptimizeForNetworkUse = true

        let box = SessionBox(session)
        let poller = Task {
            while !Task.isCancelled {
                progress(Double(box.session.progress))
                try? await Task.sleep(for: .milliseconds(250))
            }
        }
        await box.session.export()
        poller.cancel()
        progress(1)
        guard box.session.status == .completed else {
            throw CompressionError.exportFailed(box.session.error?.localizedDescription)
        }
    }

    private static func localAVAsset(for asset: PHAsset) async -> AVAsset? {
        let options = PHVideoRequestOptions()
        options.isNetworkAccessAllowed = false
        options.version = .current
        options.deliveryMode = .highQualityFormat
        let box: AssetBox = await withCheckedContinuation { continuation in
            PHImageManager.default().requestAVAsset(forVideo: asset, options: options) { avAsset, _, _ in
                continuation.resume(returning: AssetBox(avAsset))
            }
        }
        return box.asset
    }

    // MARK: - Helpers

    private static func isUserCancel(_ error: Error) -> Bool {
        (error as? PHPhotosError)?.code == .userCancelled
    }

    /// CLLocation isn't Sendable; carry the values into the change block instead.
    private struct LocationSnapshot: Sendable {
        let latitude: Double, longitude: Double, altitude: Double, timestamp: Date

        init(_ location: CLLocation) {
            latitude = location.coordinate.latitude
            longitude = location.coordinate.longitude
            altitude = location.altitude
            timestamp = location.timestamp
        }

        var location: CLLocation {
            CLLocation(coordinate: .init(latitude: latitude, longitude: longitude), altitude: altitude,
                       horizontalAccuracy: 0, verticalAccuracy: 0, timestamp: timestamp)
        }
    }

    /// AVFoundation types predate Sendable; these are only touched from one task at a time.
    private final class SessionBox: @unchecked Sendable {
        let session: AVAssetExportSession
        init(_ session: AVAssetExportSession) { self.session = session }
    }

    private final class AssetBox: @unchecked Sendable {
        let asset: AVAsset?
        init(_ asset: AVAsset?) { self.asset = asset }
    }
}
