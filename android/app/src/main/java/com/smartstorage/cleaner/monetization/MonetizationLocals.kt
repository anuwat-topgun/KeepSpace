package com.smartstorage.cleaner.monetization

import androidx.compose.runtime.staticCompositionLocalOf

val LocalMonetization = staticCompositionLocalOf<MonetizationStore> { error("MonetizationStore not provided") }
val LocalProBilling = staticCompositionLocalOf<ProBillingService> { error("ProBillingService not provided") }

/** Opens the paywall, optionally focused on the Pro feature the person just tapped. Never call at launch, in onboarding or during a scan. */
val LocalPresentPaywall = staticCompositionLocalOf<(ProFeature?) -> Unit> { { } }
