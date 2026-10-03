package com.smartstorage.cleaner.monetization

import com.smartstorage.cleaner.media.SafetyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

// Mirrors ios/SmartStorageTests/MonetizationTests.swift.

private val bangkok = ZoneId.of("Asia/Bangkok")
private const val DAY = 86_400_000L

private fun date(year: Int, month: Int, day: Int, hour: Int = 12): Long =
    LocalDateTime.of(year, month, day, hour, 0).atZone(bangkok).toInstant().toEpochMilli()

private val now = date(2026, 10, 3)

private fun purchase(
    product: ProProduct,
    expiresIn: Long? = 30 * DAY,
    trial: Boolean = false,
    revoked: Boolean = false,
    renews: Boolean = true,
    id: String? = null,
) = StorePurchase(id ?: product.id, now - DAY, expiresIn?.let { now + it }, trial, revoked, renews)

private fun resolve(purchases: List<StorePurchase>?, cached: ProStatus = ProStatus.Free, at: Long = now) =
    Entitlement.resolve(purchases, cached, at)

class EntitlementTest {
    @Test fun noPurchasesIsFree() {
        assertEquals(ProStatus.Free, resolve(emptyList()))
        assertFalse(ProStatus.Free.isPro)
    }

    @Test fun activeSubscriptionIsPro() {
        val status = resolve(listOf(purchase(ProProduct.Annual, 200 * DAY)))
        assertEquals(ProStatus.Pro(ProProduct.Annual, now + 200 * DAY, isTrial = false, willRenew = true, isGrace = false), status)
        assertTrue(status.isPro && status.allows(ProFeature.VideoCompression))
    }

    @Test fun expiredSubscriptionIsFree() {
        assertEquals(ProStatus.Free, resolve(listOf(purchase(ProProduct.Monthly, -1))))
    }

    @Test fun trialIsReportedAndIsPro() {
        val status = resolve(listOf(purchase(ProProduct.Annual, 5 * DAY, trial = true))) as ProStatus.Pro
        assertTrue(status.isTrial)
    }

    @Test fun lifetimeBeatsSubscriptionsAndNeverExpires() {
        val status = resolve(listOf(purchase(ProProduct.Annual, 300 * DAY), purchase(ProProduct.Lifetime, null)))
        assertEquals(ProStatus.Pro(ProProduct.Lifetime, null, isTrial = false, willRenew = false, isGrace = false), status)
    }

    @Test fun revokedAndUnknownPurchasesAreIgnored() {
        assertEquals(ProStatus.Free, resolve(listOf(purchase(ProProduct.Lifetime, null, revoked = true))))
        assertEquals(ProStatus.Free, resolve(listOf(purchase(ProProduct.Annual, id = "com.other.app.pro"))))
        // A subscription with no end date can't be trusted.
        assertEquals(ProStatus.Free, resolve(listOf(purchase(ProProduct.Annual, expiresIn = null))))
    }

    @Test fun theLongestLastingSubscriptionWins() {
        val status = resolve(listOf(purchase(ProProduct.Monthly, 10 * DAY, trial = true), purchase(ProProduct.Annual, 90 * DAY))) as ProStatus.Pro
        assertEquals(ProProduct.Annual, status.product)
    }

    @Test fun anEmptyListFromTheStoreIsAuthoritative() {
        val cached = ProStatus.Pro(ProProduct.Annual, now + DAY, isTrial = false, willRenew = true, isGrace = false)
        assertEquals(ProStatus.Free, resolve(emptyList(), cached))
    }

    @Test fun offlineKeepsLifetimeAndUnexpiredSubscriptions() {
        val lifetime = ProStatus.Pro(ProProduct.Lifetime, null, isTrial = false, willRenew = false, isGrace = false)
        assertEquals(lifetime, resolve(null, lifetime, now + 500 * DAY))
        val active = ProStatus.Pro(ProProduct.Annual, now + DAY, isTrial = false, willRenew = true, isGrace = false)
        assertEquals(active, resolve(null, active))
        assertEquals(ProStatus.Free, resolve(null, ProStatus.Free))
    }

