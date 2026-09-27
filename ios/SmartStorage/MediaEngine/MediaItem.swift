import Foundation

/// What the library layer knows about one asset. Plain value type so it can cross actors
/// and be cached; `id` is the `PHAsset.localIdentifier`.
struct MediaItem: Identifiable, Hashable, Sendable {
    enum Kind: Sendable {
        case photo, screenshot, video, screenRecording
    }

    let id: String
    let kind: Kind
    let creationDate: Date
    let bytes: Int64
    let pixelWidth: Int
    let pixelHeight: Int
    let duration: TimeInterval
    let isFavorite: Bool

    var isVideo: Bool { kind == .video || kind == .screenRecording }
    var isStill: Bool { !isVideo }
}

/// Per-image results from the on-device analyzer.
struct ImageFeatures: Sendable, Equatable {
    /// Vision feature-print vector (unit-length floats). Empty when Vision failed.
    let featurePrint: [Float]
    /// Variance of the Laplacian on a 256px grayscale thumbnail; higher = sharper.
    let sharpness: Double
    /// Mean luminance 0...1.
    let exposure: Double
    /// Best face-capture quality 0...1, nil when no faces were found.
    let faceQuality: Double?
    let faceCount: Int
    /// Top scene label from Vision's on-device classifier ("beach", "food"…), nil when unsure.
    var sceneLabel: String? = nil

    /// Distance between two feature prints (Euclidean). `.infinity` when either is missing.
    func distance(to other: ImageFeatures) -> Float {
        guard !featurePrint.isEmpty, featurePrint.count == other.featurePrint.count else { return .infinity }
        var sum: Float = 0
        for i in featurePrint.indices {
            let d = featurePrint[i] - other.featurePrint[i]
            sum += d * d
        }
        return sum.squareRoot()
    }
}

struct AnalyzedPhoto: Sendable, Identifiable {
    let item: MediaItem
    let features: ImageFeatures

    var id: String { item.id }
}
