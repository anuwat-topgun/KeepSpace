package com.smartstorage.cleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import com.smartstorage.cleaner.media.RuleStore
import com.smartstorage.cleaner.ui.feature.onboarding.OnboardingScreen
import com.smartstorage.cleaner.ui.shell.AppShell
import com.smartstorage.cleaner.ui.theme.SmartStorageTheme

class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels()
    /** Registered at construction so the trash-request launcher exists before the activity starts. */
    private val mediaActions = MediaActions(this)
    private val ruleStore by lazy { RuleStore(applicationContext) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    /** `adb shell am start ... --ez demoData true` shows the mockup data set. */
    private val store: LibraryStore by lazy { libraryViewModel.store(demo = intent.getBooleanExtra(EXTRA_DEMO, false)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                CompositionLocalProvider(
                    LocalLibraryStore provides store,
                    LocalRequestLibraryAccess provides { permissionLauncher.launch(LibraryStore.permissions) },
                    LocalMediaActions provides mediaActions,
                    LocalRuleStore provides ruleStore,
                ) {
                    if (onboarded) AppShell() else OnboardingScreen(onContinue = { permissionLauncher.launch(LibraryStore.permissions) })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also covers returning from system Settings after changing photo access.
        if (prefs.getBoolean(KEY_ONBOARDED, false)) store.refreshAccess()
    }

    private companion object {
        const val PREFS = "keepspace"
        const val KEY_ONBOARDED = "hasCompletedOnboarding"
        const val EXTRA_DEMO = "demoData"
    }
}
