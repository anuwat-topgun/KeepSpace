import Foundation
import Testing
@testable import SmartStorage

/// Prices are given as text so the Decimal is exact (a float literal like 39.99 is not).
private func offer(_ product: ProProduct, _ price: String, trial: Int? = nil) -> ProOffer {
    ProOffer(product: product, displayPrice: "$\(price)", priceValue: Decimal(string: price)!, perMonthDisplay: nil, trialDays: trial)
}

private let standard = [offer(.monthly, "2.99"), offer(.annual, "19.99", trial: 7), offer(.lifetime, "39.99")]

struct PaywallModelTests {
    @Test func yearlyIsPreselectedWhateverTheOrder() {
        #expect(PaywallModel.defaultSelection(standard) == .annual)
        #expect(PaywallModel.defaultSelection([offer(.lifetime, "39.99"), offer(.monthly, "2.99")]) == .lifetime)
        #expect(PaywallModel.defaultSelection([]) == nil)
    }

    @Test func savingIsComputedFromStorePricesAndNeverOverstated() {
        // 12 × 2.99 = 35.88; 19.99 is 44.28 % cheaper → 44 (truncated).
        #expect(PaywallModel.savePercent(standard) == 44)
        // Under 20 % is not worth a chip.
        #expect(PaywallModel.savePercent([offer(.monthly, "2.00"), offer(.annual, "21.00")]) == nil)
        // Yearly dearer than monthly × 12, or a plan missing: no chip.
        #expect(PaywallModel.savePercent([offer(.monthly, "1.00"), offer(.annual, "20.00")]) == nil)
        #expect(PaywallModel.savePercent([offer(.annual, "19.99")]) == nil)
    }

    @Test func buttonAndFinePrintFollowThePlanAndTrialEligibility() {
        let yearlyTrial = offer(.annual, "19.99", trial: 7)
        #expect(PaywallModel.cta(for: yearlyTrial) == .startTrial(days: 7))
        #expect(PaywallModel.finePrint(for: yearlyTrial) == .trialThenYearly(days: 7, price: "$19.99"))

        // A person who already had the trial sees no trial wording.
        let yearlyNoTrial = offer(.annual, "19.99")
        #expect(PaywallModel.cta(for: yearlyNoTrial) == .subscribeYearly(price: "$19.99"))
        #expect(PaywallModel.finePrint(for: yearlyNoTrial) == .yearly(price: "$19.99"))

        #expect(PaywallModel.cta(for: offer(.monthly, "2.99")) == .subscribeMonthly(price: "$2.99"))
        #expect(PaywallModel.finePrint(for: offer(.monthly, "2.99")) == .monthly(price: "$2.99"))
        #expect(PaywallModel.cta(for: offer(.lifetime, "39.99")) == .buyLifetime(price: "$39.99"))
        #expect(PaywallModel.finePrint(for: offer(.lifetime, "39.99")) == .lifetime)
    }

    @Test func everyProFeatureMapsToABenefitRow() {
        for feature in ProFeature.allCases { #expect(ProBenefit.allCases.contains(ProBenefit(feature))) }
        #expect(ProBenefit(.unlimitedRules) == .unlimitedBackupAndRules)
        #expect(ProBenefit(.multipleCloudAccounts) == .unlimitedBackupAndRules)
        #expect(ProBenefit.allCases.count == 6)
    }
}
