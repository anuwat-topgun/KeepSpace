import SwiftUI

/// Type scale from the design handoff (§2.2). Built on text styles so Dynamic Type still scales.
enum Typography {
    /// Extra-bold, large, left-aligned screen title.
    static let screenTitle = Font.system(.largeTitle, weight: .heavy)
    static let screenSubtitle = Font.system(.title3, weight: .regular)
    static let cardHeadline = Font.system(.headline, weight: .semibold)
    static let body = Font.system(.body)
    static let metadata = Font.system(.subheadline)
    static let metricLarge = Font.system(.largeTitle, weight: .bold)
    static let metric = Font.system(.title2, weight: .bold)
    /// Uppercase, letter-spaced section label ("DEVICE STORAGE").
    static let sectionLabel = Font.system(.caption, weight: .medium)
    static let button = Font.system(.headline, weight: .semibold)
}

/// Spacing and shape tokens.
enum Metrics {
    static let cardRadius: CGFloat = 24
    static let tileRadius: CGFloat = 16
    static let cardPadding: CGFloat = 20
    static let stackSpacing: CGFloat = 14
    static let sectionSpacing: CGFloat = 24

    /// Horizontal gutter by size class: phones get the mockup margin, tablets breathe more.
    static func gutter(_ sizeClass: UserInterfaceSizeClass?) -> CGFloat {
        sizeClass == .regular ? 32 : 16
    }

    /// Single-column content never stretches wider than this on iPad.
    static let readableWidth: CGFloat = 680
    /// Grid content (e.g. Home category tiles) may use a wider column.
    static let wideContentWidth: CGFloat = 960
}
