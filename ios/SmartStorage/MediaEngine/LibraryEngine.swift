@preconcurrency import AVFoundation
import CryptoKit
import Photos
import UIKit

enum LibraryAccess: Sendable, Equatable {
    case notDetermined, authorized, limited, denied

    var canRead: Bool { self == .authorized || self == .limited }

    init(_ status: PHAuthorizationStatus) {
        switch status {
        case .authorized: self = .authorized
        case .limited: self = .limited
        case .notDetermined: self = .notDetermined
        default: self = .denied
        }
    }
}

/// Owns PhotoKit access. Holds `PHAsset` references internally and only hands out value types,
/// so callers on any actor can use the results safely.
actor LibraryEngine {
    struct PixelSize: Hashable, Sendable {
        let width: Int
        let height: Int
    }

    private let analyzer = ImageAnalyzer()
    private let screenshotAnalyzer = ScreenshotAnalyzer()
    private var assets: [String: PHAsset] = [:]
    private let imageManager = PHImageManager.default()

    static func currentAccess() -> LibraryAccess {
        LibraryAccess(PHPhotoLibrary.authorizationStatus(for: .readWrite))
    }

    static func requestAccess() async -> LibraryAccess {
        LibraryAccess(await PHPhotoLibrary.requestAuthorization(for: .readWrite))
    }

    /// Enumerates the whole library. `screenSizes` are the device's native pixel sizes, used to
    /// recognise screen recordings (PhotoKit has no subtype for them).
    func loadItems(screenSizes: Set<PixelSize>) -> [MediaItem] {
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        let result = PHAsset.fetchAssets(with: options)

        var items: [MediaItem] = []
        var map: [String: PHAsset] = [:]
        items.reserveCapacity(result.count)
        result.enumerateObjects { asset, _, _ in
            guard asset.mediaType == .image || asset.mediaType == .video else { return }
            map[asset.localIdentifier] = asset
            items.append(Self.item(for: asset, screenSizes: screenSizes))
        }
        assets = map
        return items
    }

    /// Analyzes the given photos, `concurrency` at a time. Reports progress, and hands completed
    /// results to `onBatch` every `batchSize` photos so callers can persist as they go.
    func analyze(
        _ photos: [MediaItem],
        concurrency: Int = 4,
        batchSize: Int = 25,
        progress: @escaping @Sendable (Int, Int) async -> Void,
        onBatch: @escaping @Sendable ([AnalyzedPhoto]) async -> Void
    ) async -> [AnalyzedPhoto] {
        var results: [AnalyzedPhoto] = []
        results.reserveCapacity(photos.count)
        var pending: [AnalyzedPhoto] = []
        var done = 0

        await withTaskGroup(of: AnalyzedPhoto?.self) { group in
            var iterator = photos.makeIterator()
            func enqueue() {
                guard let item = iterator.next() else { return }
                group.addTask { await self.analyzeOne(item) }
            }
            for _ in 0..<concurrency { enqueue() }
            while let next = await group.next() {
                if let next {
                    results.append(next)
                    pending.append(next)
                }
                done += 1
                if done % 10 == 0 || done == photos.count { await progress(done, photos.count) }
                if pending.count >= batchSize {
                    await onBatch(pending)
                    pending.removeAll()
                }
                enqueue()
            }
        }
        if !pending.isEmpty { await onBatch(pending) }
        return results
    }

    /// Reads screenshots (OCR + barcodes) with the same batching/progress contract as `analyze`.
    /// Photos passed here are paper-receipt candidates: read larger, text only.
    func analyzeScreenshots(
        _ screenshots: [MediaItem],
        concurrency: Int = 2,
        batchSize: Int = 25,
        progress: @escaping @Sendable (Int, Int) async -> Void,
        onBatch: @escaping @Sendable ([(MediaItem, ScreenshotInfo)]) async -> Void
    ) async -> [String: ScreenshotInfo] {
        var results: [String: ScreenshotInfo] = [:]
        var pending: [(MediaItem, ScreenshotInfo)] = []
        var done = 0
        await withTaskGroup(of: (MediaItem, ScreenshotInfo)?.self) { group in
            var iterator = screenshots.makeIterator()
            func enqueue() {
                guard let item = iterator.next() else { return }
                group.addTask { await self.readScreenshot(item) }
            }
            for _ in 0..<concurrency { enqueue() }
            while let next = await group.next() {
                if let next {
                    results[next.0.id] = next.1
                    pending.append(next)
                }
                done += 1
                if done % 5 == 0 || done == screenshots.count { await progress(done, screenshots.count) }
                if pending.count >= batchSize {
                    await onBatch(pending)
                    pending.removeAll()
                }
                enqueue()
            }
        }
        if !pending.isEmpty { await onBatch(pending) }
        return results
    }

    /// Replaces PhotoKit's non-public `fileSize` KVC with public APIs. Still images are streamed
    /// once so their exact byte count and SHA-256 arrive together. Videos use the public sample
    /// data lengths and are only streamed later when duplicate candidates need a hash.
    func measure(
        _ items: [MediaItem],
        concurrency: Int = 3,
        batchSize: Int = 50,
        progress: @escaping @Sendable (Int, Int) async -> Void,
        onBatch: @escaping @Sendable ([CachedHash]) async -> Void
    ) async -> [CachedHash] {
        var results: [CachedHash] = []
        results.reserveCapacity(items.count)
        var pending: [CachedHash] = []
        var done = 0

        await withTaskGroup(of: CachedHash?.self) { group in
            var iterator = items.makeIterator()
            func enqueue() {
                guard let item = iterator.next() else { return }
                group.addTask { await self.measureOne(item) }
            }
            for _ in 0..<concurrency { enqueue() }
            while let next = await group.next() {
                if let next {
                    results.append(next)
                    pending.append(next)
                }
                done += 1
                if done % 20 == 0 || done == items.count { await progress(done, items.count) }
                if pending.count >= batchSize {
                    await onBatch(pending)
                    pending.removeAll()
                }
                enqueue()
            }
        }
        if !pending.isEmpty { await onBatch(pending) }
        return results
    }

    /// SHA-256 of each item's original file, two at a time. Edited assets are skipped (what you see
    /// isn't the original file) and so are iCloud-only originals (nothing is downloaded).
    func hashFiles(
        _ items: [MediaItem],
        progress: @escaping @Sendable (Int, Int) async -> Void
    ) async -> [CachedHash] {
        var results: [CachedHash] = []
        var done = 0
        await withTaskGroup(of: CachedHash?.self) { group in
            var iterator = items.makeIterator()
            func enqueue() {
                guard let item = iterator.next() else { return }
                group.addTask { await self.hashFile(item) }
            }
            for _ in 0..<2 { enqueue() }
            while let next = await group.next() {
                if let next { results.append(next) }
                done += 1
                if done % 10 == 0 || done == items.count { await progress(done, items.count) }
                enqueue()
            }
        }
        return results
    }

    private func hashFile(_ item: MediaItem) async -> CachedHash? {
        guard let asset = assets[item.id] else { return nil }
        let resources = PHAssetResource.assetResources(for: asset)
        guard !resources.contains(where: { $0.type == .adjustmentData }),
              let resource = Self.primaryResource(in: resources),
              let read = await stream(resource) else { return nil }
        return CachedHash(assetID: item.id, modifiedAt: item.modifiedAt, bytes: read.bytes, hash: read.sha256)
    }

    private func measureOne(_ item: MediaItem) async -> CachedHash? {
        guard let asset = assets[item.id] else { return nil }
        if item.isVideo {
            guard let bytes = await videoBytes(of: asset) else { return nil }
            return CachedHash(assetID: item.id, modifiedAt: item.modifiedAt, bytes: bytes, hash: nil)
        }
        let resources = PHAssetResource.assetResources(for: asset)
        guard let resource = Self.primaryResource(in: resources), let read = await stream(resource) else { return nil }
        let edited = resources.contains { $0.type == .adjustmentData }
        return CachedHash(assetID: item.id, modifiedAt: item.modifiedAt, bytes: read.bytes, hash: edited ? nil : read.sha256)
    }

    /// Exact byte count + SHA-256 of a local original, read once. iCloud-only originals return nil.
    private func stream(_ resource: PHAssetResource) async -> (bytes: Int64, sha256: String)? {
        let options = PHAssetResourceRequestOptions()
        options.isNetworkAccessAllowed = false
        return await withCheckedContinuation { continuation in
            let box = StreamBox()
            PHAssetResourceManager.default().requestData(for: resource, options: options) { chunk in
                box.add(chunk)
            } completionHandler: { error in
                continuation.resume(returning: error == nil ? box.result() : nil)
            }
        }
    }

    /// PhotoKit delivers chunks serially on its own queue.
    private final class StreamBox: @unchecked Sendable {
        private var hasher = SHA256()
        private var count: Int64 = 0

        func add(_ data: Data) {
            count += Int64(data.count)
            hasher.update(data: data)
        }

        func result() -> (bytes: Int64, sha256: String) {
            (count, hasher.finalize().map { String(format: "%02x", $0) }.joined())
        }
    }

    /// A video's media payload size from public AVFoundation track metadata; no file read.
    private func videoBytes(of asset: PHAsset) async -> Int64? {
        let options = PHVideoRequestOptions()
        options.isNetworkAccessAllowed = false
        options.version = .original
        options.deliveryMode = .highQualityFormat
        let box: AVAssetBox = await withCheckedContinuation { continuation in
            PHImageManager.default().requestAVAsset(forVideo: asset, options: options) { avAsset, _, _ in
                continuation.resume(returning: AVAssetBox(avAsset))
            }
        }
        guard let avAsset = box.asset, let tracks = try? await avAsset.load(.tracks) else { return nil }
        var total: Int64 = 0
        for track in tracks {
            total += (try? await track.load(.totalSampleDataLength)) ?? 0
        }
        return total > 0 ? total : nil
    }

    private struct AVAssetBox: @unchecked Sendable {
        let asset: AVAsset?
        init(_ asset: AVAsset?) { self.asset = asset }
    }

    private func readScreenshot(_ item: MediaItem) async -> (MediaItem, ScreenshotInfo)? {
        let isPhoto = item.kind == .photo
        let side = isPhoto ? PaperReceiptDetector.readSide : ScreenshotAnalyzer.readSide
        guard let asset = assets[item.id], let (image, orientation) = await cgImage(for: asset, side: side) else { return nil }
        let reader = screenshotAnalyzer
        let info = await Task.detached(priority: .utility) {
            reader.analyze(image, orientation: orientation, isPhoto: isPhoto)
        }.value
        return (item, info)
    }

    private func analyzeOne(_ item: MediaItem) async -> AnalyzedPhoto? {
        guard let asset = assets[item.id], let (image, orientation) = await cgImage(for: asset, side: 512) else { return nil }
        // Vision work is synchronous and CPU/ANE bound; run it off the actor so analyses overlap.
        let analyzer = self.analyzer
        let features = await Task.detached(priority: .utility) { analyzer.analyze(image, orientation: orientation) }.value
        return AnalyzedPhoto(item: item, features: features)
    }

    /// Local-only thumbnail plus how to turn it upright (camera photos are often stored sideways);
    /// iCloud-only originals are skipped rather than downloaded.
    private func cgImage(for asset: PHAsset, side: CGFloat) async -> (CGImage, CGImagePropertyOrientation)? {
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = false
        options.isSynchronous = false
        return await withCheckedContinuation { continuation in
            imageManager.requestImage(
                for: asset,
                targetSize: CGSize(width: side, height: side),
                contentMode: .aspectFit,
                options: options
            ) { image, _ in
                continuation.resume(returning: image.flatMap { image in
                    image.cgImage.map { ($0, CGImagePropertyOrientation(image.imageOrientation)) }
                })
            }
        }
    }

    private static func item(for asset: PHAsset, screenSizes: Set<PixelSize>) -> MediaItem {
        let kind: MediaItem.Kind
        if asset.mediaType == .video {
            let size = PixelSize(width: asset.pixelWidth, height: asset.pixelHeight)
            kind = screenSizes.contains(size) && asset.location == nil ? .screenRecording : .video
        } else {
            kind = asset.mediaSubtypes.contains(.photoScreenshot) ? .screenshot : .photo
        }
        let fileName = primaryResource(in: PHAssetResource.assetResources(for: asset))?.originalFilename
        return MediaItem(
            id: asset.localIdentifier,
            kind: kind,
            creationDate: asset.creationDate ?? .distantPast,
            modifiedAt: asset.modificationDate ?? asset.creationDate ?? .distantPast,
            bytes: SizeEstimator.estimate(
                isVideo: asset.mediaType == .video,
                pixelWidth: asset.pixelWidth,
                pixelHeight: asset.pixelHeight,
                duration: asset.duration,
                fileName: fileName
            ),
            pixelWidth: asset.pixelWidth,
            pixelHeight: asset.pixelHeight,
            duration: asset.duration,
            isFavorite: asset.isFavorite,
            fileName: fileName,
            latitude: asset.location?.coordinate.latitude,
            longitude: asset.location?.coordinate.longitude,
            isSizeEstimated: true
        )
    }

    /// The original photo or video file.
    private static func primaryResource(in resources: [PHAssetResource]) -> PHAssetResource? {
        resources.first { $0.type == .photo || $0.type == .video || $0.type == .fullSizePhoto || $0.type == .fullSizeVideo }
            ?? resources.first
    }

}

extension CGImagePropertyOrientation {
    init(_ orientation: UIImage.Orientation) {
        switch orientation {
        case .up: self = .up
        case .upMirrored: self = .upMirrored
        case .down: self = .down
        case .downMirrored: self = .downMirrored
        case .left: self = .left
        case .leftMirrored: self = .leftMirrored
        case .right: self = .right
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}
