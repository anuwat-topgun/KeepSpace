package com.smartstorage.cleaner.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One purchasable plan, as the paywall shows it. Prices come from Play (localized, already formatted). */
data class ProOffer(
    val product: ProProduct,
    val displayPrice: String,
    /** The billed amount in micro-units (for comparing plans; never shown directly). */
    val priceMicros: Long,
    /** Yearly only: the price per month, formatted in the store's currency. */
    val perMonthDisplay: String?,
    val trialDays: Int?,
)

enum class PurchaseOutcome { Success, Pending, Cancelled, Failed }

/**
 * The Play Billing layer. It only translates: it reads what Play says the person owns and hands that to
 * [MonetizationStore.applyPurchases], which decides what it means. No server, no account.
 *
 * Play Billing doesn't expose a subscription's end date on the device (that needs the Play Developer API and a
 * server). So every purchase Play lists as owned is reported as valid until a day from now; the list is re-read at every
 * launch and resume, a lapsed or refunded purchase simply stops being listed, and [Entitlement] adds its offline grace.
 */
class ProBillingService(
    context: Context,
    private val monetization: MonetizationStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _offers = MutableStateFlow<List<ProOffer>>(emptyList())
    val offers: StateFlow<List<ProOffer>> = _offers.asStateFlow()
    private val _offersFailed = MutableStateFlow(false)
    val offersFailed: StateFlow<Boolean> = _offersFailed.asStateFlow()

    private var details: Map<ProProduct, ProductDetails> = emptyMap()
    private var pendingOutcome: ((PurchaseOutcome) -> Unit)? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                for (purchase in purchases.orEmpty()) acknowledgeIfNeeded(purchase)
                refreshEntitlements()
                finish(if (purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }) PurchaseOutcome.Pending else PurchaseOutcome.Success)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> finish(PurchaseOutcome.Cancelled)
            // Bought on another device/account state we didn't know about: re-read, which usually unlocks it.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refreshEntitlements(); finish(PurchaseOutcome.Success) }
            else -> finish(PurchaseOutcome.Failed)
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Fixed plans for screenshots and emulators without Play products (`--ez debugPaywall true`, debug builds only). */
    fun useDemoOffers() {
        _offers.value = listOf(
            ProOffer(ProProduct.Annual, "$19.99", 19_990_000, "$1.67", 7),
            ProOffer(ProProduct.Monthly, "$2.99", 2_990_000, null, null),
            ProOffer(ProProduct.Lifetime, "$39.99", 39_990_000, null, null),
        )
        _offersFailed.value = false
    }

    /** Call at launch and again when the app returns to the foreground. */
    fun start() {
        if (client.isReady) {
            scope.launch { refreshEntitlements(); loadOffers() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { refreshEntitlements(); loadOffers() }
                } else {
                    monetization.applyPurchases(null) // store unreachable: keep the cached status for its grace
                    _offersFailed.value = true
                }
            }

            override fun onBillingServiceDisconnected() = Unit // start() reconnects on the next resume
        })
    }

    fun close() {
        if (client.isReady) client.endConnection()
    }

    suspend fun loadOffers() {
        val products = ProProduct.entries.map { it to if (it.isSubscription) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP }
        val loaded = mutableMapOf<ProProduct, ProductDetails>()
        for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            val ids = products.filter { it.second == type }.map { it.first }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(type).build() })
                .build()
            val result = client.queryProductDetails(params)
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) continue
            result.productDetailsList.orEmpty().forEach { d -> ProProduct.fromId(d.productId)?.let { loaded[it] = d } }
        }
        details = loaded
        _offers.value = ProProduct.entries.mapNotNull { product -> loaded[product]?.let { toOffer(product, it) } }
        _offersFailed.value = _offers.value.isEmpty()
    }

    /** Starts the Play purchase sheet. The outcome arrives through [onOutcome]. */
    fun purchase(activity: Activity, product: ProProduct, onOutcome: (PurchaseOutcome) -> Unit) {
        val d = details[product] ?: return onOutcome(PurchaseOutcome.Failed)
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        if (product.isSubscription) {
            // The offer with a free trial if there is one, otherwise the base plan.
            val offer = d.subscriptionOfferDetails.orEmpty().let { offers -> offers.firstOrNull { trialDays(it) != null } ?: offers.firstOrNull() }
            productParams.setOfferToken(offer?.offerToken ?: return onOutcome(PurchaseOutcome.Failed))
        }
        pendingOutcome = onOutcome
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) finish(PurchaseOutcome.Failed)
    }

    /** "Restore Purchases": Play keeps no separate restore, so this just re-reads what the account owns. */
    suspend fun restore(): Boolean = refreshEntitlements()

    /** Re-reads owned purchases and applies them. Returns false (keeping the cached status) when Play couldn't be reached. */
    suspend fun refreshEntitlements(): Boolean {
        if (!client.isReady) {
            monetization.applyPurchases(null)
            return false
        }
        val owned = mutableListOf<Purchase>()
        for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                monetization.applyPurchases(null)
                return false
            }
            owned += result.purchasesList
        }
        owned.forEach { acknowledgeIfNeeded(it) }
        monetization.applyPurchases(toStorePurchases(owned.map { OwnedPurchase(it.products, it.purchaseTime, it.purchaseState == Purchase.PurchaseState.PURCHASED, it.isAutoRenewing) }, clock()))
        return true
    }

    // Play refunds a purchase that isn't acknowledged within 3 days.
    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
    }

    private fun finish(outcome: PurchaseOutcome) {
        pendingOutcome?.invoke(outcome)
        pendingOutcome = null
    }

    private fun toOffer(product: ProProduct, d: ProductDetails): ProOffer {
        if (!product.isSubscription) {
            val once = d.oneTimePurchaseOfferDetails
            return ProOffer(product, once?.formattedPrice.orEmpty(), once?.priceAmountMicros ?: 0, null, null)
        }
        val offers = d.subscriptionOfferDetails.orEmpty()
        val base = offers.firstOrNull { trialDays(it) == null } ?: offers.firstOrNull()
        val phase = base?.pricingPhases?.pricingPhaseList?.lastOrNull() // the recurring phase
        val perMonth = if (product == ProProduct.Annual && phase != null) formatPerMonth(phase.priceAmountMicros, phase.priceCurrencyCode) else null
        return ProOffer(product, phase?.formattedPrice.orEmpty(), phase?.priceAmountMicros ?: 0, perMonth, offers.firstNotNullOfOrNull { trialDays(it) })
    }

    private fun trialDays(offer: ProductDetails.SubscriptionOfferDetails): Int? {
        val free = offer.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L } ?: return null
        return billingPeriodDays(free.billingPeriod)
    }
}

