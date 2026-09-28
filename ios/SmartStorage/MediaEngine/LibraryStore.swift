import OSLog
import Photos
import SwiftUI

/// Single source of truth for library data shown in the UI. Runs scans through `LibraryEngine`
/// and publishes `LibraryContent`; falls back to demo content only when explicitly asked to.
@MainActor
@Observable
final class LibraryStore {
    enum Phase: Equatable {
        case idle
        case loadingLibrary
        case analyzing(done: Int, total: Int)
        case ready
    }

    private(set) var access: LibraryAccess
    private(set) var phase: Phase = .idle
    private(set) var content: LibraryContent
    /// Cleanup target chosen on the Clean tab; nil = Maximum Safe Cleanup.
    var cleanupTarget: Int64? = 10_000_000_000
    /// Short confirmation shown after a delete/compress; the UI clears it when dismissed.
    var notice: String?
    let isDemo: Bool

    // Last scan inputs, so deletions update the screens without a full rescan.
    private var lastItems: [MediaItem] = []
    private var lastAnalyzed: [AnalyzedPhoto] = []
    private var lastScreenshotInfo: [String: ScreenshotInfo] = [:]
    /// Content hashes of files that share a size with another file (exact-duplicate check).
    private var lastHashes: [String: String] = [:]

    private static let log = Logger(subsystem: "com.keepspace.app", category: "scan")
    private let engine = LibraryEngine()
    private let builder = LibraryReportBuilder()
    /// nil only if the on-disk cache can't be opened; scans then work uncached.
    private let cache = try? AnalysisStore.make()
    private var scanTask: Task<Void, Never>?

    init(demo: Bool = false) {
        isDemo = demo
        access = demo ? .authorized : LibraryEngine.currentAccess()
        content = demo ? .demo : .empty(storage: Self.deviceStorage())
    }

    var cleanupPlan: CleanupPlan { content.plan(for: cleanupTarget) }

    var isScanning: Bool {
        switch phase {
        case .loadingLibrary, .analyzing: true
        case .idle, .ready: false
        }
    }

    /// Ask for access (first run) and scan when allowed.
    func requestAccessAndScan() async {
        guard !isDemo else { return }
        if access == .notDetermined {
            access = await LibraryEngine.requestAccess()
        }
        if access.canRead { scan() }
    }

    /// Re-read permission (e.g. after returning from Settings) and scan if it changed to allowed.
    func refreshAccess() {
        guard !isDemo else { return }
        let current = LibraryEngine.currentAccess()
        access = current
        // Incremental thanks to the cache, so returning to the app always picks up library changes.
        if current.canRead { scan() }
    }

    // MARK: - Actions

    /// Deletes after the system confirmation. Items go to Recently Deleted, so the space is only
    /// reclaimed when that album is emptied (or after 30 days) — the notice says so.
    @discardableResult
    func delete(_ ids: Set<String>) async -> DeletionOutcome {
        guard !isDemo else { return .cancelled }
        let outcome = await LibraryActions.delete(ids: Array(ids))
        switch outcome {
        case .deleted(let count, let bytes):
            removeFromResults(ids)
            notice = "\(count) \(count == 1 ? "item" : "items") (\(bytes.formattedBytes)) moved to Recently Deleted. Empty it in Photos to free the space now."
        case .failed(let message):
            notice = "Couldn't delete: \(message)"
        case .cancelled:
            break
        }
        return outcome
    }

