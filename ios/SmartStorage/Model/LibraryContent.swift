import Foundation

/// Everything the screens display, from one source: either a real library scan or the demo data.
struct LibraryContent: Sendable {
    var storage: StorageSummary
    var similarBytes: Int64
    var photoGroups: [PhotoGroup]
    var screenshotsBytes: Int64
    var screenshotCategories: [ScreenshotCategory]
    var expiredScreenshots: [ExpiredScreenshot]
    var largeVideoBytes: Int64
    var recordingBytes: Int64
    var videos: [VideoItem]
    var forecast: StorageForecast
    var memories: [MemoryEvent]
    var memoriesCleanup: (similarPhotos: Int, blurryShots: Int)
    /// Candidate cleanup sources, safest first; plans are assembled from these per target.
    var cleanupCandidates: [PlanItem]
    /// Items behind each candidate, for the review screen. Empty for demo content.
    var reviewSets: [ReviewKind: [ReviewItem]] = [:]
    /// Receipts with their extracted details, newest first.
    var receipts: [ReceiptEntry] = []

    var similarGroupCount: Int { photoGroups.count }

    /// Greedily adds whole candidate categories (safest first) until the target is met.
    /// `nil` target = Maximum Safe Cleanup (everything eligible).
    func plan(for targetBytes: Int64?) -> CleanupPlan {
        var chosen: [PlanItem] = []
        var total: Int64 = 0
        for item in cleanupCandidates where item.bytes > 0 {
            if let targetBytes, total >= targetBytes { break }
            chosen.append(item)
            total += item.bytes
        }
        // ~1.5 s per category item reviewed, rounded to the nearest 10 s.
        let itemCount = chosen.reduce(0) { $0 + $1.itemCount }
        let seconds = max(10, Int((Double(itemCount) * 1.5 / 10).rounded()) * 10)
        let duration = Duration.seconds(seconds).formatted(.units(allowed: [.minutes, .seconds], width: .abbreviated))
        return CleanupPlan(targetBytes: targetBytes ?? total, estimatedBytes: total, reviewTime: duration, items: chosen)
    }
}

extension LibraryContent {
    /// Stable placeholder assets for the Photos-style Library in demo/screenshot and UI-test runs.
    /// Their IDs intentionally do not resolve in Photos, so AssetImage renders its safe fallback art.
    static let demoMediaItems: [MediaItem] = {
        let now = Calendar.utcGregorian.date(from: DateComponents(year: 2026, month: 9, day: 28, hour: 12))!
        return [
            MediaItem(id: "demo-library-sunset", kind: .photo, creationDate: now,
                      bytes: 3_800_000, pixelWidth: 4032, pixelHeight: 3024, duration: 0,
                      isFavorite: true, fileName: "IMG_1203.HEIC"),
            MediaItem(id: "demo-library-portrait", kind: .photo, creationDate: now.addingTimeInterval(-3_600),
                      bytes: 3_200_000, pixelWidth: 3024, pixelHeight: 4032, duration: 0,
                      isFavorite: false, fileName: "IMG_1202.HEIC"),
            MediaItem(id: "demo-library-family", kind: .photo, creationDate: now.addingTimeInterval(-7_200),
                      bytes: 4_100_000, pixelWidth: 4032, pixelHeight: 3024, duration: 0,
                      isFavorite: false, fileName: "IMG_1201.HEIC"),
            MediaItem(id: "demo-library-video", kind: .video, creationDate: now.addingTimeInterval(-10_800),
                      bytes: 420_000_000, pixelWidth: 3840, pixelHeight: 2160, duration: 48,
                      isFavorite: false, fileName: "IMG_1200.MOV"),
        ]
    }()

    /// Demo data matching the mockups (previews, screenshots, and the simulator without photos).
    static let demo = LibraryContent(
        storage: MockData.storage,
        similarBytes: MockData.similarPhotosSummary.bytes,
        photoGroups: MockData.photoGroups,
        screenshotsBytes: MockData.screenshotsRecoverableBytes,
        screenshotCategories: MockData.screenshotCategories,
        expiredScreenshots: MockData.expiredScreenshots,
        largeVideoBytes: MockData.videoSummary.largeBytes,
        recordingBytes: MockData.videoSummary.recordingBytes,
        videos: MockData.videos,
        forecast: MockData.forecast,
        memories: MockData.memories,
        memoriesCleanup: MockData.memoriesCleanup,
        cleanupCandidates: MockData.cleanupPlan.items,
        receipts: MockData.receipts
    )

    /// Before a scan finishes: real device storage, nothing else yet.
    static func empty(storage: StorageSummary) -> LibraryContent {
        LibraryContent(
            storage: storage,
            similarBytes: 0,
            photoGroups: [],
            screenshotsBytes: 0,
            screenshotCategories: [],
            expiredScreenshots: [],
            largeVideoBytes: 0,
            recordingBytes: 0,
            videos: [],
            forecast: StorageForecast(
                capacityGB: Double(storage.totalBytes) / 1e9,
                remainingBytes: storage.freeBytes,
                daysUntilFull: nil,
                points: [],
                photosAddedThisWeek: 0,
                videosAddedThisWeek: 0,
                potentialCleanupBytes: 0
            ),
            memories: [],
            memoriesCleanup: (0, 0),
            cleanupCandidates: []
        )
    }
}
