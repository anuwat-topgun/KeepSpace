import Foundation

/// What the quota gate sheet shows when a deletion is over the free allowance (store/PAYWALL_DESIGN.md §3.2).
/// Pure, so the numbers and the choice of buttons can be tested; the sheet only draws it. Mirrors QuotaGatePrompt.kt.
struct QuotaGatePrompt: Identifiable, Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        /// Part of the selection fits: these ids (safest first) total `bytes`.
        case partial(ids: [String], bytes: Int64)
        /// Allowance is left, but no selected item fits in it.
        case noneFit
        /// This month's free cleanup is used up.
        case exhausted
    }

    let id = UUID()
    let kind: Kind
    let selectionBytes: Int64
    let remaining: Int64
    let used: Int64
    let limit: Int64
    let resetDate: Date

    /// nil when the deletion is allowed and no sheet is needed.
    init?(_ result: CleanupGateResult, selection: [CleanupItem], allowances: Allowances) {
        let limit = allowances.limits.cleanupBytesPerMonth
        switch result {
        case .allowed: return nil
        case .partial(let ids, let bytes, let remaining): kind = .partial(ids: ids, bytes: bytes); self.remaining = remaining
        case .noneFit(let remaining): kind = .noneFit; self.remaining = remaining
        case .exhausted: kind = .exhausted; remaining = 0
        }
        selectionBytes = selection.reduce(0) { $0 + max(0, $1.bytes) }
        self.limit = limit
        used = min(limit, allowances.ledger.cleanupBytes)
        resetDate = allowances.resetDate
    }

    /// Ids to delete if the person picks "Delete … (safest first)"; nil when that option isn't offered.
    var partialIDs: [String]? {
        if case .partial(let ids, _) = kind { return ids }
        return nil
    }

    var partialBytes: Int64? {
        if case .partial(_, let bytes) = kind { return bytes }
        return nil
    }
}