    @Test fun offlineGraceCoversAShortOutageThenEnds() {
        val cached = ProStatus.Pro(ProProduct.Monthly, now, isTrial = false, willRenew = true, isGrace = false)
        assertEquals(cached.copy(isGrace = true), resolve(null, cached, now + 2 * DAY))
        assertEquals(ProStatus.Free, resolve(null, cached, now + Entitlement.OFFLINE_GRACE_MS + 1))
    }

    @Test fun everyProFeatureFollowsTheStatus() {
        val pro = ProStatus.Pro(ProProduct.Lifetime, null, isTrial = false, willRenew = false, isGrace = false)
        for (feature in ProFeature.entries) {
            assertTrue(pro.allows(feature))
            assertFalse(ProStatus.Free.allows(feature))
        }
    }
}

class UsageLedgerTest {
    @Test fun monthKeyUsesTheDeviceTimeZone() {
        assertEquals("2026-10", UsageLedger.monthKey(date(2026, 10, 3), bangkok))
        assertEquals("2026-01", UsageLedger.monthKey(date(2026, 1, 31, 23), bangkok))
    }

    @Test fun aNewMonthStartsFresh() {
        val ledger = UsageLedger.empty(date(2026, 10, 3), bangkok).recordingCleanup(400_000_000, date(2026, 10, 3), bangkok)
        assertEquals(400_000_000, ledger.cleanupBytes)
        assertEquals(400_000_000, ledger.rolled(date(2026, 10, 31, 23), bangkok).cleanupBytes)
        assertEquals(UsageLedger("2026-11"), ledger.rolled(date(2026, 11, 1, 0), bangkok))
    }

    @Test fun changingTheClockBackDoesNotResetTheAllowance() {
        val ledger = UsageLedger.empty(date(2026, 10, 3), bangkok).recordingCleanup(900_000_000, date(2026, 10, 3), bangkok)
        val rewound = ledger.rolled(date(2026, 9, 1), bangkok)
        assertEquals(900_000_000, rewound.cleanupBytes)
        assertEquals("2026-10", rewound.month)
    }

    @Test fun recordingCountsAndIgnoresNegatives() {
        val ledger = UsageLedger.empty(now, bangkok)
            .recordingBackups(10, now, bangkok).recordingBackups(-5, now, bangkok).recordingCleanup(-1, now, bangkok)
        assertEquals(10, ledger.backupFiles)
        assertEquals(0, ledger.cleanupBytes)
    }

    @Test fun recordingInANewMonthRollsFirst() {
        val old = UsageLedger("2026-09", 800_000_000, 90)
        assertEquals(UsageLedger("2026-10", 100_000_000, 0), old.recordingCleanup(100_000_000, date(2026, 10, 2), bangkok))
    }
}

private val free = ProStatus.Free
private val pro = ProStatus.Pro(ProProduct.Annual, null, isTrial = false, willRenew = true, isGrace = false)

private fun allowances(status: ProStatus = free, usedBytes: Long = 0, usedFiles: Int = 0, at: Long = now) =
    Allowances(status, UsageLedger(UsageLedger.monthKey(at, bangkok), usedBytes, usedFiles), at, zone = bangkok)

private fun item(id: String, megabytes: Long, safety: SafetyLevel) = CleanupItem(id, megabytes * 1_000_000, safety)

class AllowanceTest {
    @Test fun freeHasLimitsAndProHasNone() {
        assertEquals(700_000_000L, allowances(usedBytes = 300_000_000).cleanupRemaining)
        assertEquals(60, allowances(usedFiles = 40).backupRemaining)
        assertEquals(0L, allowances(usedBytes = 5_000_000_000).cleanupRemaining)
        assertNull(allowances(pro, usedBytes = 5_000_000_000).cleanupRemaining)
        assertNull(allowances(pro).backupRemaining)
    }

