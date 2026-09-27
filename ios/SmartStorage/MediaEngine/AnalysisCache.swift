import Foundation
import SwiftData

/// Bump whenever `ImageAnalyzer` output changes meaning (new model, new metric), so stale
/// cached results are re-analyzed instead of silently mixed with new ones.
let analyzerVersion = 1

/// One cached analysis, as a value type that can cross actors.
struct CachedAnalysis: Sendable, Equatable {
    let assetID: String
    /// Asset modification time when analyzed; an edit invalidates the entry.
    let modifiedAt: Date
    let version: Int
    let features: ImageFeatures
}

/// Decides what a scan can reuse from the cache and what must be (re)analyzed. Pure, for tests.
enum CachePlanner {
    struct Plan: Sendable {
        let hits: [AnalyzedPhoto]
        let toAnalyze: [MediaItem]
        /// Cached assets that no longer exist in the library.
        let staleIDs: [String]
    }

    static func plan(photos: [MediaItem], cached: [String: CachedAnalysis], version: Int = analyzerVersion) -> Plan {
        var hits: [AnalyzedPhoto] = []
        var toAnalyze: [MediaItem] = []
        for item in photos {
            if let entry = cached[item.id], entry.version == version, entry.modifiedAt == item.modifiedAt {
                hits.append(AnalyzedPhoto(item: item, features: entry.features))
            } else {
                toAnalyze.append(item)
            }
        }
        let live = Set(photos.map(\.id))
        return Plan(hits: hits, toAnalyze: toAnalyze, staleIDs: cached.keys.filter { !live.contains($0) }.sorted())
    }
}

// MARK: - Persistence

@Model
final class AnalysisRecord {
    @Attribute(.unique) var assetID: String
    var modifiedAt: Date
    var version: Int
    /// Feature-print floats, packed little-endian.
    var featurePrint: Data
    var sharpness: Double
    var exposure: Double
    var faceQuality: Double?
    var faceCount: Int
    var sceneLabel: String?

    init(_ entry: CachedAnalysis) {
        assetID = entry.assetID
        modifiedAt = entry.modifiedAt
        version = entry.version
        featurePrint = entry.features.featurePrint.withUnsafeBufferPointer { Data(buffer: $0) }
        sharpness = entry.features.sharpness
        exposure = entry.features.exposure
        faceQuality = entry.features.faceQuality
        faceCount = entry.features.faceCount
        sceneLabel = entry.features.sceneLabel
    }

    func update(from entry: CachedAnalysis) {
        modifiedAt = entry.modifiedAt
        version = entry.version
        featurePrint = entry.features.featurePrint.withUnsafeBufferPointer { Data(buffer: $0) }
        sharpness = entry.features.sharpness
        exposure = entry.features.exposure
        faceQuality = entry.features.faceQuality
        faceCount = entry.features.faceCount
        sceneLabel = entry.features.sceneLabel
    }

    var value: CachedAnalysis {
        let floats = featurePrint.withUnsafeBytes { Array($0.bindMemory(to: Float.self)) }
        return CachedAnalysis(
            assetID: assetID,
            modifiedAt: modifiedAt,
            version: version,
            features: ImageFeatures(
                featurePrint: floats,
                sharpness: sharpness,
                exposure: exposure,
                faceQuality: faceQuality,
                faceCount: faceCount,
                sceneLabel: sceneLabel
            )
        )
    }
}

/// On-device cache of analysis results. Lives in Caches: it is derived, regenerable data, so it is
/// excluded from backups (image fingerprints never leave the device) and the OS may purge it.
@ModelActor
actor AnalysisStore {
    static func make(inMemory: Bool = false) throws -> AnalysisStore {
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        } else {
            let url = URL.cachesDirectory.appending(path: "analysis.store")
            configuration = ModelConfiguration(url: url)
        }
        let container = try ModelContainer(for: AnalysisRecord.self, configurations: configuration)
        return AnalysisStore(modelContainer: container)
    }

    func loadAll() -> [String: CachedAnalysis] {
        let records = (try? modelContext.fetch(FetchDescriptor<AnalysisRecord>())) ?? []
        return Dictionary(records.map { ($0.assetID, $0.value) }, uniquingKeysWith: { _, latest in latest })
    }

    func save(_ entries: [CachedAnalysis]) {
        guard !entries.isEmpty else { return }
        let ids = entries.map(\.assetID)
        let existing = (try? modelContext.fetch(FetchDescriptor<AnalysisRecord>(predicate: #Predicate { ids.contains($0.assetID) }))) ?? []
        let byID = Dictionary(existing.map { ($0.assetID, $0) }, uniquingKeysWith: { first, _ in first })
        for entry in entries {
            if let record = byID[entry.assetID] {
                record.update(from: entry)
            } else {
                modelContext.insert(AnalysisRecord(entry))
            }
        }
        try? modelContext.save()
    }

    func delete(ids: [String]) {
        guard !ids.isEmpty else { return }
        try? modelContext.delete(model: AnalysisRecord.self, where: #Predicate { ids.contains($0.assetID) })
        try? modelContext.save()
    }

    func count() -> Int {
        (try? modelContext.fetchCount(FetchDescriptor<AnalysisRecord>())) ?? 0
    }
}
