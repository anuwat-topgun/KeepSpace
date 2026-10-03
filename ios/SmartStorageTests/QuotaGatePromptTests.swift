import Foundation
import Testing
@testable import SmartStorage

private let cal: Calendar = { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Bangkok")!; return c }()
private let now = cal.date(from: DateComponents(year: 2026, month: 10, day: 3, hour: 12))!

private func allowances(_ status: ProStatus = .free, used: Int64 = 0) -> Allowances {
    Allowances(status: status, ledger: UsageLedger(month: "2026-10", cleanupBytes: used), now: now, calendar: cal)
}
private func item(_ id: String, _ mb: Int64, _ safety: SafetyLevel = .safe) -> CleanupItem { CleanupItem(id: id, bytes: mb * 1_000_000, safety: safety) }

private func prompt(_ a: Allowances, _ selection: [CleanupItem]) -> QuotaGatePrompt? {
    QuotaGatePrompt(a.gate(cleanup: selection), selection: selection, allowances: a)
}

struct QuotaGatePromptTests {
    @Test func noSheetWhenTheDeletionFits() {
        #expect(prompt(allowances(), [item("a", 400)]) == nil)
        #expect(prompt(allowances(.pro(product: .lifetime, expiresAt: nil, isTrial: false, willRenew: false, isGrace: false)), [item("a", 50_000)]) == nil)
    }

    @Test func partialOffersTheSafestItemsThatFit() throws {
        let p = try #require(prompt(allowances(used: 500_000_000), [item("a", 400, .reviewFirst), item("b", 300, .verySafe), item("c", 400, .safe)]))
        // 500 MB left: b (300, very safe) fits; c and a (400 each) no longer do.
        #expect(p.partialIDs == ["b"] && p.partialBytes == 300_000_000)
        #expect(p.selectionBytes == 1_100_000_000)
        #expect(p.remaining == 500_000_000)
        #expect(p.used == 500_000_000 && p.limit == 1_000_000_000)
    }

    @Test func exhaustedAndNoneFitOfferNoPartialDelete() throws {
        let exhausted = try #require(prompt(allowances(used: 1_000_000_000), [item("a", 10)]))
        #expect(exhausted.kind == .exhausted && exhausted.partialIDs == nil && exhausted.remaining == 0)
        let none = try #require(prompt(allowances(used: 900_000_000), [item("big", 500)]))
        #expect(none.kind == .noneFit && none.partialIDs == nil && none.remaining == 100_000_000)
    }

    @Test func usedIsNeverShownAboveTheLimitAndResetIsNextMonth() throws {
        let p = try #require(prompt(allowances(used: 5_000_000_000), [item("a", 10)]))
        #expect(p.used == p.limit)
        #expect(p.resetDate == cal.date(from: DateComponents(year: 2026, month: 11, day: 1)))
    }
}