    @Test fun allowancesResetOnTheFirstOfNextMonth() {
        assertEquals(date(2026, 11, 1, 0), allowances(at = date(2026, 10, 3)).resetDate)
        assertEquals(date(2027, 1, 1, 0), allowances(at = date(2026, 12, 31, 23)).resetDate)
    }

    @Test fun aStaleLedgerIsRolledBeforeUse() {
        val stale = Allowances(free, UsageLedger("2026-09", 1_000_000_000, 100), now, zone = bangkok)
        assertEquals(1_000_000_000L, stale.cleanupRemaining)
        assertEquals(100, stale.backupRemaining)
    }

    @Test fun rulesAndCloudAccountsAreCappedOnFree() {
        assertTrue(allowances().canCreateRule(0))
        assertFalse(allowances().canCreateRule(1))
        assertTrue(allowances(pro).canCreateRule(25))
        assertTrue(allowances().canConnectCloudAccount(0))
        assertFalse(allowances().canConnectCloudAccount(1))
        assertTrue(allowances(pro).canConnectCloudAccount(2))
    }

    @Test fun backupIsTrimmedToWhatIsLeft() {
        assertEquals(10, allowances(usedFiles = 90).backupAllowedCount(25))
        assertEquals(0, allowances(usedFiles = 100).backupAllowedCount(25))
        assertEquals(25, allowances(usedFiles = 10).backupAllowedCount(25))
        assertEquals(500, allowances(pro, usedFiles = 999).backupAllowedCount(500))
    }
}

class CleanupGateTest {
    @Test fun proAndSmallSelectionsAreAllowed() {
        assertEquals(CleanupGateResult.Allowed, allowances(pro).gate(listOf(item("a", 5_000, SafetyLevel.Safe))))
        assertEquals(CleanupGateResult.Allowed, allowances(usedBytes = 100_000_000).gate(listOf(item("a", 400, SafetyLevel.Safe))))
        assertEquals(CleanupGateResult.Allowed, allowances(usedBytes = 400_000_000).gate(listOf(item("a", 600, SafetyLevel.Safe))))
        assertEquals(CleanupGateResult.Allowed, allowances().gate(emptyList()))
    }

    @Test fun anExhaustedAllowanceBlocksEverything() {
        assertEquals(CleanupGateResult.Exhausted, allowances(usedBytes = 1_000_000_000).gate(listOf(item("a", 1, SafetyLevel.VerySafe))))
        assertEquals(CleanupGateResult.Allowed, allowances(usedBytes = 1_000_000_000).gate(emptyList()))
    }

    @Test fun aTooBigSelectionKeepsTheSafestItemsThatFit() {
        val selection = listOf(item("a", 300, SafetyLevel.ReviewFirst), item("b", 600, SafetyLevel.VerySafe), item("c", 400, SafetyLevel.Safe))
        assertEquals(
            CleanupGateResult.Partial(listOf("b", "c"), 1_000_000_000, 1_000_000_000),
            allowances().gate(selection),
        )
    }

    @Test fun itemsThatDoNotFitAreSkippedAndSmallerOnesStillTried() {
        val selection = listOf(item("x", 600, SafetyLevel.VerySafe), item("y", 300, SafetyLevel.Safe), item("z", 200, SafetyLevel.ReviewFirst))
        assertEquals(
            CleanupGateResult.Partial(listOf("y", "z"), 500_000_000, 500_000_000),
            allowances(usedBytes = 500_000_000).gate(selection),
        )
    }

    @Test fun equalSafetyKeepsTheSelectionOrder() {
        val selection = listOf("first", "second", "third", "fourth").map { item(it, 300, SafetyLevel.Safe) }
        val result = allowances().gate(selection) as CleanupGateResult.Partial
        assertEquals(listOf("first", "second", "third"), result.ids)
    }

