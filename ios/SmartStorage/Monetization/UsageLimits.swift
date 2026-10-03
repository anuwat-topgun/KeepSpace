import Foundation

// What Free allows, how much has been used this month, and the gates built on both. Pure logic.
// Mirrors UsageLimits.kt. Design: store/PAYWALL_DESIGN.md §2–3.

/// The Free allowances (launch defaults — tune from real conversion data). Pro has none of these limits.
struct UsageLimits: Equatable, Sendable {
    /// Bytes the user may delete per calendar month on Free (decimal GB, like the rest of the app).
    var cleanupBytesPerMonth: Int64 = 1_000_000_000
    var backupFilesPerMonth = 100
    var maxRules = 1
    var maxCloudAccounts = 1

    static let free = UsageLimits()
}

/// What has been used in one calendar month. Only counters — never file names or ids.
struct UsageLedger: Equatable, Codable, Sendable {
    /// "2026-10", from the device calendar.
    var month: String
    var cleanupBytes: Int64 = 0
    var backupFiles = 0

    static func monthKey(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month], from: date)
        return String(format: "%04d-%02d", parts.year ?? 0, parts.month ?? 0)
    }

    static func empty(at date: Date, calendar: Calendar = .current) -> UsageLedger {
        UsageLedger(month: monthKey(for: date, calendar: calendar))
    }

    /// Starts a fresh month when the calendar has moved on. If the device clock was set *back* into an earlier month,
    /// the counters are kept, so changing the date can't be used to reset the allowance.
    func rolled(to date: Date, calendar: Calendar = .current) -> UsageLedger {
        let key = Self.monthKey(for: date, calendar: calendar)
        return key > month ? UsageLedger(month: key) : self
    }

    func recordingCleanup(_ bytes: Int64, at date: Date, calendar: Calendar = .current) -> UsageLedger {
        var next = rolled(to: date, calendar: calendar)
        next.cleanupBytes += max(0, bytes)
        return next
    }

    func recordingBackups(_ files: Int, at date: Date, calendar: Calendar = .current) -> UsageLedger {
        var next = rolled(to: date, calendar: calendar)
        next.backupFiles += max(0, files)
        return next
    }
}

/// One candidate for deletion, as the gate sees it.
struct CleanupItem: Equatable, Sendable {
    let id: String
    let bytes: Int64
    let safety: SafetyLevel
}

enum CleanupGateResult: Equatable, Sendable {
    /// Go ahead (Pro, or the selection fits the allowance).
    case allowed
    /// Too big, but part of it fits: these items (safest first) are within what's left.
    case partial(ids: [String], bytes: Int64, remaining: Int64)
    /// Nothing may be deleted on Free this month.
    case exhausted
    /// There is allowance left, but no single selected item fits in it.
    case noneFit(remaining: Int64)
}

/// Everything the UI asks about limits, for one moment in time.
struct Allowances: Sendable {
    let status: ProStatus
    let limits: UsageLimits
    let ledger: UsageLedger
    let now: Date
    let calendar: Calendar

    init(status: ProStatus, limits: UsageLimits = .free, ledger: UsageLedger, now: Date, calendar: Calendar = .current) {
        self.status = status
        self.limits = limits
        self.ledger = ledger.rolled(to: now, calendar: calendar)
        self.now = now
        self.calendar = calendar
    }

    var isPro: Bool { status.isPro }

    /// Bytes still deletable this month; nil = unlimited (Pro).
    var cleanupRemaining: Int64? {
        isPro ? nil : max(0, limits.cleanupBytesPerMonth - ledger.cleanupBytes)
    }

    /// Files still backable this month; nil = unlimited (Pro).
    var backupRemaining: Int? {
        isPro ? nil : max(0, limits.backupFilesPerMonth - ledger.backupFiles)
    }

    /// When the allowances reset: midnight on the 1st of next month.
    var resetDate: Date {
        let start = calendar.date(from: calendar.dateComponents([.year, .month], from: now)) ?? now
        return calendar.date(byAdding: .month, value: 1, to: start) ?? now
    }

    func allows(_ feature: ProFeature) -> Bool { status.allows(feature) }

    func canCreateRule(existing: Int) -> Bool { isPro || existing < limits.maxRules }

    func canConnectCloudAccount(existing: Int) -> Bool { isPro || existing < limits.maxCloudAccounts }

    /// How many of `requested` files may be backed up now.
    func backupAllowedCount(requested: Int) -> Int {
        guard let remaining = backupRemaining else { return requested }
        return max(0, min(requested, remaining))
    }

    /// Decides whether a deletion may go ahead. When the selection is too big, the partial result keeps the
    /// safest items first (very safe → safe → review first), skipping any that no longer fit.
    func gate(cleanup selection: [CleanupItem]) -> CleanupGateResult {
        guard let remaining = cleanupRemaining else { return .allowed }
        let total = selection.reduce(Int64(0)) { $0 + max(0, $1.bytes) }
        if total <= remaining { return .allowed }
        if remaining <= 0 { return .exhausted }

        // Stable: items of equal safety keep the order they were selected in.
        let ordered = selection.enumerated()
            .sorted { $0.element.safety != $1.element.safety ? $0.element.safety > $1.element.safety : $0.offset < $1.offset }
            .map(\.element)
        var ids: [String] = []
        var used: Int64 = 0
        for item in ordered where used + max(0, item.bytes) <= remaining {
            ids.append(item.id)
            used += max(0, item.bytes)
        }
        return ids.isEmpty ? .noneFit(remaining: remaining) : .partial(ids: ids, bytes: used, remaining: remaining)
    }
}
