import Foundation

/// Cleanup categories shown on Home and in the cleanup plan.
enum CleanupCategory: String, CaseIterable, Identifiable, Sendable {
    case similarPhotos, screenshots, largeVideos, screenRecordings, blurryPhotos

    var id: Self { self }

    var title: String {
        switch self {
        case .similarPhotos: "Similar Photos"
        case .screenshots: "Screenshots"
        case .largeVideos: "Large Videos"
        case .screenRecordings: "Screen Recordings"
        case .blurryPhotos: "Blurry Photos"
        }
    }

    var systemImage: String {
        switch self {
        case .similarPhotos: "photo.on.rectangle.angled"
        case .screenshots: "viewfinder"
        case .largeVideos: "video.fill"
        case .screenRecordings: "record.circle"
        case .blurryPhotos: "camera.filters"
        }
    }

    var tint: Tint {
        switch self {
        case .similarPhotos, .screenRecordings: .coral
        case .screenshots: .blue
        case .largeVideos: .purple
        case .blurryPhotos: .mint
        }
    }

    var route: Route {
        switch self {
        case .similarPhotos: .similarPhotos
        case .screenshots: .screenshots
        case .largeVideos, .screenRecordings: .videos
        case .blurryPhotos: .similarPhotos
        }
    }
}

struct StorageSummary: Sendable {
    var usedBytes: Int64
    var totalBytes: Int64
    var potentialCleanupBytes: Int64
    var categoryBytes: [CleanupCategory: Int64]

    var freeBytes: Int64 { totalBytes - usedBytes }
    var usedFraction: Double { totalBytes == 0 ? 0 : Double(usedBytes) / Double(totalBytes) }
}

/// Placeholder data matching the mockups until the media engine exists.
enum MockData {
    private static let gb: Int64 = 1_000_000_000

    static let storage = StorageSummary(
        usedBytes: 238 * gb,
        totalBytes: 256 * gb,
        potentialCleanupBytes: 42_700_000_000,
        categoryBytes: [
            .similarPhotos: 9_800_000_000,
            .screenshots: 5_100_000_000,
            .largeVideos: 18_200_000_000,
            .screenRecordings: 4_300_000_000,
            .blurryPhotos: 2_200_000_000,
        ]
    )
}

extension Int64 {
    /// "9.8 GB" / "18 GB" / "238 GB" / "890 MB" / "68 KB" — decimal units, one decimal only when it carries
    /// information. Mirrors `formattedBytes()` on Android so both apps show identical numbers.
    var formattedBytes: String {
        let gb = Double(self) / 1e9
        switch gb {
        case 100...: return "\(Int(gb.rounded())) GB"
        case 1...:
            let rounded = (gb * 10).rounded() / 10
            return rounded == rounded.rounded() ? "\(Int(rounded)) GB" : rounded.formatted(.number.precision(.fractionLength(1))) + " GB"
        case 0.001...: return "\(Int((Double(self) / 1e6).rounded())) MB"
        case _ where self > 0: return "\(Swift.max(1, Int((Double(self) / 1e3).rounded()))) KB"
        default: return "0 MB"
        }
    }
}
