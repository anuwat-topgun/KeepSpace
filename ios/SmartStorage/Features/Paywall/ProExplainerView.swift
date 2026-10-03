import SwiftUI

/// One-screen explanation shown where a Pro feature is tapped on Free (store/PAYWALL_DESIGN.md §3.4): what it does, what
/// Free still has, and a way to unlock. It is an invitation, not an error, and it never hides what the person already has.
struct ProExplainerView: View {
    let feature: ProFeature
    /// Called when the person taps Unlock. Screens open the paywall; a sheet closes itself first.
    var onUnlock: () -> Void

    private struct Content {
        let icon: String
        let tint: Tint
        let title: String
        let what: String
        let free: String
    }

    private var content: Content {
        switch feature {
        case .videoCompression:
            Content(icon: "film.stack.fill", tint: .purple, title: "Compress Videos",
                    what: "Shrinks a big video on your device and keeps the quality. You review the result before the original is removed.",
                    free: "On Free you can still see which videos take the most space and review them for deletion.")
        case .receiptFiling:
            Content(icon: "doc.text.fill", tint: .mint, title: "Receipt Filing",
                    what: "Finds receipts in your screenshots and photos, reads the merchant, date and amount on this device, and shows where each one would be filed.",
                    free: "On Free your receipts stay where they are, and you can still review old screenshots.")
        case .aiTaste:
            Content(icon: "wand.and.stars", tint: .amber, title: "AI Taste",
                    what: "Best Shot learns from the photos you keep and starts recommending the kind of shot you like. It learns on this device and nothing leaves it.",
                    free: "On Free, Best Shot still picks the best photo in every group.")
        default:
            Content(icon: "sparkle", tint: .teal, title: "KeepSpace Pro",
                    what: "Unlimited cleanup, video compression, smart screenshots, Receipt Filing, AI Taste, and unlimited backup and rules.",
                    free: "Scanning, the Safety Score and review stay free.")
        }
    }

    var body: some View {
        let item = content
        ScreenScaffold {
            ScreenHeader(title: item.title).padding(.bottom, 8)

            Card(style: .hero) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 10) {
                        IconTile(systemName: item.icon, tint: item.tint)
                        ProChip()
                    }
                    Text(LocalizedStringKey(item.what)).font(Typography.body).foregroundStyle(Palette.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Card(style: .info) {
                Label { Text(LocalizedStringKey(item.free)).foregroundStyle(Palette.textSecondary) } icon: {
                    Image(systemName: "checkmark.circle").foregroundStyle(Palette.accent)
                }
                .font(Typography.metadata)
            }

            Button("Unlock KeepSpace Pro", action: onUnlock)
                .buttonStyle(PrimaryButtonStyle(showsArrow: false))
        }
    }
}

/// Shows `content` to Pro, and the explainer to everyone else. Pro features stay visible; only what's behind them is locked.
struct ProGate<Content: View>: View {
    let feature: ProFeature
    @ViewBuilder var content: Content
    @Environment(MonetizationStore.self) private var monetization
    @Environment(AppRouter.self) private var router

    var body: some View {
        if monetization.allowances.allows(feature) {
            content
        } else {
            ProExplainerView(feature: feature) { router.presentPaywall(focus: feature) }
        }
    }
}
