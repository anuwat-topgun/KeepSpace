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
    /// Called with the bytes of every deletion the person confirmed (a cancelled system dialog never calls it). The app uses it to count the free monthly allowance.
    var onDeleted: (@MainActor (Int64) -> Void)?
    /// Whether a confirmed Best Shot choice may teach AI Taste (a Pro feature). Free keeps what was learned but stops learning.
    var canLearnTaste: @MainActor () -> Bool = { true }

    // Last scan inputs, so deletions update the screens without a full rescan.
    private var lastItems: [MediaItem] = []
    private var lastAnalyzed: [AnalyzedPhoto] = []
    private var lastScreenshotInfo: [String: ScreenshotInfo] = [:]
    /// Content hashes of files that share a size with another file (exact-duplicate check).
    private var lastHashes: [String: String] = [:]

    private static let log = Logger(subsystem: "com.keepspace.app", category: "scan")
    private let engine = LibraryEngine()
    /// What Best Shot has learned about this person's taste (on this device only).
    let taste = TasteStore()
    /// Built per use so a new choice changes the very next recommendation.
    private var builder: LibraryReportBuilder {
        var builder = LibraryReportBuilder()
        builder.scorer = BestShotScorer(weights: taste.weights)
        return builder
    }
    /// nil only if the on-disk cache can't be opened; scans then work uncached.
    private let cache = try? AnalysisStore.make()
    private var scanTask: Task<Void, Never>?

    init(demo: Bool = false) {
        isDemo = demo
        access = demo ? .authorized : LibraryEngine.currentAccess()
        content = demo ? .demo : .empty(storage: Self.deviceStorage())
    }

    var cleanupPlan: CleanupPlan { content.plan(for: cleanupTarget) }

    /// Full device library for the Photos-style browser, newest first.
    var mediaItems: [MediaItem] {
        lastItems.sorted { $0.creationDate > $1.creationDate }
    }

    /// Current real media for an explicit v1.1 backup. Demo rows never become upload jobs.
    func backupCandidates(for scope: CloudBackupScope) -> [MediaItem] {
        guard !isDemo else { return [] }
        let receiptIDs = Set(content.receipts.map(\.id))
        return lastItems.filter { item in
            switch scope {
            case .photos: item.kind == .photo
            case .screenshots: item.kind == .screenshot
            case .receipts: receiptIDs.contains(item.id)
            }
        }
    }

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

    /// The size of each of `ids`, for checking a deletion against the free allowance.
    func cleanupItems(_ ids: some Collection<String>, safety: SafetyLevel) -> [CleanupItem] {
        let wanted = Set(ids)
        return lastItems.filter { wanted.contains($0.id) }.map { CleanupItem(id: $0.id, bytes: $0.bytes, safety: safety) }
    }

    /// Deletes after the system confirmation. Items go to Recently Deleted, so the space is only
    /// reclaimed when that album is emptied (or after 30 days) — the notice says so.
    @discardableResult
    func delete(_ ids: Set<String>) async -> DeletionOutcome {
        guard !isDemo else { return .cancelled }
        let bytes = lastItems.lazy.filter { ids.contains($0.id) }.reduce(Int64(0)) { $0 + $1.bytes }
        #if DEBUG
        print("[KeepSpace] delete requested: \(ids.count) items, \(bytes) B")
        #endif
        let outcome = await LibraryActions.delete(ids: Array(ids), bytes: bytes)
        #if DEBUG
        print("[KeepSpace] delete outcome: \(outcome)")
        #endif
        switch outcome {
        case .deleted(let count, let bytes):
            removeFromResults(ids)
            onDeleted?(bytes)
            notice = localizedFormat(count == 1 ? "%d item (%@) moved to Recently Deleted. Empty it in Photos to free the space now." : "%d items (%@) moved to Recently Deleted. Empty it in Photos to free the space now.", count, bytes.formattedBytes)
        case .failed(let message):
            notice = localizedFormat("Couldn't delete: %@", message)
        case .cancelled:
            break
        }
        return outcome
    }

    /// Keeps the photo at `keeperIndex` and deletes the rest of the group (after the system confirmation).
    /// A confirmed choice is what Best Shot learns from; a cancelled one teaches nothing.
    @discardableResult
    func keep(_ group: PhotoGroup, keeperIndex: Int, limitingTo allowed: Set<String>? = nil) async -> DeletionOutcome {
        var others = Set(group.assetIDs.enumerated().filter { $0.offset != keeperIndex }.map(\.element))
        if let allowed { others.formIntersection(allowed) } // free allowance: delete only what fits
        let outcome = await delete(others)
        if case .deleted = outcome, !group.scoreFeatures.isEmpty, canLearnTaste() {
            taste.learn(chosen: keeperIndex, among: group.scoreFeatures)
            rebuild()
        }
        return outcome
    }

    func compress(videoID: String, preset: CompressionPreset, progress: @escaping @Sendable (Double) -> Void) async -> String? {
        guard !isDemo else { return nil }
        guard let originalBytes = lastItems.first(where: { $0.id == videoID })?.bytes else {
            return "This video is no longer in your library."
        }
        do {
            switch try await LibraryActions.compress(id: videoID, originalBytes: originalBytes, preset: preset, progress: progress) {
            case .replaced(let original, let new):
                notice = localizedFormat("Compressed %@ → %@. The original is in Recently Deleted.", original.formattedBytes, new.formattedBytes)
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
        notice = "Analysis cache cleared. KeepSpace will look at your library again.".localizedUI
        scan()
    }

    func scan() {
        guard !isDemo, access.canRead, !isScanning else { return }
        scanTask = Task { await runScan() }
    }

    private func runScan() async {
        phase = .loadingLibrary
        let storage = Self.deviceStorage()
        var items = await engine.loadItems(screenSizes: Self.screenPixelSizes())
        let cache = self.cache

        // Exact sizes share a cache record with hashes. Apply unchanged measurements immediately;
        // local items that are new or edited are then measured through public APIs only.
        let cachedMeasurements = await cache?.loadHashes() ?? [:]
        let measurePlan = CachePlanner.plan(measuring: items, cached: cachedMeasurements)
        items = Self.applying(Array(measurePlan.hits.values), to: items)
        await cache?.deleteHashes(ids: measurePlan.staleIDs)
        let cachedAnalyses = await cache?.loadAll() ?? [:]
        let cachedReads = await cache?.loadScreenshots() ?? [:]
        let previewPhotos = items.filter { $0.kind == .photo }
        let previewScreenshots = items.filter { $0.kind == .screenshot }
        let previewPhotoPlan = CachePlanner.plan(photos: previewPhotos, cached: cachedAnalyses)
        let previewReadItems = previewScreenshots + previewPhotoPlan.hits
            .filter(PaperReceiptDetector.isCandidate).map(\.item)
        let previewReads = CachePlanner.plan(screenshots: previewReadItems, cached: cachedReads)
        lastItems = items
        lastAnalyzed = previewPhotoPlan.hits
        lastScreenshotInfo = previewReads.hits
        lastHashes = Dictionary(measurePlan.hits.values.compactMap { entry in
            entry.hash.map { (entry.assetID, $0) }
        }, uniquingKeysWith: { _, latest in latest })
        rebuild(storage: storage)

        let measured = measurePlan.toMeasure.count
        var freshMeasurements: [CachedHash] = []
        if measured > 0 {
            phase = .analyzing(done: 0, total: measured)
            freshMeasurements = await engine.measure(
                measurePlan.toMeasure,
                progress: { done, _ in
                    await MainActor.run { self.phase = .analyzing(done: done, total: measured) }
                },
                onBatch: { batch in
                    await cache?.saveHashes(batch)
                    await MainActor.run {
                        self.lastItems = Self.applying(batch, to: self.lastItems)
                        self.rebuild(storage: storage)
                    }
                }
            )
            items = Self.applying(freshMeasurements, to: items)
            lastItems = items
        }

        var measurements = measurePlan.hits
        freshMeasurements.forEach { measurements[$0.assetID] = $0 }

        // Reuse cached analysis; only new or edited items go through the AI again. This happens
        // after measurement so every new analysis carries the exact size when one is available.
        let photos = items.filter { $0.kind == .photo }
        let screenshots = items.filter { $0.kind == .screenshot }
        let photoPlan = CachePlanner.plan(photos: photos, cached: cachedAnalyses)
        // Text is read from screenshots and from photos that look like paper receipts.
        func toRead(_ analyzed: [AnalyzedPhoto]) -> [MediaItem] {
            screenshots + analyzed.filter(PaperReceiptDetector.isCandidate).map(\.item)
        }
        let earlyReads = CachePlanner.plan(screenshots: toRead(photoPlan.hits), cached: cachedReads)
        let hashPlan = CachePlanner.plan(
            hashing: DuplicateFinder.candidates(items),
            cached: measurements,
            liveIDs: Set(items.map(\.id))
        )
        await cache?.delete(ids: photoPlan.staleIDs)
        // Counts only — never filenames or other personal data.
        Self.log.info("scan: \(items.count) items, sizes \(measurePlan.hits.count) cached, \(measured) measured, \(photoPlan.hits.count) analyses cached, \(photoPlan.toAnalyze.count) to analyze, \(photoPlan.staleIDs.count) stale; hashes \(hashPlan.hits.count) cached, \(hashPlan.toHash.count) to hash; text reads \(earlyReads.hits.count) cached, \(earlyReads.toAnalyze.count) to read")

        // Publish exact sizes plus everything the other caches already know.
        lastItems = items
        lastAnalyzed = photoPlan.hits
        lastScreenshotInfo = earlyReads.hits
        lastHashes = hashPlan.hits
        rebuild(storage: storage)

        let hashed = hashPlan.toHash.count
        var total = measured + hashed + photoPlan.toAnalyze.count + earlyReads.toAnalyze.count
        // Photos already got hashes while being measured. Only same-size videos (and any legacy
        // cache misses) need a separate stream here.
        if hashed > 0 {
            phase = .analyzing(done: measured, total: total)
            let fresh = await engine.hashFiles(hashPlan.toHash) { done, _ in
                await MainActor.run { self.phase = .analyzing(done: measured + done, total: total) }
            }
            await cache?.saveHashes(fresh)
            items = Self.applying(fresh, to: items)
            lastItems = items
            fresh.forEach { entry in
                if let hash = entry.hash { lastHashes[entry.assetID] = hash }
            }
            rebuild(storage: storage)
        }
        if !photoPlan.toAnalyze.isEmpty {
            let offset = measured + hashed
            phase = .analyzing(done: offset, total: total)
            let fresh = await engine.analyze(
                photoPlan.toAnalyze,
                progress: { done, _ in await MainActor.run { self.phase = .analyzing(done: offset + done, total: total) } },
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
        total = measured + hashed + photoPlan.toAnalyze.count + reads.toAnalyze.count
        let candidates = lastAnalyzed.filter(PaperReceiptDetector.isCandidate).count
        Self.log.info("scan: \(candidates) receipt-like photos; text reads \(reads.hits.count) cached, \(reads.toAnalyze.count) to read")
        guard !reads.toAnalyze.isEmpty else {
            if !reads.staleIDs.isEmpty { rebuild(storage: storage) }
            phase = .ready
            return
        }
        let offset = measured + hashed + photoPlan.toAnalyze.count
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

    /// Replaces estimates with cached or freshly measured values without disturbing item order.
    private static func applying(_ entries: [CachedHash], to items: [MediaItem]) -> [MediaItem] {
        guard !entries.isEmpty else { return items }
        let byID = Dictionary(entries.map { ($0.assetID, $0) }, uniquingKeysWith: { _, latest in latest })
        return items.map { item in
            guard let entry = byID[item.id], entry.modifiedAt == item.modifiedAt else { return item }
            var measured = item
            measured.bytes = entry.bytes
            measured.isSizeEstimated = false
            return measured
        }
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
