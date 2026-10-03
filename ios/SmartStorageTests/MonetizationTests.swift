import Foundation
import Testing
@testable import SmartStorage

private let bangkok: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Asia/Bangkok")!
    return calendar
}()

private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 12) -> Date {
    bangkok.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
}

private let now = date(2026, 10, 3)
private let day: TimeInterval = 86_400

private func purchase(_ product: ProProduct, expiresIn: TimeInterval? = 30 * day, trial: Bool = false, revoked: Bool = false,
                      renews: Bool = true, id: String? = nil) -> StorePurchase {
    StorePurchase(productID: id ?? product.rawValue, purchasedAt: now.addingTimeInterval(-day),
                  expiresAt: expiresIn.map { now.addingTimeInterval($0) }, isTrial: trial, isRevoked: revoked, willRenew: renews)
}

// MARK: - Entitlement

struct EntitlementTests {
    @Test func noPurchasesIsFree() {
        #expect(Entitlement.resolve(purchases: [], cached: .free, now: now) == .free)
        #expect(!ProStatus.free.isPro)
    }

    @Test func activeSubscriptionIsPro() {
        let status = Entitlement.resolve(purchases: [purchase(.annual, expiresIn: 200 * day)], cached: .free, now: now)
        #expect(status == .pro(product: .annual, expiresAt: now.addingTimeInterval(200 * day), isTrial: false, willRenew: true, isGrace: false))
        #expect(status.isPro && status.allows(.videoCompression))
    }

    @Test func expiredSubscriptionIsFree() {
        #expect(Entitlement.resolve(purchases: [purchase(.monthly, expiresIn: -1)], cached: .free, now: now) == .free)
    }

    @Test func trialIsReportedAndIsPro() {
        let status = Entitlement.resolve(purchases: [purchase(.annual, expiresIn: 5 * day, trial: true)], cached: .free, now: now)
        guard case .pro(_, _, let isTrial, _, _) = status else { Issue.record("expected pro"); return }
        #expect(isTrial)
    }

    @Test func lifetimeBeatsSubscriptionsAndNeverExpires() {
        let status = Entitlement.resolve(purchases: [purchase(.annual, expiresIn: 300 * day), purchase(.lifetime, expiresIn: nil)],
                                         cached: .free, now: now)
        #expect(status == .pro(product: .lifetime, expiresAt: nil, isTrial: false, willRenew: false, isGrace: false))
    }

    @Test func revokedAndUnknownPurchasesAreIgnored() {
        #expect(Entitlement.resolve(purchases: [purchase(.lifetime, expiresIn: nil, revoked: true)], cached: .free, now: now) == .free)
        #expect(Entitlement.resolve(purchases: [purchase(.annual, id: "com.other.app.pro")], cached: .free, now: now) == .free)
        // A subscription with no end date can't be trusted.
        #expect(Entitlement.resolve(purchases: [purchase(.annual, expiresIn: nil)], cached: .free, now: now) == .free)
    }

    @Test func theLongestLastingSubscriptionWins() {
        let status = Entitlement.resolve(purchases: [purchase(.monthly, expiresIn: 10 * day, trial: true), purchase(.annual, expiresIn: 90 * day)],
                                         cached: .free, now: now)
        #expect(status.product == .annual)
    }

    @Test func anEmptyListFromTheStoreIsAuthoritative() {
        // The store answered and says there is nothing (e.g. a refund): cached Pro must not linger.
        let cached = ProStatus.pro(product: .annual, expiresAt: now.addingTimeInterval(day), isTrial: false, willRenew: true, isGrace: false)
        #expect(Entitlement.resolve(purchases: [], cached: cached, now: now) == .free)
    }

    // The store couldn't be reached (purchases == nil): lean on the cache, briefly.

    @Test func offlineKeepsLifetimeAndUnexpiredSubscriptions() {
        let lifetime = ProStatus.pro(product: .lifetime, expiresAt: nil, isTrial: false, willRenew: false, isGrace: false)
        #expect(Entitlement.resolve(purchases: nil, cached: lifetime, now: now.addingTimeInterval(500 * day)) == lifetime)
        let active = ProStatus.pro(product: .annual, expiresAt: now.addingTimeInterval(day), isTrial: false, willRenew: true, isGrace: false)
        #expect(Entitlement.resolve(purchases: nil, cached: active, now: now) == active)
        #expect(Entitlement.resolve(purchases: nil, cached: .free, now: now) == .free)
    }

