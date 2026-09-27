import Foundation

/// A set of cleanup candidates the user reviews before anything is deleted.
enum ReviewKind: Hashable, Sendable {
    case similar, blurry, oldScreenshots, oldRecordings, largeVideos
    /// Tickets and passes whose date has passed.
    case expired
    case screenshots(ScreenshotKind)

    var title: String {
        switch self {
        case .similar: "Similar Photos"
        case .blurry: "Blurry Photos"
        case .oldScreenshots: "Old Screenshots"
        case .oldRecordings: "Old Screen Recordings"
        case .largeVideos: "Large Videos"
        case .expired: "Expired Tickets"
        case .screenshots(let kind): kind.title
        }
    }

    var explanation: String {
        switch self {
        case .similar: "Extra shots from bursts. The best photo of each group is kept."
        case .blurry: "Photos that came out blurry or shaky."
        case .oldScreenshots: "Screenshots older than 30 days. Receipts and upcoming tickets are left out."
        case .oldRecordings: "Screen recordings older than 30 days."
        case .largeVideos: "Your biggest videos. Nothing is selected until you choose."
        case .expired: "Boarding passes and tickets for dates that have passed."
        case .screenshots: "Sorted by what's in them, read on this device. Nothing is selected until you choose."
        }
    }

    /// Stable name for debug launch arguments ("review:screenshots.receipts").
    init?(debugName: String) {
        let map: [String: ReviewKind] = ["similar": .similar, "blurry": .blurry, "oldScreenshots": .oldScreenshots,
                                         "oldRecordings": .oldRecordings, "largeVideos": .largeVideos, "expired": .expired]
        if let kind = map[debugName] {
            self = kind
        } else if debugName.hasPrefix("screenshots."), let kind = ScreenshotKind(rawValue: String(debugName.dropFirst(12))) {
            self = .screenshots(kind)
        } else {
            return nil
        }
    }
}

struct ReviewItem: Identifiable, Hashable, Sendable {
    let id: String
    let bytes: Int64
    let isVideo: Bool
    let duration: TimeInterval
    let createdAt: Date
    /// Selected when the review opens. Keepers and personal videos never are.
    let preselected: Bool
    /// The group's keeper: shown for context, never deletable from here.
    var isKeeper = false
}

extension Array where Element == ReviewItem {
    var defaultSelection: Set<String> { Set(filter { $0.preselected && !$0.isKeeper }.map(\.id)) }

    func bytes(of selection: Set<String>) -> Int64 {
        reduce(0) { selection.contains($1.id) ? $0 + $1.bytes : $0 }
    }
}

/// What happened when the user asked to delete.
enum DeletionOutcome: Equatable, Sendable {
    case deleted(count: Int, bytes: Int64)
    case cancelled
    case failed(String)
}

// MARK: - Compression

enum CompressionPreset: String, CaseIterable, Identifiable, Sendable {
    case hd1080, hd720

    var id: Self { self }
    var title: String { self == .hd1080 ? "1080p · High quality" : "720p · Smaller file" }
    /// Target total bitrate (video + audio) the export presets land near, used for estimates.
    /// Measured: Apple's HEVC 1080p preset lands around 8 Mbps on typical footage.
    var bitsPerSecond: Double { self == .hd1080 ? 8_000_000 : 3_500_000 }
    var shortSide: Int { self == .hd1080 ? 1080 : 720 }
}

/// Estimates compression savings so the UI only offers compression when it is worth it.
enum CompressionEstimator {
    /// Replace the original only when the new file is at least this much smaller.
    static let minimumSavingsRatio = 0.2
    static let minimumSavingsBytes: Int64 = 5_000_000

    static func estimatedBytes(duration: TimeInterval, preset: CompressionPreset) -> Int64 {
        Int64(preset.bitsPerSecond / 8 * duration)
    }

    /// Estimated savings, or nil when compressing wouldn't help (already small or low resolution).
    static func estimatedSavings(bytes: Int64, duration: TimeInterval, shortSide: Int, preset: CompressionPreset) -> Int64? {
        guard duration > 0, shortSide > preset.shortSide || Double(bytes) * 8 / duration > preset.bitsPerSecond * 1.5 else { return nil }
        let savings = bytes - estimatedBytes(duration: duration, preset: preset)
        return isWorthIt(original: bytes, savings: savings) ? savings : nil
    }

    /// After the fact: keep the compressed file only if it really saved enough.
    static func shouldReplace(originalBytes: Int64, compressedBytes: Int64) -> Bool {
        isWorthIt(original: originalBytes, savings: originalBytes - compressedBytes)
    }

    private static func isWorthIt(original: Int64, savings: Int64) -> Bool {
        savings >= minimumSavingsBytes && Double(savings) >= Double(original) * minimumSavingsRatio
    }
}
