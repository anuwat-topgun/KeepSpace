import Foundation
import SwiftData

/// Bump whenever `ImageAnalyzer` output changes meaning (new model, new metric), so stale
/// cached results are re-analyzed instead of silently mixed with new ones.
/// 2: text lines + document score (paper receipts); images read upright.
let analyzerVersion = 2

/// Same idea for screenshot classification (OCR rules, keywords). Separate so tuning the
/// classifier re-reads screenshots without re-analyzing every photo.
/// 2: receipts recognised from amounts alone. 3: receipt details extracted. 4: stricter merchant names.
let screenshotReaderVersion = 4

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

/// Cached screenshot classification (also used for photos read as paper receipts). Only the
/// category and a few fields — never the text.
struct CachedScreenshot: Sendable, Equatable {
    let assetID: String
    let modifiedAt: Date
    let version: Int
    let info: ScreenshotInfo
}

extension CachePlanner {
    struct ScreenshotPlan: Sendable {
        let hits: [String: ScreenshotInfo]
        let toAnalyze: [MediaItem]
        let staleIDs: [String]
    }

    static func plan(screenshots: [MediaItem], cached: [String: CachedScreenshot], version: Int = screenshotReaderVersion) -> ScreenshotPlan {
        var hits: [String: ScreenshotInfo] = [:]
        var toAnalyze: [MediaItem] = []
        for item in screenshots {
            if let entry = cached[item.id], entry.version == version, entry.modifiedAt == item.modifiedAt {
                hits[item.id] = entry.info
            } else {
                toAnalyze.append(item)
            }
        }
        let live = Set(screenshots.map(\.id))
        return ScreenshotPlan(hits: hits, toAnalyze: toAnalyze, staleIDs: cached.keys.filter { !live.contains($0) }.sorted())
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
    // Defaults keep SwiftData's lightweight migration happy for stores written before v2.
    var textLines: Int = 0
    var documentScore: Double = 0

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
        textLines = entry.features.textLines
        documentScore = entry.features.documentScore
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
        textLines = entry.features.textLines
        documentScore = entry.features.documentScore
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
                sceneLabel: sceneLabel,
                textLines: textLines,
                documentScore: documentScore
            )
        )
    }
}

@Model
final class ScreenshotRecord {
    @Attribute(.unique) var assetID: String
    var modifiedAt: Date
    var version: Int
    var kind: String
    var eventDate: Date?
    var route: String?
    var merchant: String?
    var receiptDate: Date?
    /// Decimal as text so amounts round-trip exactly.
    var amount: String?
    var currency: String?
    var receiptCategory: String?

    init(_ entry: CachedScreenshot) {
        assetID = entry.assetID
        modifiedAt = entry.modifiedAt
        version = entry.version
        kind = entry.info.kind.rawValue
        eventDate = entry.info.eventDate
        route = entry.info.route
        merchant = entry.info.receipt?.merchant
        receiptDate = entry.info.receipt?.date
        amount = entry.info.receipt?.amount.map { "\($0)" }
        currency = entry.info.receipt?.currency
        receiptCategory = entry.info.receipt?.category.rawValue
    }

    func update(from entry: CachedScreenshot) {
        modifiedAt = entry.modifiedAt
        version = entry.version
        kind = entry.info.kind.rawValue
        eventDate = entry.info.eventDate
        route = entry.info.route
        merchant = entry.info.receipt?.merchant
        receiptDate = entry.info.receipt?.date
        amount = entry.info.receipt?.amount.map { "\($0)" }
        currency = entry.info.receipt?.currency
        receiptCategory = entry.info.receipt?.category.rawValue
    }

    var value: CachedScreenshot {
        CachedScreenshot(
            assetID: assetID,
            modifiedAt: modifiedAt,
            version: version,
            info: ScreenshotInfo(
                kind: ScreenshotKind(rawValue: kind) ?? .other,
                eventDate: eventDate,
                route: route,
                receipt: kind == ScreenshotKind.receipts.rawValue ? ReceiptDetails(
                    merchant: merchant,
                    date: receiptDate,
                    amount: amount.flatMap { Decimal(string: $0, locale: Locale(identifier: "en_US_POSIX")) },
                    currency: currency,
                    category: receiptCategory.flatMap(ReceiptCategory.init(rawValue:)) ?? .other
                ) : nil
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
        let container = try ModelContainer(for: AnalysisRecord.self, ScreenshotRecord.self, configurations: configuration)
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

    // MARK: Screenshots

    func loadScreenshots() -> [String: CachedScreenshot] {
        let records = (try? modelContext.fetch(FetchDescriptor<ScreenshotRecord>())) ?? []
        return Dictionary(records.map { ($0.assetID, $0.value) }, uniquingKeysWith: { _, latest in latest })
    }

    func saveScreenshots(_ entries: [CachedScreenshot]) {
        guard !entries.isEmpty else { return }
        let ids = entries.map(\.assetID)
        let existing = (try? modelContext.fetch(FetchDescriptor<ScreenshotRecord>(predicate: #Predicate { ids.contains($0.assetID) }))) ?? []
        let byID = Dictionary(existing.map { ($0.assetID, $0) }, uniquingKeysWith: { first, _ in first })
        for entry in entries {
            if let record = byID[entry.assetID] { record.update(from: entry) } else { modelContext.insert(ScreenshotRecord(entry)) }
        }
        try? modelContext.save()
    }

    func deleteScreenshots(ids: [String]) {
        guard !ids.isEmpty else { return }
        try? modelContext.delete(model: ScreenshotRecord.self, where: #Predicate { ids.contains($0.assetID) })
        try? modelContext.save()
    }

    func count() -> Int {
        (try? modelContext.fetchCount(FetchDescriptor<AnalysisRecord>())) ?? 0
    }
}