/** A yearly price spread over twelve months, in the store's currency. */
internal fun formatPerMonth(yearlyMicros: Long, currencyCode: String): String? = runCatching {
    java.text.NumberFormat.getCurrencyInstance().apply { currency = java.util.Currency.getInstance(currencyCode) }
        .format(yearlyMicros / 12_000_000.0)
}.getOrNull()

/** What the mapping needs from a Play purchase (so it can be tested without Play's classes). */
data class OwnedPurchase(val productIds: List<String>, val purchaseTime: Long, val isPurchased: Boolean, val isAutoRenewing: Boolean)

/** How long a purchase Play lists as owned is trusted before it must be seen again. */
const val OWNED_TRUST_MS = 24L * 3_600_000

/** Turns owned purchases into the entitlement input. Pending purchases are skipped: they unlock when they clear. */
fun toStorePurchases(owned: List<OwnedPurchase>, now: Long): List<StorePurchase> = owned
    .filter { it.isPurchased }
    .flatMap { purchase ->
        purchase.productIds.mapNotNull { id ->
            val product = ProProduct.fromId(id) ?: return@mapNotNull null
            StorePurchase(
                productId = id,
                purchasedAt = purchase.purchaseTime,
                expiresAt = if (product.isSubscription) now + OWNED_TRUST_MS else null,
                isTrial = false, // Play doesn't say on the device
                isRevoked = false, // a refunded purchase just stops being listed
                willRenew = product.isSubscription && purchase.isAutoRenewing,
            )
        }
    }

/** ISO-8601 billing period ("P7D", "P1W", "P1M", "P1Y") as days; null if unrecognised. */
fun billingPeriodDays(period: String): Int? {
    val match = Regex("""P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?""").matchEntire(period) ?: return null
    val (y, m, w, d) = match.destructured
    val days = (y.toIntOrNull() ?: 0) * 365 + (m.toIntOrNull() ?: 0) * 30 + (w.toIntOrNull() ?: 0) * 7 + (d.toIntOrNull() ?: 0)
    return days.takeIf { it > 0 }
}
