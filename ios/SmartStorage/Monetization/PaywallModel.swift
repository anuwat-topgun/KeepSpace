import Foundation

// The decisions behind the paywall, kept apart from the views so they can be tested and mirrored on Android
// (PaywallModel.kt). Copy rules: store/PAYWALL_DESIGN.md §3.1.

/// The six benefits the paywall lists. Only features that are built (PAYWALL_DESIGN.md principle 5).
enum ProBenefit: CaseIterable, Identifiable, Sendable {
    case unlimitedCleanup, compressVideos, smartScreenshots, receiptFiling, aiTaste, unlimitedBackupAndRules

    var id: Self { self }

    init(_ feature: ProFeature) {
        switch feature {
        case .unlimitedCleanup: self = .unlimitedCleanup
        case .videoCompression: self = .compressVideos
        case .screenshotCategories: self = .smartScreenshots
        case .receiptFiling: self = .receiptFiling
        case .aiTaste: self = .aiTaste
        case .unlimitedBackup, .unlimitedRules, .multipleCloudAccounts: self = .unlimitedBackupAndRules
        }
    }
}

/// What the primary button says. Each case carries only what the store told us.
enum PaywallCTA: Equatable, Sendable {
    case startTrial(days: Int)
    case subscribeYearly(price: String)
    case subscribeMonthly(price: String)
    case buyLifetime(price: String)
}

/// The disclosure under the button. A subscription always says it renews, and a trial says what it costs afterwards.
enum PaywallFinePrint: Equatable, Sendable {
    case trialThenYearly(days: Int, price: String)
    case yearly(price: String)
    case monthly(price: String)
    case lifetime
}

enum PaywallModel {
    /// A "Save N%" chip is only shown when it is at least this much (and computed from the store's prices).
    static let minimumSavingPercent = 20

    /// Yearly is pre-selected; otherwise the first plan available.
    static func defaultSelection(_ offers: [ProOffer]) -> ProProduct? {
        offers.first { $0.product == .annual }?.product ?? offers.first?.product
    }

    /// How much cheaper yearly is than twelve months of monthly, in whole percent; nil if unknown or under the minimum.
    static func savePercent(_ offers: [ProOffer]) -> Int? {
        guard let yearly = offers.first(where: { $0.product == .annual })?.priceValue,
              let monthly = offers.first(where: { $0.product == .monthly })?.priceValue,
              monthly > 0, yearly > 0 else { return nil }
        let full = monthly * 12
        guard yearly < full else { return nil }
        let percent = Int(NSDecimalNumber(decimal: (full - yearly) / full * 100).doubleValue) // truncates: never overstate
        return percent >= minimumSavingPercent ? percent : nil
    }

    static func cta(for offer: ProOffer) -> PaywallCTA {
        switch offer.product {
        case .annual: offer.trialDays.map { .startTrial(days: $0) } ?? .subscribeYearly(price: offer.displayPrice)
        case .monthly: .subscribeMonthly(price: offer.displayPrice)
        case .lifetime: .buyLifetime(price: offer.displayPrice)
        }
    }

    static func finePrint(for offer: ProOffer) -> PaywallFinePrint {
        switch offer.product {
        case .annual: offer.trialDays.map { .trialThenYearly(days: $0, price: offer.displayPrice) } ?? .yearly(price: offer.displayPrice)
        case .monthly: .monthly(price: offer.displayPrice)
        case .lifetime: .lifetime
        }
    }
}
