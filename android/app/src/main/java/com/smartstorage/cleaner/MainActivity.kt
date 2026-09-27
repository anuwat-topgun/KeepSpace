package com.smartstorage.cleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.smartstorage.cleaner.ui.feature.onboarding.OnboardingScreen
import com.smartstorage.cleaner.ui.shell.AppShell
import com.smartstorage.cleaner.ui.theme.SmartStorageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        setContent {
            SmartStorageTheme {
                var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
                if (onboarded) {
                    AppShell()
                } else {
                    OnboardingScreen(onContinue = {
                        prefs.edit { putBoolean(KEY_ONBOARDED, true) }
                        onboarded = true
                    })
                }
            }
        }
    }

    private companion object {
        const val PREFS = "keepspace"
        const val KEY_ONBOARDED = "hasCompletedOnboarding"
    }
}
