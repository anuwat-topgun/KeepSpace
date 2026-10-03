package com.smartstorage.cleaner.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun offer(product: ProProduct, price: String, trial: Int? = null): ProOffer {
    val micros = (price.toBigDecimal() * 1_000_000.toBigDecimal()).toLong()
    return ProOffer(product, "$$price", micros, null, trial)
}

private val standard = listOf(
    offer(ProProduct.Monthly, "2.99"), offer(ProProduct.Annual, "19.99", trial = 7), offer(ProProduct.Lifetime, "39.99"),
)

class PaywallModelTest {
    @Test fun yearlyIsPreselectedWhateverTheOrder() {
        assertEquals(ProProduct.Annual, PaywallModel.defaultSelection(standard))
        assertEquals(ProProduct.Lifetime, PaywallModel.defaultSelection(listOf(offer(ProProduct.Lifetime, "39.99"), offer(ProProduct.Monthly, "2.99"))))
        assertNull(PaywallModel.defaultSelection(emptyList()))
    }

    @Test fun savingIsComputedFromStorePricesAndNeverOverstated() {
        assertEquals(44, PaywallModel.savePercent(standard)) // 44.28 % truncated
        assertNull(PaywallModel.savePercent(listOf(offer(ProProduct.Monthly, "2.00"), offer(ProProduct.Annual, "21.00"))))
        assertNull(PaywallModel.savePercent(listOf(offer(ProProduct.Monthly, "1.00"), offer(ProProduct.Annual, "20.00"))))
        assertNull(PaywallModel.savePercent(listOf(offer(ProProduct.Annual, "19.99"))))
    }

    @Test fun buttonAndFinePrintFollowThePlanAndTrialEligibility() {
        val yearlyTrial = offer(ProProduct.Annual, "19.99", trial = 7)
        assertEquals(PaywallCta.StartTrial(7), PaywallModel.cta(yearlyTrial))
        assertEquals(PaywallFinePrint.TrialThenYearly(7, "$19.99"), PaywallModel.finePrint(yearlyTrial))

        // A person who already had the trial sees no trial wording.
        val yearlyNoTrial = offer(ProProduct.Annual, "19.99")
        assertEquals(PaywallCta.SubscribeYearly("$19.99"), PaywallModel.cta(yearlyNoTrial))
        assertEquals(PaywallFinePrint.Yearly("$19.99"), PaywallModel.finePrint(yearlyNoTrial))

        assertEquals(PaywallCta.SubscribeMonthly("$2.99"), PaywallModel.cta(offer(ProProduct.Monthly, "2.99")))
        assertEquals(PaywallFinePrint.Monthly("$2.99"), PaywallModel.finePrint(offer(ProProduct.Monthly, "2.99")))
        assertEquals(PaywallCta.BuyLifetime("$39.99"), PaywallModel.cta(offer(ProProduct.Lifetime, "39.99")))
        assertEquals(PaywallFinePrint.Lifetime, PaywallModel.finePrint(offer(ProProduct.Lifetime, "39.99")))
    }

    @Test fun everyProFeatureMapsToABenefitRow() {
        for (feature in ProFeature.entries) assertTrue(ProBenefit.of(feature) in ProBenefit.entries)
        assertEquals(ProBenefit.UnlimitedBackupAndRules, ProBenefit.of(ProFeature.UnlimitedRules))
        assertEquals(ProBenefit.UnlimitedBackupAndRules, ProBenefit.of(ProFeature.MultipleCloudAccounts))
        assertEquals(6, ProBenefit.entries.size)
    }
}
