import Foundation
import Observation

/// Holds the current Pro status and this month's usage, and persists both on the device.
///
/// Nothing here talks to StoreKit: the store layer calls `applyPurchases(_:)` with what it found (or nil when it
/// couldn't reach the store), and the gates call `recordCleanup` / `recordBackups` after an action really happened.
/// Only a status and a few counters are stored — never file names or ids. The status is a cache for offline use;
/// the store is re-checked at every launch, so editing it on the device gains nothing lasting.
@MainActor
@Observable
final class MonetizationStore {
    private(set) var status: ProStatus
    private(set) var ledger: UsageLedger
    let limits: UsageLimits

    private let defaults: UserDefaults
    private let calendar: Calendar
    private let clock: @Sendable () -> Date

    private enum Key {
        static let status = "monetization.status.v1"
        static let ledger = "monetization.usage.v1"
    }

    init(limits: UsageLimits = .free, defaults: UserDefaults = .standard, calendar: Calendar = .current,
         clock: @escaping @Sendable () -> Date = { Date() }) {
        self.limits = limits
        self.defaults = defaults
        self.calendar = calendar
        self.clock = clock
        let now = clock()
        status = defaults.data(forKey: Key.status).flatMap { try? JSONDecoder().decode(ProStatus.self, from: $0) } ?? .free
        ledger = (defaults.data(forKey: Key.ledger).flatMap { try? JSONDecoder().decode(UsageLedger.self, from: $0) }
                  ?? .empty(at: now, calendar: calendar)).rolled(to: now, calendar: calendar)
    }

    /// The limits as of right now (re-evaluated each call, so it stays right across midnight on the 1st).
    var allowances: Allowances {
        Allowances(status: status, limits: limits, ledger: ledger, now: clock(), calendar: calendar)
    }

    var isPro: Bool { status.isPro }

    /// Applies what the store reported. `nil` = the store couldn't be reached (keeps the cached status for a short grace).
    func applyPurchases(_ purchases: [StorePurchase]?) {
        let next = Entitlement.resolve(purchases: purchases, cached: status, now: clock())
        guard next != status else { return }
        status = next
        save(next, key: Key.status)
    }

    /// Call after a deletion the person confirmed (a cancelled system dialog uses nothing).
    func recordCleanup(bytes: Int64) {
        guard !isPro else { return } // Pro is unlimited; nothing to count
        ledger = ledger.recordingCleanup(bytes, at: clock(), calendar: calendar)
        save(ledger, key: Key.ledger)
        #if DEBUG
        print("[KeepSpace] recordCleanup +\(bytes) B → month \(ledger.month) used \(ledger.cleanupBytes) B of \(limits.cleanupBytesPerMonth) B")
        #endif
    }

    /// Call when files were actually queued for backup.
    func recordBackups(files: Int) {
        guard !isPro else { return }
        ledger = ledger.recordingBackups(files, at: clock(), calendar: calendar)
        save(ledger, key: Key.ledger)
    }

    private func save<T: Encodable>(_ value: T, key: String) {
        if let data = try? JSONEncoder().encode(value) { defaults.set(data, forKey: key) }
    }
}