    @Test fun whenNothingFitsItSaysSo() {
        assertEquals(
            CleanupGateResult.NoneFit(100_000_000),
            allowances(usedBytes = 900_000_000).gate(listOf(item("big", 500, SafetyLevel.VerySafe))),
        )
    }

    @Test fun aPartialResultNeverExceedsTheAllowance() {
        val levels = listOf(SafetyLevel.VerySafe, SafetyLevel.Safe, SafetyLevel.ReviewFirst)
        val selection = (0 until 40).map { item("i$it", 30L + it * 7, levels[it % 3]) }
        var used = 0L
        while (used < 1_000_000_000L) {
            val result = allowances(usedBytes = used).gate(selection)
            if (result is CleanupGateResult.Partial) {
                val sum = selection.filter { it.id in result.ids }.sumOf { it.bytes }
                assertEquals(sum, result.bytes)
                assertTrue(result.bytes <= result.remaining)
            }
            used += 83_000_000
        }
    }
}

private class MapStorage(val map: MutableMap<String, String> = mutableMapOf()) : MonetizationStorage {
    override fun read(key: String) = map[key]
    override fun write(key: String, value: String) { map[key] = value }
}

class MonetizationStoreTest {
    private fun store(storage: MonetizationStorage, clock: () -> Long) = MonetizationStore(storage, zone = bangkok, clock = clock)

    @Test fun usageIsCountedAndPersisted() {
        val storage = MapStorage()
        val s = store(storage) { now }
        s.recordCleanup(250_000_000)
        s.recordBackups(12)
        assertEquals(750_000_000L, s.allowances().cleanupRemaining)

        val reopened = store(storage) { now }
        assertEquals(750_000_000L, reopened.allowances().cleanupRemaining)
        assertEquals(88, reopened.allowances().backupRemaining)
    }

    @Test fun aNewMonthResetsTheStoredUsage() {
        val storage = MapStorage()
        store(storage) { now }.recordCleanup(900_000_000)
        assertEquals(1_000_000_000L, store(storage) { date(2026, 11, 2) }.allowances().cleanupRemaining)
    }

    @Test fun proIsPersistedAndStopsCounting() {
        val storage = MapStorage()
        val s = store(storage) { now }
        s.applyPurchases(listOf(purchase(ProProduct.Annual, 100 * DAY)))
        assertTrue(s.isPro)
        s.recordCleanup(5_000_000_000)
        assertEquals(0, s.ledger.value.cleanupBytes)
        assertTrue(store(storage) { now }.isPro)
    }

    @Test fun aRefundTurnsProOffAndTheStoreBeingUnreachableDoesNot() {
        val storage = MapStorage()
        val s = store(storage) { now }
        s.applyPurchases(listOf(purchase(ProProduct.Annual, 100 * DAY)))
        s.applyPurchases(null)
        assertTrue(s.isPro)
        s.applyPurchases(listOf(purchase(ProProduct.Annual, 100 * DAY, revoked = true)))
        assertFalse(s.isPro)
        assertFalse(store(storage) { now }.isPro)
    }

    @Test fun freeIsTheDefaultAndRejectsGarbageInStorage() {
        val storage = MapStorage(mutableMapOf(MonetizationStore.KEY_STATUS to "not json", MonetizationStore.KEY_LEDGER to "nope"))
        val s = store(storage) { now }
        assertFalse(s.isPro)
        assertEquals(1_000_000_000L, s.allowances().cleanupRemaining)
    }
}

class PlayBillingMappingTest {
    private fun owned(id: String, purchased: Boolean = true, renewing: Boolean = true) = OwnedPurchase(listOf(id), now - DAY, purchased, renewing)

