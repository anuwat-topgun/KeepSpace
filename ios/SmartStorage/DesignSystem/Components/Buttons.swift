import SwiftUI

/// Teal gradient pill — the primary CTA on every screen.
struct PrimaryButtonStyle: ButtonStyle {
    var showsArrow = true

    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 10) {
            configuration.label
            if showsArrow {
                Image(systemName: "arrow.right")
            }
        }
        .font(Typography.button)
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, minHeight: 56)
        .padding(.horizontal, 20)
        .background(Palette.accentGradient, in: Capsule())
        .shadow(color: Palette.accent.opacity(0.25), radius: 12, y: 6)
        .scaleEffect(configuration.isPressed ? 0.98 : 1)
        .animation(.spring(duration: 0.25), value: configuration.isPressed)
    }
}

/// Pale filled pill ("Review All") or outlined pill ("Keep Local Copies").
struct SecondaryButtonStyle: ButtonStyle {
    var outlined = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Typography.button)
            .foregroundStyle(Palette.accent)
            .frame(maxWidth: .infinity, minHeight: 52)
            .padding(.horizontal, 20)
            .background(outlined ? Color.clear : Palette.icyBlue, in: Capsule())
            .overlay(Capsule().strokeBorder(outlined ? Palette.accent : .clear, lineWidth: 1.5))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.spring(duration: 0.25), value: configuration.isPressed)
    }
}

/// Small inline pill action inside rows ("Compress", "Review").
struct InlinePillButtonStyle: ButtonStyle {
    var tint: Tint = .teal

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(.subheadline, weight: .semibold))
            .lineLimit(1)
            .fixedSize()
            .foregroundStyle(tint.foreground)
            .padding(.horizontal, 16)
            .padding(.vertical, 9)
            .background(tint.background, in: Capsule())
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

extension ButtonStyle where Self == PrimaryButtonStyle {
    static var primary: PrimaryButtonStyle { PrimaryButtonStyle() }
}

extension ButtonStyle where Self == SecondaryButtonStyle {
    static var secondary: SecondaryButtonStyle { SecondaryButtonStyle() }
    static var secondaryOutlined: SecondaryButtonStyle { SecondaryButtonStyle(outlined: true) }
}
