package com.smartstorage.cleaner.ui.feature.settings

import com.smartstorage.cleaner.ui.i18n.localizedCount
import com.smartstorage.cleaner.media.LibraryAccess
import com.smartstorage.cleaner.media.libraryState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AccountTree
import com.smartstorage.cleaner.ui.i18n.localizedFormat
import androidx.compose.material.icons.rounded.Favorite
import com.smartstorage.cleaner.monetization.LocalMonetization
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CheckCircle
import com.smartstorage.cleaner.ui.i18n.Text
import com.smartstorage.cleaner.ui.i18n.AppLanguage
import com.smartstorage.cleaner.ui.i18n.localized
import androidx.compose.runtime.Composable
import com.smartstorage.cleaner.media.LocalLibraryStore
import androidx.compose.material.icons.rounded.AutoFixHigh
import com.smartstorage.cleaner.media.LocalWeeklyReminder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import com.smartstorage.cleaner.ui.components.CardRow
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.shell.Screen
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint
import com.smartstorage.cleaner.cloud.LocalCloudStore

/** 09 — Settings. Only features that are functional in the Store build are exposed here. */
@Composable
fun SettingsScreen(onOpen: (Screen) -> Unit) {
    val colors = SmartTheme.colors
    val context = LocalContext.current
    var showsLanguagePicker by remember { mutableStateOf(false) }
    ScreenScaffold {
        ScreenHeader("Settings", modifier = Modifier.padding(bottom = 8.dp))

        SmartCard(style = CardStyle.Hero) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.Rounded.Security, Tint.Blue)
                Text("On-device AI only", style = SmartType.optionTitle, color = colors.textPrimary)
                Text(
                    "AI stays on device. Backups go only where you choose.",
                    style = SmartType.body,
                    color = colors.textSecondary,
                )
            }
        }

        val proStatus by LocalMonetization.current.status.collectAsState()
        CardRow(Icons.Rounded.AutoAwesome, "Subscription · KeepSpace Pro", tint = Tint.Teal,
            subtitle = subscriptionSettingsSubtitle(proStatus)) { onOpen(Screen.Subscription) }
        CardRow(Icons.Rounded.Favorite, Screen.Memories.title, tint = Tint.Coral, subtitle = "Protected by default") {
            onOpen(Screen.Memories)
        }
        val cloud by LocalCloudStore.current.state.collectAsState()
        val connectedClouds = cloud.connections.count { it.connected }
        CardRow(Icons.Rounded.Cloud, Screen.CloudOverview.title, tint = Tint.Blue,
            subtitle = when {
                cloud.activeCount > 0 -> "${localized("Uploading")} · ${cloud.activeCount} · ${cloud.activeProgress}%"
                connectedClouds == 0 -> "Connect Google Drive or OneDrive"
                else -> localizedFormat("%d connected", connectedClouds)
            }) { onOpen(Screen.CloudOverview) }
        val taste by LocalLibraryStore.current.taste.state.collectAsState()
        CardRow(Icons.Rounded.AutoFixHigh, Screen.AiTaste.title, tint = Tint.Purple,
            isProLocked = !proStatus.isPro && taste.profile.decisions == 0,
            subtitle = when {
                !taste.enabled -> "Off"
                taste.profile.decisions == 0 -> "Learns as you choose"
                else -> localizedCount(taste.profile.decisions, "Learned from %d choice", "Learned from %d choices")
            }) { onOpen(Screen.AiTaste) }
        val accessText = when (libraryState().access) {
            LibraryAccess.Authorized -> "Full Access"
            LibraryAccess.Limited -> "Limited Access"
            LibraryAccess.Denied -> "Off"
            LibraryAccess.NotDetermined -> "Not set"
        }
        val rules by com.smartstorage.cleaner.media.LocalRuleStore.current.rules.collectAsState()
        val activeRules = rules.count { it.isEnabled }
        CardRow(Icons.Rounded.AccountTree, Screen.StorageRules.title, tint = Tint.Mint,
            subtitle = if (activeRules == 0) "No rules on" else localizedFormat(if (activeRules == 1) "%d rule on" else "%d rules on", activeRules)) { onOpen(Screen.StorageRules) }
        CardRow(Icons.Rounded.Photo, "Photo Access", tint = Tint.Coral, subtitle = accessText) { onOpen(Screen.PhotoAccess) }
        val weekly by LocalWeeklyReminder.current.state.collectAsState()
        CardRow(Icons.Rounded.Notifications, "Notifications", tint = Tint.Purple,
            subtitle = if (weekly.enabled) "Weekly Smart Clean · On" else "Weekly Smart Clean · Off") { onOpen(Screen.Notifications) }
        CardRow(Icons.Rounded.Language, "Language", tint = Tint.Teal, subtitle = localized(AppLanguage.displayName(context))) {
            showsLanguagePicker = true
        }
        CardRow(Icons.Rounded.VerifiedUser, "Privacy & Security", tint = Tint.Blue) { onOpen(Screen.Privacy) }
        CardRow(Icons.Rounded.Info, "About", tint = Tint.Gray) { onOpen(Screen.About) }
    }

    if (showsLanguagePicker) {
        val activity = context as android.app.Activity
        Dialog(onDismissRequest = { showsLanguagePicker = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.surface,
            ) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "Language",
                        style = SmartType.optionTitle,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    LazyColumn(Modifier.heightIn(max = 520.dp)) {
                        items(AppLanguage.options, key = { it.tag ?: "system" }) { option ->
                            val selected = AppLanguage.selectedTag(context) == option.tag
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showsLanguagePicker = false
                                        AppLanguage.select(activity, option.tag)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(option.nativeName, style = SmartType.body, color = colors.textPrimary)
                                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
