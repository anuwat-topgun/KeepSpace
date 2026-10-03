import SwiftUI

/// Placeholder media tile. `variant` nudges the gradient so a strip of the same style
/// still reads as distinct shots. Swapped for real `PHAsset` thumbnails once the media engine lands.
struct MediaThumbnail: View {
    let style: ThumbnailStyle
    var variant: Int = 0
    var cornerRadius: CGFloat = 12
    var symbolScale: CGFloat = 0.3

    var body: some View {
        GeometryReader { proxy in
            let side = min(proxy.size.width, proxy.size.height)
            ZStack {
                LinearGradient(colors: style.colors, startPoint: startPoint, endPoint: endPoint)
                Image(systemName: style.symbol)
                    .font(.system(size: max(side * symbolScale, 12), weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .accessibilityHidden(true)
    }

    private var startPoint: UnitPoint {
        [.topLeading, .top, .topTrailing, .leading][variant % 4]
    }

    private var endPoint: UnitPoint {
        [.bottomTrailing, .bottom, .bottomLeading, .trailing][variant % 4]
    }
}

/// Translucent "+8" tile that ends a thumbnail strip.
struct OverflowTile: View {
    let count: Int
    var cornerRadius: CGFloat = 12

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(.gray.opacity(0.55))
            .overlay(
                Text("+\(count)")
                    .font(.system(.title3, weight: .semibold))
                    .foregroundStyle(.white)
            )
            .accessibilityLabel(localizedFormat("%d more", count))
    }
}

/// Row of thumbnails that fills the available width, ending in an overflow tile.
struct ThumbnailStrip: View {
    let style: ThumbnailStyle
    let totalCount: Int
    /// Real assets to show; empty = gradient placeholders.
    var assetIDs: [String] = []
    var visibleCount: Int = 5
    var aspectRatio: CGFloat = 0.78

    var body: some View {
        let shown = min(visibleCount, totalCount)
        let overflow = totalCount - shown
        HStack(spacing: 6) {
            ForEach(0..<shown, id: \.self) { index in
                AssetImage(assetID: assetIDs.indices.contains(index) ? assetIDs[index] : nil, fallback: style, variant: index)
                    .aspectRatio(aspectRatio, contentMode: .fit)
            }
            if overflow > 0 {
                OverflowTile(count: overflow)
                    .aspectRatio(aspectRatio, contentMode: .fit)
            }
        }
    }
}

/// Duration pill overlaid on video thumbnails ("08:42").
struct DurationBadge: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(.caption, weight: .semibold).monospacedDigit())
            .foregroundStyle(.white)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(.black.opacity(0.55), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}