    @Test fun ownedSubscriptionIsTrustedForADayAndFeedsEntitlement() {
        val purchases = toStorePurchases(listOf(owned(ProProduct.Annual.id)), now)
        assertEquals(now + OWNED_TRUST_MS, purchases.single().expiresAt)
        assertTrue(purchases.single().willRenew)
        assertEquals(ProProduct.Annual, (resolve(purchases) as ProStatus.Pro).product)
    }

    @Test fun lifetimeHasNoExpiry() {
        val status = resolve(toStorePurchases(listOf(owned(ProProduct.Lifetime.id)), now)) as ProStatus.Pro
        assertEquals(ProProduct.Lifetime, status.product)
        assertNull(status.expiresAt)
    }

    @Test fun pendingAndUnknownPurchasesDoNotUnlock() {
        assertEquals(ProStatus.Free, resolve(toStorePurchases(listOf(owned(ProProduct.Annual.id, purchased = false)), now)))
        assertEquals(ProStatus.Free, resolve(toStorePurchases(listOf(owned("com.other.app.pro")), now)))
    }

    @Test fun aRefundedPurchaseJustStopsBeingListed() {
        val cached = ProStatus.Pro(ProProduct.Lifetime, null, isTrial = false, willRenew = false, isGrace = false)
        assertEquals(ProStatus.Free, resolve(toStorePurchases(emptyList(), now), cached))
    }

    @Test fun billingPeriodsBecomeDays() {
        assertEquals(7, billingPeriodDays("P7D"))
        assertEquals(7, billingPeriodDays("P1W"))
        assertEquals(30, billingPeriodDays("P1M"))
        assertEquals(365, billingPeriodDays("P1Y"))
        assertNull(billingPeriodDays("garbage"))
        assertNull(billingPeriodDays("P0D"))
    }
}

class QuotaGatePromptTest {
    private fun prompt(a: Allowances, selection: List<CleanupItem>) = QuotaGatePrompt.of(a.gate(selection), selection, a)

    @Test fun noSheetWhenTheDeletionFits() {
        assertNull(prompt(allowances(), listOf(item("a", 400, SafetyLevel.Safe))))
        assertNull(prompt(allowances(pro), listOf(item("a", 50_000, SafetyLevel.Safe))))
    }

    @Test fun partialOffersTheSafestItemsThatFit() {
        val p = prompt(allowances(usedBytes = 500_000_000), listOf(item("a", 400, SafetyLevel.ReviewFirst), item("b", 300, SafetyLevel.VerySafe), item("c", 400, SafetyLevel.Safe)))!!
        // 500 MB left: b (300, very safe) fits; c and a (400 each) no longer do.
        assertEquals(listOf("b"), p.partialIds)
        assertEquals(300_000_000L, p.partialBytes)
        assertEquals(1_100_000_000L, p.selectionBytes)
        assertEquals(500_000_000L, p.remaining)
        assertEquals(500_000_000L, p.used)
        assertEquals(1_000_000_000L, p.limit)
    }

    @Test fun exhaustedAndNoneFitOfferNoPartialDelete() {
        val exhausted = prompt(allowances(usedBytes = 1_000_000_000), listOf(item("a", 10, SafetyLevel.Safe)))!!
        assertEquals(QuotaGateKind.Exhausted, exhausted.kind)
        assertNull(exhausted.partialIds)
        assertEquals(0L, exhausted.remaining)
        val none = prompt(allowances(usedBytes = 900_000_000), listOf(item("big", 500, SafetyLevel.VerySafe)))!!
        assertEquals(QuotaGateKind.NoneFit, none.kind)
        assertNull(none.partialIds)
        assertEquals(100_000_000L, none.remaining)
    }

    @Test fun usedIsNeverShownAboveTheLimitAndResetIsNextMonth() {
        val p = prompt(allowances(usedBytes = 5_000_000_000), listOf(item("a", 10, SafetyLevel.Safe)))!!
        assertEquals(p.limit, p.used)
        assertEquals(date(2026, 11, 1, 0), p.resetDate)
    }
}
