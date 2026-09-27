import SwiftUI
import UIKit

/// Color tokens from the design handoff (§2.1). Light values follow the mockups;
/// dark values are derived so the app stays usable in Dark Mode.
enum Palette {
    static let background = Color(light: 0xF8F7F4, dark: 0x0E1419)
    static let surface = Color(light: 0xFFFFFF, dark: 0x172029)
    static let surfaceMuted = Color(light: 0xEEF2F6, dark: 0x1F2A35)
    static let separator = Color(light: 0xE4E8ED, dark: 0x2A3642)

    static let textPrimary = Color(light: 0x0B1B2B, dark: 0xF2F5F8)
    static let textSecondary = Color(light: 0x6B7A8F, dark: 0x97A5B5)

    static let accent = Color(light: 0x1E8FB0, dark: 0x4FB8D4)
    static let accentGradientStart = Color(light: 0x52B6CE, dark: 0x3FA7C4)
    static let accentGradientEnd = Color(light: 0x16809F, dark: 0x1B7390)
    static let icyBlue = Color(light: 0xE7F4FA, dark: 0x16303D)

    static let accentGradient = LinearGradient(
        colors: [accentGradientStart, accentGradientEnd],
        startPoint: .leading,
        endPoint: .trailing
    )

    /// Soft wash used behind hero cards.
    static let heroGradient = LinearGradient(
        colors: [surface, icyBlue],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}

/// Semantic tints used by icon tiles, badges and status indicators.
enum Tint: CaseIterable, Sendable {
    case teal, blue, purple, coral, mint, amber, gray

    var foreground: Color {
        switch self {
        case .teal: Palette.accent
        case .blue: Color(light: 0x2F7FE8, dark: 0x6BA8F5)
        case .purple: Color(light: 0x6A5AE0, dark: 0x9D91F2)
        case .coral: Color(light: 0xE8534F, dark: 0xF28782)
        case .mint: Color(light: 0x2FA878, dark: 0x5CCB9C)
        case .amber: Color(light: 0xE89B1F, dark: 0xF2B955)
        case .gray: Color(light: 0x6B7A8F, dark: 0x97A5B5)
        }
    }

    var background: Color {
        switch self {
        case .teal: Color(light: 0xE3F3F6, dark: 0x163239)
        case .blue: Color(light: 0xE6F0FD, dark: 0x192B42)
        case .purple: Color(light: 0xEEEBFD, dark: 0x252242)
        case .coral: Color(light: 0xFDE8E7, dark: 0x3A2224)
        case .mint: Color(light: 0xE2F5EC, dark: 0x173327)
        case .amber: Color(light: 0xFDF1DC, dark: 0x3A2E17)
        case .gray: Color(light: 0xEEF1F4, dark: 0x222C36)
        }
    }
}

extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