    @Test func offlineGraceCoversAShortOutageThenEnds() {
        let expiry = now
        let cached = ProStatus.pro(product: .monthly, expiresAt: expiry, isTrial: false, willRenew: true, isGrace: false)
        let inGrace = Entitlement.resolve(purchases: nil, cached: cached, now: expiry.addingTimeInterval(2 * day))
        #expect(inGrace == .pro(product: .monthly, expiresAt: expiry, isTrial: false, willRenew: true, isGrace: true))
        #expect(Entitlement.resolve(purchases: nil, cached: cached, now: expiry.addingTimeInterval(Entitlement.offlineGrace + 1)) == .free)
    }

    @Test func everyProFeatureFollowsTheStatus() {
        let pro = ProStatus.pro(product: .lifetime, expiresAt: nil, isTrial: false, willRenew: false, isGrace: false)
        for feature in ProFeature.allCases {
            #expect(pro.allows(feature))
            #expect(!ProStatus.free.allows(feature))
        }
    }

    @Test func statusSurvivesEncoding() throws {
        let status = ProStatus.pro(product: .annual, expiresAt: now, isTrial: true, willRenew: true, isGrace: false)
        #expect(try JSONDecoder().decode(ProStatus.self, from: JSONEncoder().encode(status)) == status)
        #expect(try JSONDecoder().decode(ProStatus.self, from: JSONEncoder().encode(ProStatus.free)) == .free)
    }
}

// MARK: - Usage ledger

struct UsageLedgerTests {
    @Test func monthKeyUsesTheDeviceCalendar() {
        #expect(UsageLedger.monthKey(for: date(2026, 10, 3), calendar: bangkok) == "2026-10")
        #expect(UsageLedger.monthKey(for: date(2026, 1, 31, 23), calendar: bangkok) == "2026-01")
    }

    @Test func aNewMonthStartsFresh() {
        var ledger = UsageLedger.empty(at: date(2026, 10, 3), calendar: bangkok)
        ledger = ledger.recordingCleanup(400_000_000, at: date(2026, 10, 3), calendar: bangkok)
        #expect(ledger.cleanupBytes == 400_000_000)
        #expect(ledger.rolled(to: date(2026, 10, 31, 23), calendar: bangkok).cleanupBytes == 400_000_000) // same month
        let november = ledger.rolled(to: date(2026, 11, 1, 0), calendar: bangkok)
        #expect(november == UsageLedger(month: "2026-11"))
    }

    @Test func changingTheClockBackDoesNotResetTheAllowance() {
        let ledger = UsageLedger.empty(at: date(2026, 10, 3), calendar: bangkok)
            .recordingCleanup(900_000_000, at: date(2026, 10, 3), calendar: bangkok)
        let rewound = ledger.rolled(to: date(2026, 9, 1), calendar: bangkok)
        #expect(rewound.cleanupBytes == 900_000_000)
        #expect(rewound.month == "2026-10")
    }

    @Test func recordingCountsAndIgnoresNegatives() {
        var ledger = UsageLedger.empty(at: now, calendar: bangkok)
        ledger = ledger.recordingBackups(10, at: now, calendar: bangkok).recordingBackups(-5, at: now, calendar: bangkok)
        ledger = ledger.recordingCleanup(-1, at: now, calendar: bangkok)
        #expect(ledger.backupFiles == 10 && ledger.cleanupBytes == 0)
    }

    @Test func recordingInANewMonthRollsFirst() {
        let old = UsageLedger(month: "2026-09", cleanupBytes: 800_000_000, backupFiles: 90)
        let next = old.recordingCleanup(100_000_000, at: date(2026, 10, 2), calendar: bangkok)
        #expect(next == UsageLedger(month: "2026-10", cleanupBytes: 100_000_000, backupFiles: 0))
    }
}

// MARK: - Allowances and gates

private let free = ProStatus.free
private let pro = ProStatus.pro(product: .annual, expiresAt: nil, isTrial: false, willRenew: true, isGrace: false)

private func allowances(_ status: ProStatus = free, usedBytes: Int64 = 0, usedFiles: Int = 0, at moment: Date = now) -> Allowances {
    Allowances(status: status, ledger: UsageLedger(month: UsageLedger.monthKey(for: moment, calendar: bangkok), cleanupBytes: usedBytes, backupFiles: usedFiles),
               now: moment, calendar: bangkok)
}

