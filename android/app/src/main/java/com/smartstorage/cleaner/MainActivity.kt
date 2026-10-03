package com.smartstorage.cleaner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.smartstorage.cleaner.media.LibraryStore
import com.smartstorage.cleaner.media.LibraryViewModel
import com.smartstorage.cleaner.media.LocalLibraryStore
import com.smartstorage.cleaner.media.LocalMediaActions
import com.smartstorage.cleaner.media.LocalRequestLibraryAccess
import com.smartstorage.cleaner.media.LocalRuleStore
import com.smartstorage.cleaner.media.MediaActions
import com.smartstorage.cleaner.media.LocalWeeklyReminder
import com.smartstorage.cleaner.media.RuleStore
import com.smartstorage.cleaner.media.ScanPhase
import com.smartstorage.cleaner.media.WeeklyCleanReminder
import com.smartstorage.cleaner.ui.feature.onboarding.OnboardingScreen
import com.smartstorage.cleaner.ui.shell.AppShell
import com.smartstorage.cleaner.ui.theme.SmartStorageTheme
import com.smartstorage.cleaner.cloud.CloudStore
import com.smartstorage.cleaner.cloud.LocalCloudStore
import com.smartstorage.cleaner.monetization.MonetizationStore
import com.smartstorage.cleaner.monetization.PrefsMonetizationStorage
import com.smartstorage.cleaner.monetization.ProBillingService
import androidx.lifecycle.lifecycleScope
import com.smartstorage.cleaner.monetization.LocalMonetization
import com.smartstorage.cleaner.monetization.LocalPresentPaywall
import com.smartstorage.cleaner.monetization.LocalProBilling
import com.smartstorage.cleaner.monetization.ProFeature
import com.smartstorage.cleaner.ui.feature.paywall.PaywallScreen

class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels()
    /** Registered at construction so the trash-request launcher exists before the activity starts. */
    private val mediaActions = MediaActions(this)
    private val ruleStore by lazy { RuleStore(applicationContext) }
    private val weeklyReminder by lazy { WeeklyCleanReminder(applicationContext) }
    private val cloudStore by lazy { CloudStore(applicationContext) }
    private val monetization by lazy { MonetizationStore(PrefsMonetizationStorage(prefs)) }
    private val proBilling by lazy { ProBillingService(applicationContext, monetization, lifecycleScope) }
    /** The paywall request, if one is showing. Set only by a person tapping something locked or Upgrade — never at launch. */
    private var paywall by mutableStateOf<PaywallRequest?>(null)
    private var debugQuota by mutableStateOf(false)
    /** Bumped each time a reminder is tapped; the shell opens the Cleanup Plan for every new value. */
    private var openPlanRequest by mutableIntStateOf(0)
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    /** `adb shell am start ... --ez demoData true` shows the mockup data set. */
    private val store: LibraryStore by lazy { libraryViewModel.store(demo = intent.getBooleanExtra(EXTRA_DEMO, false)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleOpenIntent(intent)
        // Count confirmed deletions against the free monthly allowance.
        store.onDeleted = { bytes -> monetization.recordCleanup(bytes) }
        // AI Taste is Pro: Free keeps what was learned but stops learning.
        store.canLearnTaste = { monetization.isPro }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("debugQuotaGate", false)) debugQuota = true
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_PAYWALL, false)) {
            proBilling.useDemoOffers()
            paywall = PaywallRequest(intent.getStringExtra(EXTRA_DEBUG_FOCUS)?.let { name -> ProFeature.entries.firstOrNull { it.name == name } })
        }
        setContent {
            SmartStorageTheme {
                var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    store.refreshAccess(afterRequest = true)
                    if (!onboarded) {
                        prefs.edit { putBoolean(KEY_ONBOARDED, true) }
                        onboarded = true
                    }
                }
                // Keep the reminder's text current: it quotes the last completed scan.
                val libraryState by store.state.collectAsState()
                LaunchedEffect(libraryState.phase) {
                    if (libraryState.phase is ScanPhase.Ready && !libraryState.isDemo) {
                        weeklyReminder.recordScan(libraryState.content.storage.potentialCleanupBytes)
                    }
                }
                CompositionLocalProvider(
                    LocalLibraryStore provides store,
                    LocalRequestLibraryAccess provides { permissionLauncher.launch(LibraryStore.permissions) },
                    LocalMediaActions provides mediaActions,
                    LocalRuleStore provides ruleStore,
                    LocalWeeklyReminder provides weeklyReminder,
                    LocalCloudStore provides cloudStore,
                    LocalMonetization provides monetization,
                    LocalProBilling provides proBilling,
                    LocalPresentPaywall provides { feature -> paywall = PaywallRequest(feature) },
                ) {
                    if (onboarded) AppShell(openCleanupPlanRequest = openPlanRequest) else OnboardingScreen(onContinue = { permissionLauncher.launch(LibraryStore.permissions) })
                    if (BuildConfig.DEBUG && debugQuota) {
                        // `--ez debugQuotaGate true`: the quota gate with sample numbers (screenshots; demo data has no real sizes).
                        val sample = listOf(
                            com.smartstorage.cleaner.monetization.CleanupItem("a", 600_000_000, com.smartstorage.cleaner.media.SafetyLevel.VerySafe),
                            com.smartstorage.cleaner.monetization.CleanupItem("b", 700_000_000, com.smartstorage.cleaner.media.SafetyLevel.Safe),
                        )
                        val now = System.currentTimeMillis()
                        val allowances = com.smartstorage.cleaner.monetization.Allowances(
                            com.smartstorage.cleaner.monetization.ProStatus.Free,
                            com.smartstorage.cleaner.monetization.UsageLedger.empty(now).copy(cleanupBytes = 100_000_000), now,
                        )
                        com.smartstorage.cleaner.monetization.QuotaGatePrompt.of(allowances.gate(sample), sample, allowances)?.let { prompt ->
                            com.smartstorage.cleaner.ui.feature.paywall.QuotaGateSheet(prompt, onUnlock = { debugQuota = false }, onDeletePartial = { debugQuota = false }, onNotNow = { debugQuota = false })
                        }
                    }
                    paywall?.let { request -> PaywallScreen(focus = request.feature, onDismiss = { paywall = null }) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        if (intent?.getStringExtra(WeeklyCleanReminder.EXTRA_OPEN) == WeeklyCleanReminder.OPEN_CLEANUP_PLAN) {
            intent.removeExtra(WeeklyCleanReminder.EXTRA_OPEN) // don't reopen after rotation
            openPlanRequest++
        }
    }

    override fun onResume() {
        super.onResume()
        proBilling.start() // re-reads what the account owns (renewals, refunds, purchases made elsewhere)
        // Also covers returning from system Settings after changing photo access.
        if (prefs.getBoolean(KEY_ONBOARDED, false)) store.refreshAccess()
    }

    override fun onDestroy() {
        cloudStore.close()
        proBilling.close()
        super.onDestroy()
    }

    private data class PaywallRequest(val feature: ProFeature?)

    private companion object {
        const val PREFS = "keepspace"
        const val KEY_ONBOARDED = "hasCompletedOnboarding"
        const val EXTRA_DEMO = "demoData"
        const val EXTRA_DEBUG_PAYWALL = "debugPaywall"
        const val EXTRA_DEBUG_FOCUS = "debugPaywallFocus"
    }
}
