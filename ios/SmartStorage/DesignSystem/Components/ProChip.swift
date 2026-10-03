import SwiftUI

/// Small "PRO" mark on anything that opens a Pro feature. Never used to hide or blur content (store/PAYWALL_DESIGN.md §3.4).
struct ProChip: View {
    var body: some View {
        Text(verbatim: "PRO")
            .font(.system(.caption2, weight: .bold))
            .foregroundStyle(Palette.accent)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(Palette.icyBlue, in: Capsule())
            .accessibilityLabel(Text("Pro"))
    }
}
