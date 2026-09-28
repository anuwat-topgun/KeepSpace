package com.smartstorage.cleaner

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun demoLibraryPrimaryNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("keepspace", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("hasCompletedOnboarding", true)
            .commit()

        val intent = Intent(context, MainActivity::class.java)
            .putExtra("demoData", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<MainActivity>(intent).use {
            compose.onNodeWithText("Your storage, organized intelligently.").assertExists()
            compose.onNodeWithText("Clean").performClick()
            compose.onNodeWithText("How much space do you need?").assertExists()
            compose.onNodeWithText("Library").performClick()
            compose.onNodeWithText("Review and organize your media.").assertExists()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("On-device AI only").assertExists()
            compose.onNodeWithText("Cloud").performClick()
            compose.onNodeWithText("OAuth setup required").assertExists()
        }
    }
}
