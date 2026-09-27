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
        let storage = Self.deviceStorage()
        content = builder.build(items: lastItems, analyzed: lastAnalyzed, deviceTotalBytes: storage.totalBytes, deviceFreeBytes: storage.freeBytes)
        Task { [cache] in await cache?.delete(ids: Array(ids)) }
    }

    func scan() {
        guard !isDemo, access.canRead, !isScanning else { return }
        scanTask = Task { await runScan() }
    }

    private func runScan() async {
        phase = .loadingLibrary
        let storage = Self.deviceStorage()
        let screenSizes = Self.screenPixelSizes()
        let items = await engine.loadItems(screenSizes: screenSizes)

        // Reuse cached analysis; only new or edited photos go through the AI again.
        let photos = items.filter { $0.kind == .photo }
        let plan = CachePlanner.plan(photos: photos, cached: await cache?.loadAll() ?? [:])
        await cache?.delete(ids: plan.staleIDs)
        // Counts only — never filenames or other personal data.
        Self.log.info("scan: \(items.count) items, \(plan.hits.count) cached, \(plan.toAnalyze.count) to analyze, \(plan.staleIDs.count) stale")

        // Publish straight away: sizes plus everything the cache already knows.
        lastItems = items
        lastAnalyzed = plan.hits
        content = builder.build(items: items, analyzed: plan.hits, deviceTotalBytes: storage.totalBytes, deviceFreeBytes: storage.freeBytes)
        guard !plan.toAnalyze.isEmpty else {
            phase = .ready
            return
        }
        phase = .analyzing(done: 0, total: plan.toAnalyze.count)

        let cache = self.cache
        let fresh = await engine.analyze(
            plan.toAnalyze,
            progress: { done, total in
                await MainActor.run { self.phase = .analyzing(done: done, total: total) }
            },
            onBatch: { batch in
                // Persist as we go so an interrupted scan keeps its progress.
                await cache?.save(batch.map {
                    CachedAnalysis(assetID: $0.id, modifiedAt: $0.item.modifiedAt, version: analyzerVersion, features: $0.features)
                })
            }
        )
        lastAnalyzed = plan.hits + fresh
        content = builder.build(items: items, analyzed: lastAnalyzed, deviceTotalBytes: storage.totalBytes, deviceFreeBytes: storage.freeBytes)
        phase = .ready
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
