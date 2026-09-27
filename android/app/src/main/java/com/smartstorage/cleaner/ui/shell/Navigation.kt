package com.smartstorage.cleaner.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** The five top-level destinations (design handoff §3). Each owns a nested nav graph / back stack. */
enum class AppTab(val route: String, val title: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Clean("clean", "Clean", Icons.Rounded.AutoAwesome),
    Library("library", "Library", Icons.Rounded.PhotoLibrary),
    Insights("insights", "Insights", Icons.Rounded.BarChart),
    Settings("settings", "Settings", Icons.Rounded.Settings);

    val rootRoute: String get() = "$route/root"
    val screenPattern: String get() = "$route/screen/{$SCREEN_ARG}"
    fun screenRoute(screen: Screen): String = "$route/screen/${screen.name}"

    companion object {
        const val SCREEN_ARG = "screen"
    }
}

/** Screens pushed inside a tab's back stack. Mirrors `Route` on iOS. */
enum class Screen(val title: String) {
    // v1.0
    CleanupPlan("Cleanup Plan"),
    SimilarPhotos("Similar Photos"),
    BestShot("Best Shot"),
    Screenshots("Screenshots"),
    Videos("Videos"),
    Memories("Memories"),
    // v1.1
    CloudOverview("Cloud"),
    ManualBackup("Back Up Now"),
    // v1.2
    StorageRules("Storage Rules"),
    NewRule("New Rule"),
    // v1.3
    ReceiptFiling("Receipt Filing"),
    BackupVerification("Backed Up"),
}