private func item(_ id: String, _ megabytes: Int64, _ safety: SafetyLevel) -> CleanupItem {
    CleanupItem(id: id, bytes: megabytes * 1_000_000, safety: safety)
}

struct AllowanceTests {
    @Test func freeHasLimitsAndProHasNone() {
        #expect(allowances(usedBytes: 300_000_000).cleanupRemaining == 700_000_000)
        #expect(allowances(usedFiles: 40).backupRemaining == 60)
        #expect(allowances(usedBytes: 5_000_000_000).cleanupRemaining == 0) // never negative
        #expect(allowances(pro, usedBytes: 5_000_000_000).cleanupRemaining == nil)
        #expect(allowances(pro).backupRemaining == nil)
    }

    @Test func allowancesResetOnTheFirstOfNextMonth() {
        let a = allowances(at: date(2026, 10, 3))
        #expect(a.resetDate == bangkok.date(from: DateComponents(year: 2026, month: 11, day: 1, hour: 0)))
        #expect(allowances(at: date(2026, 12, 31, 23)).resetDate == bangkok.date(from: DateComponents(year: 2027, month: 1, day: 1, hour: 0)))
    }

    @Test func aStaleLedgerIsRolledBeforeUse() {
        let stale = Allowances(status: free, ledger: UsageLedger(month: "2026-09", cleanupBytes: 1_000_000_000, backupFiles: 100),
                               now: now, calendar: bangkok)
        #expect(stale.cleanupRemaining == 1_000_000_000 && stale.backupRemaining == 100)
    }

    @Test func rulesAndCloudAccountsAreCappedOnFree() {
        #expect(allowances().canCreateRule(existing: 0))
        #expect(!allowances().canCreateRule(existing: 1))
        #expect(allowances(pro).canCreateRule(existing: 25))
        #expect(allowances().canConnectCloudAccount(existing: 0))
        #expect(!allowances().canConnectCloudAccount(existing: 1))
        #expect(allowances(pro).canConnectCloudAccount(existing: 2))
    }

    @Test func backupIsTrimmedToWhatIsLeft() {
        #expect(allowances(usedFiles: 90).backupAllowedCount(requested: 25) == 10)
        #expect(allowances(usedFiles: 100).backupAllowedCount(requested: 25) == 0)
        #expect(allowances(usedFiles: 10).backupAllowedCount(requested: 25) == 25)
        #expect(allowances(pro, usedFiles: 999).backupAllowedCount(requested: 500) == 500)
    }
}

struct CleanupGateTests {
    @Test func proAndSmallSelectionsAreAllowed() {
        #expect(allowances(pro).gate(cleanup: [item("a", 5_000, .safe)]) == .allowed)
        #expect(allowances(usedBytes: 100_000_000).gate(cleanup: [item("a", 400, .safe)]) == .allowed)
        // Exactly what is left still fits.
        #expect(allowances(usedBytes: 400_000_000).gate(cleanup: [item("a", 600, .safe)]) == .allowed)
        #expect(allowances().gate(cleanup: []) == .allowed)
    }

    @Test func anExhaustedAllowanceBlocksEverything() {
        #expect(allowances(usedBytes: 1_000_000_000).gate(cleanup: [item("a", 1, .verySafe)]) == .exhausted)
        // …but selecting nothing is never blocked.
        #expect(allowances(usedBytes: 1_000_000_000).gate(cleanup: []) == .allowed)
    }

    @Test func aTooBigSelectionKeepsTheSafestItemsThatFit() {
        // 1 GB left. a (review first) is dropped because b and c, which are safer, already use it all.
        let selection = [item("a", 300, .reviewFirst), item("b", 600, .verySafe), item("c", 400, .safe)]
        #expect(allowances().gate(cleanup: selection) == .partial(ids: ["b", "c"], bytes: 1_000_000_000, remaining: 1_000_000_000))
    }

    @Test func itemsThatDoNotFitAreSkippedAndSmallerOnesStillTried() {
        // 500 MB left: x (600) can't fit, so y and z are taken.
        let selection = [item("x", 600, .verySafe), item("y", 300, .safe), item("z", 200, .reviewFirst)]
        let result = allowances(usedBytes: 500_000_000).gate(cleanup: selection)
        #expect(result == .partial(ids: ["y", "z"], bytes: 500_000_000, remaining: 500_000_000))
    }

