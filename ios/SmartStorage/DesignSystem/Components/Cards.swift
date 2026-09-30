import SwiftUI

enum CardStyle {
    /// Plain white rounded card.
    case plain
    /// Soft icy-blue wash for hero / summary cards.
    case hero
    /// Pale info card (privacy notes, estimates).
    case info
    /// Selected state: icy wash with an accent outline.
    case selected
}

struct Card<Content: View>: View {
    var style: CardStyle = .plain
    var padding: CGFloat = Metrics.cardPadding
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(background, in: shape)
            .overlay(shape.strokeBorder(borderColor, lineWidth: style == .selected ? 1.5 : 1))
            .shadow(color: .black.opacity(0.04), radius: 12, y: 4)
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
    }

    private var background: AnyShapeStyle {
        switch style {
        case .plain: AnyShapeStyle(Palette.surface)
        case .hero, .selected: AnyShapeStyle(Palette.heroGradient)
        case .info: AnyShapeStyle(Palette.icyBlue.opacity(0.6))
        }
    }

    private var borderColor: Color {
        style == .selected ? Palette.accent : Palette.separator.opacity(0.6)
    }
}

/// Rounded square holding an SF Symbol on a soft tint.
struct IconTile: View {
    let systemName: String
    var tint: Tint = .teal
    var size: CGFloat = 52

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(tint.foreground)
            .frame(width: size, height: size)
            .background(tint.background, in: RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// Uppercase letter-spaced caption ("DEVICE STORAGE").
struct SectionLabel: View {
    let text: String

    init(_ text: String) { self.text = text }

    var body: some View {
        Text(LocalizedStringKey(text)).textCase(.uppercase)
            .font(Typography.sectionLabel)
            .tracking(2)
            .foregroundStyle(Palette.textSecondary)
    }
}

/// Large left-aligned screen title with optional subtitle (every mockup uses this).
struct ScreenHeader: View {
    let title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(LocalizedStringKey(title))
                .font(Typography.screenTitle)
                .foregroundStyle(Palette.textPrimary)
                .accessibilityAddTraits(.isHeader)
            if let subtitle {
                Text(LocalizedStringKey(subtitle))
                    .font(Typography.screenSubtitle)
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }
}

/// Rounded capsule progress bar used for storage usage.
struct UsageBar: View {
    /// 0...1
    let fraction: Double

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.surfaceMuted)
                Capsule()
                    .fill(Palette.accentGradient)
                    .frame(width: proxy.size.width * min(max(fraction, 0), 1))
            }
        }
        .frame(height: 10)
        .accessibilityElement()
        .accessibilityValue(Text(fraction, format: .percent.precision(.fractionLength(0))))
    }
}
