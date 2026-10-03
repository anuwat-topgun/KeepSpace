package com.smartstorage.cleaner.monetization

import com.smartstorage.cleaner.media.SafetyLevel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// What Free allows, how much has been used this month, and the gates built on both. Pure logic.
// Mirrors ios/SmartStorage/Monetization/UsageLimits.swift. Design: store/PAYWALL_DESIGN.md §2–3.

/** The Free allowances (launch defaults — tune from real conversion data). Pro has none of these limits. */
data class UsageLimits(
    /** Bytes the user may delete per calendar month on Free (decimal GB, like the rest of the app). */
    val cleanupBytesPerMonth: Long = 1_000_000_000L,
    val backupFilesPerMonth: Int = 100,
    val maxRules: Int = 1,
    val maxCloudAccounts: Int = 1,
) {
    companion object {
        val Free = UsageLimits()
    }
}

/** What has been used in one calendar month. Only counters — never file names or ids. */
data class UsageLedger(
    /** "2026-10", from the device time zone. */
    val month: String,
    val cleanupBytes: Long = 0,
    val backupFiles: Int = 0,
) {
    /**
     * Starts a fresh month when the calendar has moved on. If the device clock was set *back* into an earlier month,
     * the counters are kept, so changing the date can't be used to reset the allowance.
     */
    fun rolled(now: Long, zone: ZoneId = ZoneId.systemDefault()): UsageLedger {
        val key = monthKey(now, zone)
        return if (key > month) UsageLedger(key) else this
    }

    fun recordingCleanup(bytes: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): UsageLedger =
        rolled(now, zone).let { it.copy(cleanupBytes = it.cleanupBytes + maxOf(0, bytes)) }

    fun recordingBackups(files: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): UsageLedger =
        rolled(now, zone).let { it.copy(backupFiles = it.backupFiles + maxOf(0, files)) }

    companion object {
        fun monthKey(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
            val month = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
            return "%04d-%02d".format(month.year, month.monthValue)
        }

        fun empty(now: Long, zone: ZoneId = ZoneId.systemDefault()) = UsageLedger(monthKey(now, zone))
    }
}

/** One candidate for deletion, as the gate sees it. */
data class CleanupItem(val id: String, val bytes: Long, val safety: SafetyLevel)

sealed interface CleanupGateResult {
    /** Go ahead (Pro, or the selection fits the allowance). */
    data object Allowed : CleanupGateResult

    /** Too big, but part of it fits: these items (safest first) are within what's left. */
    data class Partial(val ids: List<String>, val bytes: Long, val remaining: Long) : CleanupGateResult

    /** Nothing may be deleted on Free this month. */
    data object Exhausted : CleanupGateResult

    /** There is allowance left, but no single selected item fits in it. */
    data class NoneFit(val remaining: Long) : CleanupGateResult
}

/** Everything the UI asks about limits, for one moment in time. */
class Allowances(
    val status: ProStatus,
    ledger: UsageLedger,
    val now: Long,
    val limits: UsageLimits = UsageLimits.Free,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val ledger: UsageLedger = ledger.rolled(now, zone)

    val isPro: Boolean get() = status.isPro

    /** Bytes still deletable this month; null = unlimited (Pro). */
    val cleanupRemaining: Long? get() = if (isPro) null else maxOf(0, limits.cleanupBytesPerMonth - ledger.cleanupBytes)

    /** Files still backable this month; null = unlimited (Pro). */
    val backupRemaining: Int? get() = if (isPro) null else maxOf(0, limits.backupFilesPerMonth - ledger.backupFiles)

    /** When the allowances reset: midnight on the 1st of next month. */
    val resetDate: Long
        get() = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone)).plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun allows(feature: ProFeature): Boolean = status.allows(feature)

    fun canCreateRule(existing: Int): Boolean = isPro || existing < limits.maxRules

    fun canConnectCloudAccount(existing: Int): Boolean = isPro || existing < limits.maxCloudAccounts

    /** How many of [requested] files may be backed up now. */
    fun backupAllowedCount(requested: Int): Int {
        val remaining = backupRemaining ?: return requested
        return maxOf(0, minOf(requested, remaining))
    }

    /**
     * Decides whether a deletion may go ahead. When the selection is too big, the partial result keeps the safest items
     * first (very safe → safe → review first), skipping any that no longer fit.
     */
    fun gate(cleanup: List<CleanupItem>): CleanupGateResult {
        val remaining = cleanupRemaining ?: return CleanupGateResult.Allowed
        val total = cleanup.sumOf { maxOf(0, it.bytes) }
        if (total <= remaining) return CleanupGateResult.Allowed
        if (remaining <= 0) return CleanupGateResult.Exhausted

        // sortedByDescending is stable: items of equal safety keep the order they were selected in.
        val ids = mutableListOf<String>()
        var used = 0L
        for (item in cleanup.sortedByDescending { it.safety.score }) {
            val bytes = maxOf(0, item.bytes)
            if (used + bytes <= remaining) {
                ids += item.id
                used += bytes
            }
        }
        return if (ids.isEmpty()) CleanupGateResult.NoneFit(remaining) else CleanupGateResult.Partial(ids, used, remaining)
    }
}
