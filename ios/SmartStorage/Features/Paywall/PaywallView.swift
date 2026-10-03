import SwiftUI

/// KeepSpace Pro paywall (store/PAYWALL_DESIGN.md §3.1). Full screen; the primary button and the disclosure under it
/// follow the selected plan. Prices, trial length and eligibility all come from the store.
struct PaywallView: View {
    var focus: ProFeature?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(MonetizationStore.self) private var monetization
    @Environment(ProStoreService.self) private var store

    @State private var selected: ProProduct?
    @State private var isWorking = false
    @State private var message: Message?

    private enum Message: Equatable { case pending, failed, restoredNothing, restoreFailed }

    private let privacyURL = URL(string: "https://github.com/anuwat-topgun/KeepSpace/blob/main/legal/privacy-policy.md")!
    private let termsURL = URL(string: "https://github.com/anuwat-topgun/KeepSpace/blob/main/legal/terms-of-use.md")!

    private var selectedOffer: ProOffer? {
        store.offers.first { $0.product == selected } ?? store.offers.first { $0.product == PaywallModel.defaultSelection(store.offers) }
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        header
                        plans
                        benefits
                        statusMessage
                    }
                    .frame(maxWidth: 560)
                    .padding(.horizontal, Metrics.gutter(sizeClass))
                    .padding(.bottom, 16)
                    .frame(maxWidth: .infinity)
                }
                .scrollContentBackground(.hidden)
                .onAppear {
                    guard let focus else { return }
                    withAnimation { proxy.scrollTo(ProBenefit(focus), anchor: .center) }
                }
            }
            footer
        }
        .background(Palette.background.ignoresSafeArea())
        .onChange(of: monetization.isPro) { _, isPro in
            if isPro { dismiss() } // success: back to where the person was; the gated action is offered again, not run
        }
    }

    // MARK: Pieces

    private var topBar: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.system(.body, weight: .semibold))
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(Text("Close"))
            Spacer()
            Button("Restore") { Task { await restore() } }
                .font(.system(.body, weight: .medium))
                .frame(minHeight: 44)
                .disabled(isWorking)
        }
        .foregroundStyle(Palette.accent)
        .padding(.horizontal, 12)
    }

    private var header: some View {
        HStack(spacing: 14) {
            IconTile(systemName: "sparkle", tint: .teal, size: 56)
            VStack(alignment: .leading, spacing: 2) {
                Text("KeepSpace Pro").font(Typography.metric).foregroundStyle(Palette.textPrimary)
                Text("Keep space. Keep what matters.").font(Typography.metadata).foregroundStyle(Palette.textSecondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var benefits: some View {
        Card(padding: 10) {
            VStack(spacing: 0) {
                ForEach(Array(ProBenefit.allCases.enumerated()), id: \.element) { index, benefit in
                    BenefitRow(benefit: benefit, isHighlighted: focus.map(ProBenefit.init) == benefit)
                        .id(benefit)
                    if index < ProBenefit.allCases.count - 1 { Divider().overlay(Palette.separator.opacity(0.5)) }
                }
            }
        }
    }

    @ViewBuilder private var plans: some View {
        if store.offers.isEmpty {
            if store.isLoadingOffers || !store.offersFailed {
                VStack(spacing: 10) { ForEach(0..<3, id: \.self) { _ in PlanSkeleton() } }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text("Loading prices"))
            } else {
                Card(style: .info) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Can't reach the store. Check your connection.")
                            .font(Typography.body).foregroundStyle(Palette.textPrimary)
                        Button("Retry") { Task { await store.loadOffers() } }
                            .buttonStyle(InlinePillButtonStyle())
                    }
                }
            }
        } else {
            let saving = PaywallModel.savePercent(store.offers)
            VStack(spacing: 10) {
                ForEach(store.offers) { offer in
                    PlanCard(offer: offer, isSelected: selectedOffer?.product == offer.product,
                             isBestValue: offer.product == .annual, savePercent: offer.product == .annual ? saving : nil) {
                        selected = offer.product
                        message = nil
                    }
                }
            }
            .accessibilityElement(children: .contain)
        }
    }

    @ViewBuilder private var statusMessage: some View {
        switch message {
        case .pending:
            note("Waiting for approval", detail: "Pro unlocks by itself once the purchase is approved.")
        case .failed:
            note("The purchase didn't go through", detail: "Please try again.")
        case .restoredNothing:
            note("No active Pro purchase found", detail: "Restore only works for purchases made with this store account.")
        case .restoreFailed:
            note("Can't reach the store", detail: "Check your connection and try again.")
        case nil:
            EmptyView()
        }
    }

    private func note(_ title: String, detail: String) -> some View {
        Card(style: .info) {
            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey(title)).font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                Text(LocalizedStringKey(detail)).font(Typography.metadata).foregroundStyle(Palette.textSecondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var footer: some View {
        VStack(spacing: 10) {
            Button {
                Task { await buy() }
            } label: {
                if isWorking {
                    ProgressView().tint(.white)
                } else {
                    Text(ctaTitle)
                }
            }
            .buttonStyle(PrimaryButtonStyle(showsArrow: false))
            .disabled(selectedOffer == nil || isWorking)

            if let offer = selectedOffer {
                Text(finePrint(for: offer))
                    .font(.system(.footnote)).foregroundStyle(Palette.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            HStack(spacing: 18) {
                Link("Terms of Use", destination: termsURL)
                Link("Privacy Policy", destination: privacyURL)
            }
            .font(.system(.footnote, weight: .medium))
            .tint(Palette.accent)

            Text("Your photos stay yours. Analysis stays on your device, and backups go only where you choose.")
                .font(.system(.caption)).foregroundStyle(Palette.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: 560)
        .padding(.horizontal, Metrics.gutter(sizeClass))
        .padding(.top, 12)
        .padding(.bottom, 8)
        .frame(maxWidth: .infinity)
        .background(.bar)
    }

    // MARK: Copy

    private var ctaTitle: String {
        guard let offer = selectedOffer else { return "Continue".localizedUI }
        return switch PaywallModel.cta(for: offer) {
        case .startTrial(let days): localizedFormat("Start %d-Day Free Trial", days)
        case .subscribeYearly(let price): localizedFormat("Subscribe · %@ / year", price)
        case .subscribeMonthly(let price): localizedFormat("Subscribe · %@ / month", price)
        case .buyLifetime(let price): localizedFormat("Buy Lifetime · %@", price)
        }
    }

    private func finePrint(for offer: ProOffer) -> String {
        switch PaywallModel.finePrint(for: offer) {
        case .trialThenYearly(let days, let price):
            localizedFormat("%d days free, then %@ per year. Renews automatically until you cancel in your account settings.", days, price)
        case .yearly(let price): localizedFormat("Renews automatically at %@ per year until you cancel.", price)
        case .monthly(let price): localizedFormat("Renews automatically at %@ per month until you cancel.", price)
        case .lifetime: "One-time purchase. No subscription, no renewal.".localizedUI
        }
    }

    // MARK: Actions

    private func buy() async {
        guard let offer = selectedOffer else { return }
        isWorking = true
        message = nil
        defer { isWorking = false }
        switch await store.purchase(offer.product) {
        case .success: break // monetization.isPro flips and the sheet dismisses
        case .pending: message = .pending
        case .cancelled: break // back to the picker, no error
        case .failed: message = .failed
        }
    }

    private func restore() async {
        isWorking = true
        message = nil
        defer { isWorking = false }
        if await store.restore() {
            if !monetization.isPro { message = .restoredNothing }
        } else {
            message = .restoreFailed
        }
    }
}

// MARK: - Subviews

private struct BenefitRow: View {
    let benefit: ProBenefit
    let isHighlighted: Bool

    private var content: (icon: String, tint: Tint, title: String, detail: String) {
        switch benefit {
        case .unlimitedCleanup: ("trash.fill", .coral, "Unlimited Cleanup", "Clear whole categories at once, with no monthly limit.")
        case .compressVideos: ("film.stack.fill", .purple, "Compress Videos", "Shrink big videos on your device and keep the quality.")
        case .smartScreenshots: ("camera.viewfinder", .blue, "Smart Screenshots", "Browse screenshots by what's in them.")
        case .receiptFiling: ("doc.text.fill", .mint, "Receipt Filing", "Find receipts and see where each would be filed.")
        case .aiTaste: ("wand.and.stars", .amber, "AI Taste", "Suggestions that learn what you like to keep.")
        case .unlimitedBackupAndRules: ("icloud.fill", .teal, "Unlimited Backup & Rules", "Back up as much as you want, with every rule and cloud account.")
        }
    }

    var body: some View {
        let item = content
        HStack(alignment: .top, spacing: 12) {
            IconTile(systemName: item.icon, tint: item.tint, size: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(LocalizedStringKey(item.title)).font(.system(.subheadline, weight: .semibold)).foregroundStyle(Palette.textPrimary)
                Text(LocalizedStringKey(item.detail)).font(.system(.footnote)).foregroundStyle(Palette.textSecondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 5)
        .padding(.horizontal, 6)
        .background(isHighlighted ? Palette.icyBlue : .clear, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

private struct PlanCard: View {
    let offer: ProOffer
    let isSelected: Bool
    let isBestValue: Bool
    let savePercent: Int?
    let action: () -> Void

    private var title: String {
        switch offer.product {
        case .annual: "Yearly"
        case .monthly: "Monthly"
        case .lifetime: "Lifetime"
        }
    }

    private var detail: String {
        switch offer.product {
        case .annual: offer.trialDays.map { localizedFormat("%d days free", $0) } ?? "Billed yearly".localizedUI
        case .monthly: "Billed monthly".localizedUI
        case .lifetime: "One-time payment".localizedUI
        }
    }

    private var unit: String {
        switch offer.product {
        case .annual: "per year".localizedUI
        case .monthly: "per month".localizedUI
        case .lifetime: "once".localizedUI
        }
    }

    private var savingText: String? { savePercent.map { localizedFormat("Save %d%%", $0) } }

    private var perMonthText: String? { offer.perMonthDisplay.map { localizedFormat("≈ %@ / month", $0) } }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(isSelected ? Palette.accent : Palette.textSecondary)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(LocalizedStringKey(title)).font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                        if isBestValue { BestValueBadge() }
                    }
                    Text(detail).font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                    if let savingText { Text(savingText).font(.system(.caption, weight: .semibold)).foregroundStyle(Tint.mint.foreground) }
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(offer.displayPrice).font(Typography.metric).foregroundStyle(Palette.textPrimary)
                    Text(unit).font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                    if let perMonthText { Text(perMonthText).font(.system(.caption)).foregroundStyle(Palette.textSecondary) }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .background(isSelected ? AnyShapeStyle(Palette.heroGradient) : AnyShapeStyle(Palette.surface),
                        in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                .strokeBorder(isSelected ? Palette.accent : Palette.separator.opacity(0.6), lineWidth: isSelected ? 2 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(accessibilityText))
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    /// "Yearly, $19.99 per year, 7 days free, best value" — VoiceOver adds "selected" from the trait.
    private var accessibilityText: String {
        [title.localizedUI, "\(offer.displayPrice) \(unit)", offer.trialDays == nil ? nil : detail,
         isBestValue ? "Best value".localizedUI : nil].compactMap { $0 }.joined(separator: ", ")
    }
}

private struct BestValueBadge: View {
    var body: some View {
        Text("Best value")
            .font(.system(.caption2, weight: .bold))
            .foregroundStyle(Palette.accent)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(Palette.icyBlue, in: Capsule())
            .accessibilityHidden(true) // spoken as part of the plan's label
    }
}

private struct PlanSkeleton: View {
    var body: some View {
        RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
            .fill(Palette.surfaceMuted)
            .frame(height: 84)
    }
}

#Preview {
    PaywallView(focus: .videoCompression)
        .environment(MonetizationStore(defaults: UserDefaults(suiteName: "preview.monetization") ?? .standard))
        .environment(ProStoreService(monetization: MonetizationStore(defaults: UserDefaults(suiteName: "preview.monetization") ?? .standard)))
}
