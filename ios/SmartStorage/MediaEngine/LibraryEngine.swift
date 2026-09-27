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

    private func readScreenshot(_ item: MediaItem) async -> (MediaItem, ScreenshotInfo)? {
        guard let asset = assets[item.id], let image = await cgImage(for: asset, side: ScreenshotAnalyzer.readSide) else { return nil }
        let reader = screenshotAnalyzer
        let info = await Task.detached(priority: .utility) { reader.analyze(image) }.value
        return (item, info)
    }

    private func analyzeOne(_ item: MediaItem) async -> AnalyzedPhoto? {
        guard let asset = assets[item.id], let image = await cgImage(for: asset, side: 512) else { return nil }
        // Vision work is synchronous and CPU/ANE bound; run it off the actor so analyses overlap.
        let analyzer = self.analyzer
        let features = await Task.detached(priority: .utility) { analyzer.analyze(image) }.value
        return AnalyzedPhoto(item: item, features: features)
    }

    /// Local-only thumbnail; iCloud-only originals are skipped rather than downloaded.
    private func cgImage(for asset: PHAsset, side: CGFloat) async -> CGImage? {
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
                continuation.resume(returning: image?.cgImage)
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
            fileName: PHAssetResource.assetResources(for: asset).first?.originalFilename
        )
    }

    /// PhotoKit doesn't expose asset size publicly; `fileSize` on the primary resource is the
    /// long-standing way to read it. Falls back to an estimate from pixel count.
    private static func fileSize(of asset: PHAsset) -> Int64 {
        let resources = PHAssetResource.assetResources(for: asset)
        let primary = resources.first { $0.type == .photo || $0.type == .video || $0.type == .fullSizePhoto || $0.type == .fullSizeVideo }
            ?? resources.first
        if let size = primary?.value(forKey: "fileSize") as? Int64 { return size }
        if let size = primary?.value(forKey: "fileSize") as? Int { return Int64(size) }
        let pixels = Int64(asset.pixelWidth * asset.pixelHeight)
        return asset.mediaType == .video ? Int64(asset.duration * 1_000_000) : pixels / 4
    }
}
