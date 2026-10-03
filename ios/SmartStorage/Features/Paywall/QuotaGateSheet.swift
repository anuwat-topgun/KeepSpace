import SwiftUI

/// Bottom sheet shown when a deletion is bigger than what's left of this month's free cleanup
/// (store/PAYWALL_DESIGN.md §3.2). It is an offer, never a wall: a smaller delete is always possible while allowance remains.
struct QuotaGateSheet: View {
    let prompt: QuotaGatePrompt
    var onUnlock: () -> Void
    var onDeletePartial: ([String]) -> Void
    var onNotNow: () -> Void

    private var title: String {
        prompt.kind == .exhausted ? "You've used this month's free cleanup" : "You're close to this month's free limit"
    }

    private var message: String {
        switch prompt.kind {
        case .exhausted:
            localizedFormat("Free includes %@ of cleanup each month. It resets on %@.", prompt.limit.formattedBytes, resetText)
        case .noneFit:
            "None of the selected items fit in what's left. Select fewer or smaller items, or unlock Pro.".localizedUI
        case .partial:
            localizedFormat("Your selection is %@. You can free %@ more on Free.", prompt.selectionBytes.formattedBytes, prompt.remaining.formattedBytes)
        }
    }

    private var resetText: String { prompt.resetDate.formatted(.dateTime.month(.wide).day()) }

    private var fraction: Double { prompt.limit > 0 ? min(1, Double(prompt.used) / Double(prompt.limit)) : 0 }

    var body: some View {
        ScrollView {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text(LocalizedStringKey(title)).font(Typography.metric).foregroundStyle(Palette.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text(message).font(Typography.body).foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            VStack(alignment: .leading, spacing: 6) {
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Palette.surfaceMuted)
                        Capsule().fill(Tint.amber.foreground).frame(width: max(6, proxy.size.width * fraction))
                    }
                }
                .frame(height: 8)
                .accessibilityHidden(true)
                Text(localizedFormat("%@ of %@ used this month · Resets %@", prompt.used.formattedBytes, prompt.limit.formattedBytes, resetText))
                    .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .accessibilityElement(children: .combine)

            VStack(spacing: 10) {
                Button("Unlock Unlimited Cleanup", action: onUnlock)
                    .buttonStyle(PrimaryButtonStyle(showsArrow: false))
                if let ids = prompt.partialIDs, let bytes = prompt.partialBytes {
                    Button(localizedFormat("Delete %@ (safest first)", bytes.formattedBytes)) { onDeletePartial(ids) }
                        .buttonStyle(.secondary)
                }
                Button("Not now", action: onNotNow)
                    .font(.system(.body, weight: .medium))
                    .frame(minHeight: 44)
                    .foregroundStyle(Palette.textSecondary)
            }

            Text("Nothing is deleted without your confirmation.")
                .font(.footnote).foregroundStyle(Palette.textSecondary)
                .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 20)
        .padding(.top, 24)
        .padding(.bottom, 12)
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
        }
        .background(Palette.background.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

/// Presents the quota gate for a prompt and, if the person chooses to unlock, opens the paywall *after* the sheet has gone
/// (SwiftUI can't show a full-screen cover while a sheet is still up).
private struct QuotaGateModifier: ViewModifier {
    @Binding var prompt: QuotaGatePrompt?
    var onDeletePartial: ([String]) -> Void
    @Environment(AppRouter.self) private var router
    @State private var wantsPaywall = false

    func body(content: Content) -> some View {
        content.sheet(item: $prompt, onDismiss: {
            if wantsPaywall {
                wantsPaywall = false
                router.presentPaywall(focus: .unlimitedCleanup)
            }
        }) { prompt in
            QuotaGateSheet(prompt: prompt,
                           onUnlock: { wantsPaywall = true; self.prompt = nil },
                           onDeletePartial: { ids in self.prompt = nil; onDeletePartial(ids) },
                           onNotNow: { self.prompt = nil })
        }
    }
}

extension View {
    func quotaGate(_ prompt: Binding<QuotaGatePrompt?>, onDeletePartial: @escaping ([String]) -> Void) -> some View {
        modifier(QuotaGateModifier(prompt: prompt, onDeletePartial: onDeletePartial))
    }
}
