package com.smartstorage.cleaner.monetization

// The decisions behind the paywall, kept apart from the screens so they can be tested.
// Mirrors ios/SmartStorage/Monetization/PaywallModel.swift. Copy rules: store/PAYWALL_DESIGN.md §3.1.

/** The six benefits the paywall lists. Only features that are built. */
enum class ProBenefit {
    UnlimitedCleanup, CompressVideos, SmartScreenshots, ReceiptFiling, AiTaste, UnlimitedBackupAndRules;

    companion object {
        fun of(feature: ProFeature): ProBenefit = when (feature) {
            ProFeature.UnlimitedCleanup -> UnlimitedCleanup
            ProFeature.VideoCompression -> CompressVideos
            ProFeature.ScreenshotCategories -> SmartScreenshots
            ProFeature.ReceiptFiling -> ReceiptFiling
            ProFeature.AiTaste -> AiTaste
            ProFeature.UnlimitedBackup, ProFeature.UnlimitedRules, ProFeature.MultipleCloudAccounts -> UnlimitedBackupAndRules
        }
    }
}

/** What the primary button says. */
sealed interface PaywallCta {
    data class StartTrial(val days: Int) : PaywallCta
    data class SubscribeYearly(val price: String) : PaywallCta
    data class SubscribeMonthly(val price: String) : PaywallCta
    data class BuyLifetime(val price: String) : PaywallCta
}

/** The disclosure under the button. A subscription always says it renews; a trial says what it costs afterwards. */
sealed interface PaywallFinePrint {
    data class TrialThenYearly(val days: Int, val price: String) : PaywallFinePrint
    data class Yearly(val price: String) : PaywallFinePrint
    data class Monthly(val price: String) : PaywallFinePrint
    data object Lifetime : PaywallFinePrint
}

object PaywallModel {
    /** A "Save N%" chip is only shown when it is at least this much (and computed from the store's prices). */
    const val MINIMUM_SAVING_PERCENT = 20

    /** Yearly is pre-selected; otherwise the first plan available. */
    fun defaultSelection(offers: List<ProOffer>): ProProduct? =
        offers.firstOrNull { it.product == ProProduct.Annual }?.product ?: offers.firstOrNull()?.product

    /** How much cheaper yearly is than twelve months of monthly, in whole percent (truncated); null if unknown or under the minimum. */
    fun savePercent(offers: List<ProOffer>): Int? {
        val yearly = offers.firstOrNull { it.product == ProProduct.Annual }?.priceMicros ?: return null
        val monthly = offers.firstOrNull { it.product == ProProduct.Monthly }?.priceMicros ?: return null
        if (monthly <= 0 || yearly <= 0) return null
        val full = monthly * 12
        if (yearly >= full) return null
        val percent = ((full - yearly) * 100 / full).toInt()
        return percent.takeIf { it >= MINIMUM_SAVING_PERCENT }
    }

    fun cta(offer: ProOffer): PaywallCta = when (offer.product) {
        ProProduct.Annual -> offer.trialDays?.let { PaywallCta.StartTrial(it) } ?: PaywallCta.SubscribeYearly(offer.displayPrice)
        ProProduct.Monthly -> PaywallCta.SubscribeMonthly(offer.displayPrice)
        ProProduct.Lifetime -> PaywallCta.BuyLifetime(offer.displayPrice)
    }

    fun finePrint(offer: ProOffer): PaywallFinePrint = when (offer.product) {
        ProProduct.Annual -> offer.trialDays?.let { PaywallFinePrint.TrialThenYearly(it, offer.displayPrice) } ?: PaywallFinePrint.Yearly(offer.displayPrice)
        ProProduct.Monthly -> PaywallFinePrint.Monthly(offer.displayPrice)
        ProProduct.Lifetime -> PaywallFinePrint.Lifetime
    }
}