    @Test func equalSafetyKeepsTheSelectionOrder() {
        let selection = [item("first", 300, .safe), item("second", 300, .safe), item("third", 300, .safe), item("fourth", 300, .safe)]
        guard case .partial(let ids, _, _) = allowances().gate(cleanup: selection) else { Issue.record("expected partial"); return }
        #expect(ids == ["first", "second", "third"])
    }

    @Test func whenNothingFitsItSaysSo() {
        #expect(allowances(usedBytes: 900_000_000).gate(cleanup: [item("big", 500, .verySafe)]) == .noneFit(remaining: 100_000_000))
    }

    @Test func aPartialResultNeverExceedsTheAllowance() {
        let selection = (0..<40).map { item("i\($0)", Int64(30 + $0 * 7), SafetyLevel.allSafetyLevels[$0 % 3]) }
        for used in stride(from: Int64(0), to: 1_000_000_000, by: 83_000_000) {
            let a = allowances(usedBytes: used)
            if case .partial(let ids, let bytes, let remaining) = a.gate(cleanup: selection) {
                let sum = selection.filter { ids.contains($0.id) }.reduce(Int64(0)) { $0 + $1.bytes }
                #expect(sum == bytes && bytes <= remaining)
            }
        }
    }
}

private extension SafetyLevel {
    static let allSafetyLevels: [SafetyLevel] = [.verySafe, .safe, .reviewFirst]
}

// MARK: - Store

private final class TestClock: @unchecked Sendable {
    var now: Date
    init(_ now: Date) { self.now = now }
}

@MainActor
struct MonetizationStoreTests {
    private func makeStore(_ defaults: UserDefaults, clock: TestClock) -> MonetizationStore {
        MonetizationStore(defaults: defaults, calendar: bangkok, clock: { clock.now })
    }

    private func freshDefaults() throws -> UserDefaults {
        try #require(UserDefaults(suiteName: "monetization-tests-\(UUID().uuidString)"))
    }

    @Test func usageIsCountedAndPersisted() throws {
        let defaults = try freshDefaults()
        let clock = TestClock(now)
        let store = makeStore(defaults, clock: clock)
        store.recordCleanup(bytes: 250_000_000)
        store.recordBackups(files: 12)
        #expect(store.allowances.cleanupRemaining == 750_000_000)

        let reopened = makeStore(defaults, clock: clock)
        #expect(reopened.allowances.cleanupRemaining == 750_000_000 && reopened.allowances.backupRemaining == 88)
    }

    @Test func aNewMonthResetsTheStoredUsage() throws {
        let defaults = try freshDefaults()
        let clock = TestClock(now)
        makeStore(defaults, clock: clock).recordCleanup(bytes: 900_000_000)
        clock.now = date(2026, 11, 2)
        #expect(makeStore(defaults, clock: clock).allowances.cleanupRemaining == 1_000_000_000)
    }

    @Test func proIsPersistedAndStopsCounting() throws {
        let defaults = try freshDefaults()
        let clock = TestClock(now)
        let store = makeStore(defaults, clock: clock)
        store.applyPurchases([purchase(.annual, expiresIn: 100 * day)])
        #expect(store.isPro)
        store.recordCleanup(bytes: 5_000_000_000)
        #expect(store.ledger.cleanupBytes == 0)
        #expect(makeStore(defaults, clock: clock).isPro) // survives a relaunch (offline start)
    }

    @Test func aRefundTurnsProOffAndTheStoreBeingUnreachableDoesNot() throws {
        let defaults = try freshDefaults()
        let clock = TestClock(now)
        let store = makeStore(defaults, clock: clock)
        store.applyPurchases([purchase(.annual, expiresIn: 100 * day)])
        store.applyPurchases(nil)
        #expect(store.isPro)
        store.applyPurchases([purchase(.annual, expiresIn: 100 * day, revoked: true)])
        #expect(!store.isPro)
        #expect(!makeStore(defaults, clock: clock).isPro)
    }

    @Test func freeIsTheDefaultAndRejectsGarbageInStorage() throws {
        let defaults = try freshDefaults()
        defaults.set(Data("not json".utf8), forKey: "monetization.status.v1")
        defaults.set(Data("nope".utf8), forKey: "monetization.usage.v1")
        let store = makeStore(defaults, clock: TestClock(now))
        #expect(!store.isPro)
        #expect(store.allowances.cleanupRemaining == 1_000_000_000)
    }
}
