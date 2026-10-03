import Foundation
import Observation
import StoreKit

/// One purchasable plan, as the paywall shows it. Prices come from the store (localized, already formatted) and are never hard-coded.
struct ProOffer: Identifiable, Equatable, Sendable {
    let product: ProProduct
    let displayPrice: String
    /// Length of the free trial in days, when the store offers this person one (they haven't used it before).
    let trialDays: Int?
    var id: String { product.rawValue }
}

enum PurchaseOutcome: Equatable, Sendable {
    case success
    /// Waiting on approval (Ask to Buy, SCA). Pro unlocks by itself when it clears.
    case pending
    case cancelled
    case failed
}

/// The StoreKit 2 layer. It only translates: it reads what the App Store says the person owns and hands that to
/// `MonetizationStore.applyPurchases(_:)`, which decides what it means. No server, no account — the transaction
/// signature is verified on device, and anything unverified is ignored.
@MainActor
@Observable
final class ProStoreService {
    private(set) var offers: [ProOffer] = []
    private(set) var isLoadingOffers = false
    private(set) var offersFailed = false

    private let monetization: MonetizationStore
    private var products: [ProProduct: Product] = [:]
    private var updatesTask: Task<Void, Never>?

    init(monetization: MonetizationStore) {
        self.monetization = monetization
    }

    /// Call once at launch: starts listening for purchases made elsewhere (renewals, Family Sharing, Ask to Buy,
    /// refunds) and checks what the person owns right now.
    func start() {
        guard updatesTask == nil else { return }
        updatesTask = Task { [weak self] in
            for await update in Transaction.updates {
                guard let self else { return }
                if case .verified(let transaction) = update { await transaction.finish() }
                await self.refreshEntitlements()
            }
        }
        Task {
            await refreshEntitlements()
            await loadOffers()
        }
    }

    func loadOffers() async {
        isLoadingOffers = true
        defer { isLoadingOffers = false }
        do {
            let loaded = try await Product.products(for: ProProduct.allCases.map(\.rawValue))
            products = Dictionary(uniqueKeysWithValues: loaded.compactMap { product in
                ProProduct(rawValue: product.id).map { ($0, product) }
            })
            var next: [ProOffer] = []
            for kind in ProProduct.allCases {
                guard let product = products[kind] else { continue }
                next.append(ProOffer(product: kind, displayPrice: product.displayPrice, trialDays: await trialDays(of: product)))
            }
            offers = next
            offersFailed = next.isEmpty
        } catch {
            offersFailed = true
        }
    }

    func purchase(_ kind: ProProduct) async -> PurchaseOutcome {
        guard let product = products[kind] else { return .failed }
        do {
            switch try await product.purchase() {
            case .success(let verification):
                guard case .verified(let transaction) = verification else { return .failed }
                await transaction.finish()
                await refreshEntitlements()
                return .success
            case .pending: return .pending
            case .userCancelled: return .cancelled
            @unknown default: return .failed
            }
        } catch {
            return .failed
        }
    }

    /// "Restore Purchases": asks the App Store to re-sync (may prompt for sign-in), then re-reads what is owned.
    @discardableResult
    func restore() async -> Bool {
        do {
            try await AppStore.sync()
        } catch {
            return false
        }
        await refreshEntitlements()
        return true
    }

    /// Re-reads current entitlements and applies them. The App Store already leaves out expired and refunded ones.
    func refreshEntitlements() async {
        var purchases: [StorePurchase] = []
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            purchases.append(await Self.purchase(from: transaction))
        }
        monetization.applyPurchases(purchases)
    }

    private static func purchase(from transaction: Transaction) async -> StorePurchase {
        let isTrial: Bool
        if #available(iOS 17.2, *) {
            isTrial = transaction.offer?.type == .introductory
        } else {
            isTrial = transaction.offerType == .introductory
        }
        var willRenew = false
        if transaction.productType == .autoRenewable,
           let status = await transaction.subscriptionStatus,
           case .verified(let renewal) = status.renewalInfo {
            willRenew = renewal.willAutoRenew
        }
        return StorePurchase(productID: transaction.productID, purchasedAt: transaction.purchaseDate,
                             expiresAt: transaction.expirationDate, isTrial: isTrial,
                             isRevoked: transaction.revocationDate != nil, willRenew: willRenew)
    }

    /// Trial length only if this person is still eligible for it (Apple allows one intro offer per subscription group).
    private func trialDays(of product: Product) async -> Int? {
        guard let subscription = product.subscription,
              let intro = subscription.introductoryOffer, intro.paymentMode == .freeTrial,
              await subscription.isEligibleForIntroOffer else { return nil }
        let period = intro.period
        switch period.unit {
        case .day: return period.value
        case .week: return period.value * 7
        case .month: return period.value * 30
        case .year: return period.value * 365
        @unknown default: return nil
        }
    }
}
