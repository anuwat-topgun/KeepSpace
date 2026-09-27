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
        guard !resources.contains(where: { $0.type == .adjustmentData }), let resource = Self.primaryResource(in: resources) else { return nil }
        let options = PHAssetResourceRequestOptions()
        options.isNetworkAccessAllowed = false
        let digest = await withCheckedContinuation { (continuation: CheckedContinuation<String?, Never>) in
            let hasher = HashBox()
            PHAssetResourceManager.default().requestData(for: resource, options: options) { chunk in
                hasher.update(chunk)
            } completionHandler: { error in
                continuation.resume(returning: error == nil ? hasher.finalize() : nil)
            }
        }
        return digest.map { CachedHash(assetID: item.id, modifiedAt: item.modifiedAt, bytes: item.bytes, hash: $0) }
    }

    /// Streams chunks into SHA-256; PhotoKit delivers them serially on its own queue.
    private final class HashBox: @unchecked Sendable {
        private var hasher = SHA256()
        func update(_ data: Data) { hasher.update(data: data) }
        func finalize() -> String { hasher.finalize().map { String(format: "%02x", $0) }.joined() }
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
        return MediaItem(
            id: asset.localIdentifier,
            kind: kind,
            creationDate: asset.creationDate ?? .distantPast,
            modifiedAt: asset.modificationDate ?? asset.creationDate ?? .distantPast,
            bytes: fileSize(of: asset),
            pixelWidth: asset.pixelWidth,
            pixelHeight: asset.pixelHeight,
            duration: asset.duration,
            isFavorite: asset.isFavorite,
            fileName: PHAssetResource.assetResources(for: asset).first?.originalFilename,
            latitude: asset.location?.coordinate.latitude,
            longitude: asset.location?.coordinate.longitude
        )
    }

    /// The original photo or video file.
    private static func primaryResource(in resources: [PHAssetResource]) -> PHAssetResource? {
        resources.first { $0.type == .photo || $0.type == .video || $0.type == .fullSizePhoto || $0.type == .fullSizeVideo }
            ?? resources.first
    }

    /// PhotoKit doesn't expose asset size publicly; `fileSize` on the primary resource is the
    /// long-standing way to read it. Falls back to an estimate from pixel count.
    private static func fileSize(of asset: PHAsset) -> Int64 {
        let primary = primaryResource(in: PHAssetResource.assetResources(for: asset))
        if let size = primary?.value(forKey: "fileSize") as? Int64 { return size }
        if let size = primary?.value(forKey: "fileSize") as? Int { return Int64(size) }
        let pixels = Int64(asset.pixelWidth * asset.pixelHeight)
        return asset.mediaType == .video ? Int64(asset.duration * 1_000_000) : pixels / 4
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
