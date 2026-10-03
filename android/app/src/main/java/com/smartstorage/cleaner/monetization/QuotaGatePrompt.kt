package com.smartstorage.cleaner.monetization

// What the quota gate sheet shows when a deletion is over the free allowance (store/PAYWALL_DESIGN.md §3.2).
// Pure, so the numbers and the choice of buttons can be tested; the sheet only draws it.
// Mirrors ios/SmartStorage/Monetization/QuotaGatePrompt.swift.

sealed interface QuotaGateKind {
    /** Part of the selection fits: these ids (safest first) total [bytes]. */
    data class Partial(val ids: List<String>, val bytes: Long) : QuotaGateKind

    /** Allowance is left, but no selected item fits in it. */
    data object NoneFit : QuotaGateKind

    /** This month's free cleanup is used up. */
    data object Exhausted : QuotaGateKind
}

data class QuotaGatePrompt(
    val kind: QuotaGateKind,
    val selectionBytes: Long,
    val remaining: Long,
    val used: Long,
    val limit: Long,
    /** Epoch millis of the next reset. */
    val resetDate: Long,
) {
    /** Ids to delete if the person picks "Delete … (safest first)"; null when that option isn't offered. */
    val partialIds: List<String>? get() = (kind as? QuotaGateKind.Partial)?.ids
    val partialBytes: Long? get() = (kind as? QuotaGateKind.Partial)?.bytes

    companion object {
        /** null when the deletion is allowed and no sheet is needed. */
        fun of(result: CleanupGateResult, selection: List<CleanupItem>, allowances: Allowances): QuotaGatePrompt? {
            val limit = allowances.limits.cleanupBytesPerMonth
            val (kind, remaining) = when (result) {
                CleanupGateResult.Allowed -> return null
                is CleanupGateResult.Partial -> QuotaGateKind.Partial(result.ids, result.bytes) to result.remaining
                is CleanupGateResult.NoneFit -> QuotaGateKind.NoneFit to result.remaining
                CleanupGateResult.Exhausted -> QuotaGateKind.Exhausted to 0L
            }
            return QuotaGatePrompt(
                kind = kind,
                selectionBytes = selection.sumOf { maxOf(0, it.bytes) },
                remaining = remaining,
                used = minOf(limit, allowances.ledger.cleanupBytes),
                limit = limit,
                resetDate = allowances.resetDate,
            )
        }
    }
}
