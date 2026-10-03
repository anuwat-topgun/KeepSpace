package com.smartstorage.cleaner.monetization

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.ZoneId

/** Where the store keeps its two small records. SharedPreferences in the app, a map in tests. */
interface MonetizationStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

class PrefsMonetizationStorage(private val prefs: SharedPreferences) : MonetizationStorage {
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String) = prefs.edit().putString(key, value).apply()
}

/**
 * Holds the current Pro status and this month's usage, and persists both on the device.
 * Mirrors ios/SmartStorage/Monetization/MonetizationStore.swift.
 *
 * Nothing here talks to Play Billing: the billing layer calls [applyPurchases] with what it found (or null when it
 * couldn't reach the store), and the gates call [recordCleanup] / [recordBackups] after an action really happened.
 * Only a status and a few counters are stored — never file names or ids.
 */
class MonetizationStore(
    private val storage: MonetizationStorage,
    val limits: UsageLimits = UsageLimits.Free,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _status = MutableStateFlow(decodeStatus(storage.read(KEY_STATUS)))
    private val _ledger = MutableStateFlow(
        (decodeLedger(storage.read(KEY_LEDGER)) ?: UsageLedger.empty(clock(), zone)).rolled(clock(), zone),
    )

    val status: StateFlow<ProStatus> = _status.asStateFlow()
    val ledger: StateFlow<UsageLedger> = _ledger.asStateFlow()

    val isPro: Boolean get() = _status.value.isPro

    /** The limits as of right now (re-evaluated each call, so it stays right across midnight on the 1st). */
    fun allowances(): Allowances = Allowances(_status.value, _ledger.value, clock(), limits, zone)

    /** Applies what the store reported. null = the store couldn't be reached (keeps the cached status for a short grace). */
    fun applyPurchases(purchases: List<StorePurchase>?) {
        val next = Entitlement.resolve(purchases, _status.value, clock())
        if (next == _status.value) return
        _status.value = next
        storage.write(KEY_STATUS, encode(next))
    }

    /** Call after a deletion the person confirmed (a cancelled system dialog uses nothing). */
    fun recordCleanup(bytes: Long) {
        if (isPro) return // Pro is unlimited; nothing to count
        _ledger.value = _ledger.value.recordingCleanup(bytes, clock(), zone)
        storage.write(KEY_LEDGER, encode(_ledger.value))
    }

    /** Call when files were actually queued for backup. */
    fun recordBackups(files: Int) {
        if (isPro) return
        _ledger.value = _ledger.value.recordingBackups(files, clock(), zone)
        storage.write(KEY_LEDGER, encode(_ledger.value))
    }

    companion object {
        const val KEY_STATUS = "monetization.status.v1"
        const val KEY_LEDGER = "monetization.usage.v1"

        private fun encode(status: ProStatus): String = when (status) {
            ProStatus.Free -> JSONObject().put("kind", "free")
            is ProStatus.Pro -> JSONObject()
                .put("kind", "pro")
                .put("product", status.product.id)
                .put("expiresAt", status.expiresAt ?: JSONObject.NULL)
                .put("isTrial", status.isTrial)
                .put("willRenew", status.willRenew)
                .put("isGrace", status.isGrace)
        }.toString()

        // Garbage in storage means free, never a crash and never Pro.
        internal fun decodeStatus(raw: String?): ProStatus = runCatching {
            val json = JSONObject(raw ?: return ProStatus.Free)
            if (json.getString("kind") != "pro") return ProStatus.Free
            ProStatus.Pro(
                product = ProProduct.fromId(json.getString("product")) ?: return ProStatus.Free,
                expiresAt = if (json.isNull("expiresAt")) null else json.getLong("expiresAt"),
                isTrial = json.getBoolean("isTrial"),
                willRenew = json.getBoolean("willRenew"),
                isGrace = json.getBoolean("isGrace"),
            )
        }.getOrDefault(ProStatus.Free)

        private fun encode(ledger: UsageLedger): String = JSONObject()
            .put("month", ledger.month)
            .put("cleanupBytes", ledger.cleanupBytes)
            .put("backupFiles", ledger.backupFiles)
            .toString()

        internal fun decodeLedger(raw: String?): UsageLedger? = runCatching {
            val json = JSONObject(raw ?: return null)
            UsageLedger(json.getString("month"), json.getLong("cleanupBytes"), json.getInt("backupFiles"))
        }.getOrNull()
    }
}
