import StoreKit
import SwiftUI

/// Settings → Subscription (store/PAYWALL_DESIGN.md §3.3): the plan, this month's free allowance, and the way to upgrade,
/// restore or manage. A person on Pro never sees the paywall from here — only their plan.
struct SubscriptionView: View {
    @Environment(AppRouter.self) private var router
    @Environment(MonetizationStore.self) private var monetization
    @Environment(ProStoreService.self) private var store
    @Environment(RuleStore.self) private var rules
    @Environment(CloudStore.self) private var cloud

    @State private var showsManage = false
    @State private var restoreResult: Bool?

    var body: some View {
        let allowances = monetization.allowances
        ScreenScaffold {
            ScreenHeader(title: "Subscription").padding(.bottom, 8)

            Card(style: .hero) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(Self.planTitle(monetization.status))
                        .font(Typography.metric).foregroundStyle(Palette.textPrimary)
                    if let detail = Self.planDetail(monetization.status) {
                        Text(detail).font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                    }
                }
            }

            if !allowances.isPro { usageCard(allowances) }

            if allowances.isPro {
                if case .pro(let product, _, _, _, _) = monetization.status, product.isSubscription {
                    Button("Manage Subscription") { showsManage = true }.buttonStyle(.secondary)
                }
            } else {
                Button("Upgrade to Pro") { router.presentPaywall() }.buttonStyle(PrimaryButtonStyle(showsArrow: false))
            }

            Button("Restore Purchases") { Task { restoreResult = await store.restore() } }
                .buttonStyle(.secondaryOutlined)
            if restoreResult != nil {
                Text(monetization.isPro ? "Purchases restored." : (restoreResult == true ? "No active Pro purchase found." : "Can't reach the store. Check your connection."))
                    .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
            }

            if !allowances.isPro {
                SectionLabel("Pro adds")
                Card(padding: 14) {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(["Unlimited Cleanup", "Compress Videos", "Smart Screenshots", "Receipt Filing", "AI Taste", "Unlimited Backup & Rules"], id: \.self) { item in
                            Label { Text(LocalizedStringKey(item)).foregroundStyle(Palette.textPrimary) } icon: {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(Palette.accent)
                            }
                            .font(Typography.body)
                        }
                    }
                }
            }
        }
        .manageSubscriptionsSheet(isPresented: $showsManage)
    }

    private func usageCard(_ a: Allowances) -> some View {
        let limits = monetization.limits
        let usedBytes = min(a.ledger.cleanupBytes, limits.cleanupBytesPerMonth)
        return Card {
            VStack(alignment: .leading, spacing: 16) {
                SectionLabel("This month")
                Meter(title: "Cleanup", value: Double(usedBytes), limit: Double(limits.cleanupBytesPerMonth),
                      text: localizedFormat("%@ of %@ used", usedBytes.formattedBytes, limits.cleanupBytesPerMonth.formattedBytes))
                Meter(title: "Backup", value: Double(min(a.ledger.backupFiles, limits.backupFilesPerMonth)), limit: Double(limits.backupFilesPerMonth),
                      text: localizedFormat("%d of %d files used", min(a.ledger.backupFiles, limits.backupFilesPerMonth), limits.backupFilesPerMonth))
                Meter(title: "Rules", value: Double(min(rules.userRuleCount, limits.maxRules)), limit: Double(limits.maxRules),
                      text: localizedFormat("%d of %d used", min(rules.userRuleCount, limits.maxRules), limits.maxRules))
                Text(localizedFormat("Resets %@", a.resetDate.formatted(.dateTime.month(.wide).day())))
                    .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
            }
        }
    }

    static func planTitle(_ status: ProStatus) -> String {
        switch status {
        case .free: "You're on the Free plan.".localizedUI
        case .pro(.annual, _, _, _, _): "KeepSpace Pro · Yearly".localizedUI
        case .pro(.monthly, _, _, _, _): "KeepSpace Pro · Monthly".localizedUI
        case .pro(.lifetime, _, _, _, _): "KeepSpace Pro · Lifetime".localizedUI
        }
    }

    static func planDetail(_ status: ProStatus) -> String? {
        switch status {
        case .free: return "Everything you see is free to scan and review.".localizedUI
        case .pro(let product, let expiresAt, let isTrial, let willRenew, let isGrace):
            if product == .lifetime { return "Thank you for supporting KeepSpace.".localizedUI }
            if isGrace { return "Can't reach the store. Pro stays on for now.".localizedUI }
            guard let expiresAt else { return nil }
            let date = expiresAt.formatted(.dateTime.month(.wide).day())
            if isTrial { return localizedFormat(willRenew ? "Free trial ends %@, then renews." : "Free trial ends %@.", date) }
            return localizedFormat(willRenew ? "Renews %@" : "Ends %@", date)
        }
    }

    /// The row's subtitle in Settings: "Free", "Pro · renews Oct 28", "Pro · Lifetime".
    static func settingsSubtitle(_ status: ProStatus) -> String {
        switch status {
        case .free: return "Free".localizedUI
        case .pro(let product, let expiresAt, _, let willRenew, _):
            if product == .lifetime { return "Pro · Lifetime".localizedUI }
            guard let expiresAt else { return "Pro".localizedUI }
            let date = expiresAt.formatted(.dateTime.month(.abbreviated).day())
            return localizedFormat(willRenew ? "Pro · renews %@" : "Pro · ends %@", date)
        }
    }
}

/// A labelled usage bar. Turns amber near the limit; the text is the accessible value.
private struct Meter: View {
    let title: String
    let value: Double
    let limit: Double
    let text: String

    private var fraction: Double { limit > 0 ? min(1, value / limit) : 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(LocalizedStringKey(title)).font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                Spacer()
                Text(text).font(Typography.metadata).foregroundStyle(Palette.textSecondary)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.surfaceMuted)
                    Capsule().fill(fraction >= 0.8 ? Tint.amber.foreground : Palette.accent)
                        .frame(width: max(fraction > 0 ? 6 : 0, proxy.size.width * fraction))
                }
            }
            .frame(height: 8)
            .accessibilityHidden(true)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(LocalizedStringKey(title)))
        .accessibilityValue(Text(text))
    }
}

#Preview {
    NavigationStack { SubscriptionView() }
        .previewEnvironment()
}
