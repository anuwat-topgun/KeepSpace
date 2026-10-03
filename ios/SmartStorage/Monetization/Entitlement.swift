import Foundation

// Free / Pro entitlement. Pure logic, no StoreKit: the store layer turns its transactions into `StorePurchase`
// values and this decides what they mean. Mirrors Entitlement.kt. Design: store/PAYWALL_DESIGN.md.

/// A Pro product. Raw values are the product IDs created in App Store Connect / Play Console.
enum ProProduct: String, CaseIterable, Codable, Sendable {
    case annual = "com.keepspace.app.pro.annual"
    case monthly = "com.keepspace.app.pro.monthly"
    case lifetime = "com.keepspace.app.pro.lifetime"

    var isSubscription: Bool { self != .lifetime }
}

/// What the store reports about one purchase.
struct StorePurchase: Equatable, Codable, Sendable {
    let productID: String
    let purchasedAt: Date
    /// End of the paid (or trial) period. Nil for a lifetime purchase.
    let expiresAt: Date?
    let isTrial: Bool
    /// Refunded or revoked by the store (including Family Sharing access being removed).
    let isRevoked: Bool
    let willRenew: Bool
}

/// The result: free, or Pro and how.
enum ProStatus: Equatable, Codable, Sendable {
    case free
    /// `expiresAt` is nil for lifetime. `isGrace` means the store couldn't be reached and the status is
    /// being carried over from the last successful check.
    case pro(product: ProProduct, expiresAt: Date?, isTrial: Bool, willRenew: Bool, isGrace: Bool)

    var isPro: Bool {
        if case .pro = self { true } else { false }
    }

    var product: ProProduct? {
        if case .pro(let product, _, _, _, _) = self { product } else { nil }
    }

    func allows(_ feature: ProFeature) -> Bool { isPro }
}

/// Everything that is Pro-only. Safety, viewing scan results and Best Shot are never in this list.
enum ProFeature: CaseIterable, Sendable {
    case unlimitedCleanup, videoCompression, screenshotCategories, receiptFiling, aiTaste, unlimitedBackup, unlimitedRules, multipleCloudAccounts
}

enum Entitlement {
    /// How long a Pro status survives without reaching the store (offline, store outage).
    static let offlineGrace: TimeInterval = 3 * 86_400

    /// Decides the status.
    /// - `purchases`: the store's current purchases, or nil when the store could not be reached.
    ///   A list (even an empty one) is authoritative; nil falls back to `cached` with a short grace.
    static func resolve(purchases: [StorePurchase]?, cached: ProStatus, now: Date) -> ProStatus {
        guard let purchases else { return carryOver(cached, now: now) }

        let valid: [(product: ProProduct, purchase: StorePurchase)] = purchases.compactMap { purchase in
            guard let product = ProProduct(rawValue: purchase.productID), !purchase.isRevoked else { return nil }
            if product == .lifetime { return (product, purchase) }
            guard let expiry = purchase.expiresAt, expiry > now else { return nil }
            return (product, purchase)
        }
        // Lifetime beats any subscription; otherwise the one that lasts longest.
        if valid.contains(where: { $0.product == .lifetime }) {
            return .pro(product: .lifetime, expiresAt: nil, isTrial: false, willRenew: false, isGrace: false)
        }
        guard let best = valid.max(by: { ($0.purchase.expiresAt ?? .distantPast) < ($1.purchase.expiresAt ?? .distantPast) }) else {
            return .free
        }
        return .pro(product: best.product, expiresAt: best.purchase.expiresAt, isTrial: best.purchase.isTrial,
                    willRenew: best.purchase.willRenew, isGrace: false)
    }

    private static func carryOver(_ cached: ProStatus, now: Date) -> ProStatus {
        guard case .pro(let product, let expiresAt, let isTrial, let willRenew, _) = cached else { return .free }
        guard let expiresAt else { return cached } // lifetime
        if now < expiresAt { return cached }
        if now < expiresAt.addingTimeInterval(offlineGrace) {
            return .pro(product: product, expiresAt: expiresAt, isTrial: isTrial, willRenew: willRenew, isGrace: true)
        }
        return .free
    }
}
