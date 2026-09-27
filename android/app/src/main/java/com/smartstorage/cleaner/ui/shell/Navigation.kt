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
    val bestShotPattern: String get() = "$route/bestshot/{$GROUP_ARG}"
    fun bestShotRoute(groupId: String): String = "$route/bestshot/${android.net.Uri.encode(groupId)}"
    val reviewPattern: String get() = "$route/review/{$REVIEW_ARG}"
    fun reviewRoute(kind: com.smartstorage.cleaner.media.ReviewKind): String = "$route/review/${kind.key}"
    val reviewGroupPattern: String get() = "$route/reviewgroup/{$GROUP_ARG}"
    fun reviewGroupRoute(groupId: String): String = "$route/reviewgroup/${android.net.Uri.encode(groupId)}"
    val receiptPattern: String get() = "$route/receipt/{$RECEIPT_ARG}"
    val rulePattern: String get() = "$route/rule/{$RULE_ARG}"
    fun ruleRoute(id: String): String = "$route/rule/${android.net.Uri.encode(id)}"
    fun receiptRoute(id: String): String = "$route/receipt/${android.net.Uri.encode(id)}"
    val memoryPattern: String get() = "$route/memory/{$MEMORY_ARG}"
    fun memoryRoute(id: String): String = "$route/memory/${android.net.Uri.encode(id)}"

    companion object {
        const val SCREEN_ARG = "screen"
        const val GROUP_ARG = "groupId"
        const val REVIEW_ARG = "kind"
        const val RECEIPT_ARG = "receiptId"
        const val RULE_ARG = "ruleId"
        const val MEMORY_ARG = "memoryId"
    }
}

/** Screens pushed inside a tab's back stack. Mirrors `Route` on iOS. */
enum class Screen(val title: String) {
    // v1.0
    CleanupPlan("Cleanup Plan"),
    SimilarPhotos("Similar Photos"),
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
    Receipts("Receipt Filing"),
    BackupVerification("Backed Up"),
}
