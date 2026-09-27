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
    let isDemo: Bool

    private let engine = LibraryEngine()
    private let builder = LibraryReportBuilder()
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
        let changed = current != access
        access = current
        if current.canRead, changed || phase == .idle { scan() }
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

        // Publish sizes straight away so Home is useful while photos are still being analyzed.
        content = builder.build(items: items, analyzed: [], deviceTotalBytes: storage.totalBytes, deviceFreeBytes: storage.freeBytes)
        phase = .analyzing(done: 0, total: items.filter { $0.kind == .photo }.count)

        let analyzed = await engine.analyze(items) { done, total in
            await MainActor.run { self.phase = .analyzing(done: done, total: total) }
        }
        content = builder.build(items: items, analyzed: analyzed, deviceTotalBytes: storage.totalBytes, deviceFreeBytes: storage.freeBytes)
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
