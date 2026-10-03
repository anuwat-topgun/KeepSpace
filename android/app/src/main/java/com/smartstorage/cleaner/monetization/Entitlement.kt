package com.smartstorage.cleaner.monetization

// Free / Pro entitlement. Pure logic, no Play Billing: the billing layer turns its purchases into StorePurchase
// values and this decides what they mean. Mirrors ios/SmartStorage/Monetization/Entitlement.swift.
// Design: store/PAYWALL_DESIGN.md.

/** A Pro product. The ids are the products created in App Store Connect / Play Console. */
enum class ProProduct(val id: String) {
    Annual("com.keepspace.app.pro.annual"),
    Monthly("com.keepspace.app.pro.monthly"),
    Lifetime("com.keepspace.app.pro.lifetime");

    val isSubscription: Boolean get() = this != Lifetime

    companion object {
        fun fromId(id: String): ProProduct? = entries.firstOrNull { it.id == id }
    }
}

/** What the store reports about one purchase. Times are epoch millis. */
data class StorePurchase(
    val productId: String,
    val purchasedAt: Long,
    /** End of the paid (or trial) period. Null for a lifetime purchase. */
    val expiresAt: Long?,
    val isTrial: Boolean,
    /** Refunded or revoked by the store. */
    val isRevoked: Boolean,
    val willRenew: Boolean,
)

/** The result: free, or Pro and how. */
sealed interface ProStatus {
    data object Free : ProStatus

    /**
     * [expiresAt] is null for lifetime. [isGrace] means the store couldn't be reached and the status is being
     * carried over from the last successful check.
     */
    data class Pro(
        val product: ProProduct,
        val expiresAt: Long?,
        val isTrial: Boolean,
        val willRenew: Boolean,
        val isGrace: Boolean,
    ) : ProStatus

    val isPro: Boolean get() = this is Pro

    fun allows(@Suppress("UNUSED_PARAMETER") feature: ProFeature): Boolean = isPro
}

/** Everything that is Pro-only. Safety, viewing scan results and Best Shot are never in this list. */
enum class ProFeature {
    UnlimitedCleanup, VideoCompression, ScreenshotCategories, ReceiptFiling, AiTaste, UnlimitedBackup, UnlimitedRules, MultipleCloudAccounts,
}

object Entitlement {
    /** How long a Pro status survives without reaching the store (offline, store outage). */
    const val OFFLINE_GRACE_MS = 3L * 86_400_000

    /**
     * Decides the status.
     * [purchases] is the store's current purchases, or null when the store could not be reached. A list (even an empty
     * one) is authoritative; null falls back to [cached] with a short grace.
     */
    fun resolve(purchases: List<StorePurchase>?, cached: ProStatus, now: Long): ProStatus {
        if (purchases == null) return carryOver(cached, now)

        val valid = purchases.mapNotNull { purchase ->
            val product = ProProduct.fromId(purchase.productId) ?: return@mapNotNull null
            if (purchase.isRevoked) return@mapNotNull null
            if (product != ProProduct.Lifetime) {
                val expiry = purchase.expiresAt ?: return@mapNotNull null
                if (expiry <= now) return@mapNotNull null
            }
            product to purchase
        }
        // Lifetime beats any subscription; otherwise the one that lasts longest.
        if (valid.any { it.first == ProProduct.Lifetime }) {
            return ProStatus.Pro(ProProduct.Lifetime, expiresAt = null, isTrial = false, willRenew = false, isGrace = false)
        }
        val best = valid.maxByOrNull { it.second.expiresAt ?: Long.MIN_VALUE } ?: return ProStatus.Free
        return ProStatus.Pro(best.first, best.second.expiresAt, best.second.isTrial, best.second.willRenew, isGrace = false)
    }

    private fun carryOver(cached: ProStatus, now: Long): ProStatus {
        if (cached !is ProStatus.Pro) return ProStatus.Free
        val expiresAt = cached.expiresAt ?: return cached // lifetime
        return when {
            now < expiresAt -> cached
            now < expiresAt + OFFLINE_GRACE_MS -> cached.copy(isGrace = true)
            else -> ProStatus.Free
        }
    }
}