    func compress(videoID: String, preset: CompressionPreset, progress: @escaping @Sendable (Double) -> Void) async -> String? {
        guard !isDemo else { return nil }
        do {
            switch try await LibraryActions.compress(id: videoID, preset: preset, progress: progress) {
            case .replaced(let original, let new):
                notice = "Compressed \(original.formattedBytes) → \(new.formattedBytes). The original is in Recently Deleted."
                scan() // pick up the new asset; everything else comes from the cache
            case .cancelled:
                return nil
            }
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    private func removeFromResults(_ ids: Set<String>) {
        lastItems.removeAll { ids.contains($0.id) }
        lastAnalyzed.removeAll { ids.contains($0.id) }
        ids.forEach { lastScreenshotInfo[$0] = nil; lastHashes[$0] = nil }
        rebuild()
        Task { [cache] in
            await cache?.delete(ids: Array(ids))
            await cache?.deleteScreenshots(ids: Array(ids))
            await cache?.deleteHashes(ids: Array(ids))
        }
    }

    /// Clears the on-device analysis cache and starts over; the next scan re-reads the library.
    func clearAnalysisCache() async {
        guard !isDemo else { return }
        scanTask?.cancel()
        await scanTask?.value
        await cache?.deleteAll()
        lastAnalyzed = []; lastScreenshotInfo = [:]; lastHashes = [:]
        phase = .idle
        notice = "Analysis cache cleared. KeepSpace will look at your library again."
        scan()
    }

    func scan() {
        guard !isDemo, access.canRead, !isScanning else { return }
        scanTask = Task { await runScan() }
    }

    private func runScan() async {
        phase = .loadingLibrary
        let storage = Self.deviceStorage()
        let items = await engine.loadItems(screenSizes: Self.screenPixelSizes())

        // Reuse cached analysis; only new or edited items go through the AI again.
        let photos = items.filter { $0.kind == .photo }
        let screenshots = items.filter { $0.kind == .screenshot }
        let photoPlan = CachePlanner.plan(photos: photos, cached: await cache?.loadAll() ?? [:])
        let cachedReads = await cache?.loadScreenshots() ?? [:]
        // Text is read from screenshots and from photos that look like paper receipts.
        func toRead(_ analyzed: [AnalyzedPhoto]) -> [MediaItem] {
            screenshots + analyzed.filter(PaperReceiptDetector.isCandidate).map(\.item)
        }
        let earlyReads = CachePlanner.plan(screenshots: toRead(photoPlan.hits), cached: cachedReads)
        let hashPlan = CachePlanner.plan(hashing: DuplicateFinder.candidates(items), cached: await cache?.loadHashes() ?? [:])
        await cache?.delete(ids: photoPlan.staleIDs)
        await cache?.deleteHashes(ids: hashPlan.staleIDs)
        // Counts only — never filenames or other personal data.
        Self.log.info("scan: \(items.count) items, \(photoPlan.hits.count) cached, \(photoPlan.toAnalyze.count) to analyze, \(photoPlan.staleIDs.count) stale; hashes \(hashPlan.hits.count) cached, \(hashPlan.toHash.count) to hash; text reads \(earlyReads.hits.count) cached, \(earlyReads.toAnalyze.count) to read")

        // Publish straight away: sizes plus everything the cache already knows.
        lastItems = items
        lastAnalyzed = photoPlan.hits
        lastScreenshotInfo = earlyReads.hits
        lastHashes = hashPlan.hits
        rebuild(storage: storage)

        let cache = self.cache
        let hashed = hashPlan.toHash.count
        var total = hashed + photoPlan.toAnalyze.count + earlyReads.toAnalyze.count
        // Exact duplicates first: quick (only same-size files are read) and the safest cleanup.
        if hashed > 0 {
            phase = .analyzing(done: 0, total: total)
            let fresh = await engine.hashFiles(hashPlan.toHash) { done, _ in
                await MainActor.run { self.phase = .analyzing(done: done, total: total) }
            }
            await cache?.saveHashes(fresh)
            fresh.forEach { lastHashes[$0.assetID] = $0.hash }
            rebuild(storage: storage)
        }
        if !photoPlan.toAnalyze.isEmpty {
            phase = .analyzing(done: hashed, total: total)
            let fresh = await engine.analyze(
                photoPlan.toAnalyze,
                progress: { done, _ in await MainActor.run { self.phase = .analyzing(done: hashed + done, total: total) } },
                onBatch: { batch in
                    // Persist as we go so an interrupted scan keeps its progress.
                    await cache?.save(batch.map {
                        CachedAnalysis(assetID: $0.id, modifiedAt: $0.item.modifiedAt, version: analyzerVersion, features: $0.features)
                    })
                }
            )
            lastAnalyzed = photoPlan.hits + fresh
            rebuild(storage: storage)
        }

        // Newly analyzed photos may have added receipt candidates.
        let reads = CachePlanner.plan(screenshots: toRead(lastAnalyzed), cached: cachedReads)
        await cache?.deleteScreenshots(ids: reads.staleIDs)
        reads.staleIDs.forEach { lastScreenshotInfo[$0] = nil }
        total = hashed + photoPlan.toAnalyze.count + reads.toAnalyze.count
        let candidates = lastAnalyzed.filter(PaperReceiptDetector.isCandidate).count
        Self.log.info("scan: \(candidates) receipt-like photos; text reads \(reads.hits.count) cached, \(reads.toAnalyze.count) to read")
        guard !reads.toAnalyze.isEmpty else {
            if !reads.staleIDs.isEmpty { rebuild(storage: storage) }
            phase = .ready
            return
        }
        let offset = hashed + photoPlan.toAnalyze.count
        phase = .analyzing(done: offset, total: total)
        let read = await engine.analyzeScreenshots(
            reads.toAnalyze,
            progress: { done, _ in await MainActor.run { self.phase = .analyzing(done: offset + done, total: total) } },
            onBatch: { batch in
                await cache?.saveScreenshots(batch.map {
                    CachedScreenshot(assetID: $0.0.id, modifiedAt: $0.0.modifiedAt, version: screenshotReaderVersion, info: $0.1)
                })
            }
        )
        lastScreenshotInfo.merge(read) { _, new in new }
        rebuild(storage: storage)
        phase = .ready
    }

    private func rebuild(storage: StorageSummary = LibraryStore.deviceStorage()) {
        content = builder.build(items: lastItems, analyzed: lastAnalyzed, screenshotInfo: lastScreenshotInfo, fileHashes: lastHashes,
                                deviceTotalBytes: storage.totalBytes, deviceFreeBytes: storage.freeBytes)
    }


    // MARK: - Device facts

    static func deviceStorage() -> StorageSummary {
        let url = URL(fileURLWithPath: NSHomeDirectory())
        let values = try? url.resourceValues(forKeys: [.volumeTotalCapacityKey, .volumeAvailableCapacityForImportantUsageKey])
        let total = Int64(values?.volumeTotalCapacity ?? 0)
        let free = values?.volumeAvailableCapacityForImportantUsage ?? 0
        return StorageSummary(usedBytes: total - free, totalBytes: total, potentialCleanupBytes: 0, categoryBytes: [:])
    }

    /// Native screen size in both orientations; recordings of this screen match one of them.
    private static func screenPixelSizes() -> Set<LibraryEngine.PixelSize> {
        let screen = UIScreen.main.nativeBounds.size
        let w = Int(screen.width), h = Int(screen.height)
        return [.init(width: w, height: h), .init(width: h, height: w)]
    }
}
