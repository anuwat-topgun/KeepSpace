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
