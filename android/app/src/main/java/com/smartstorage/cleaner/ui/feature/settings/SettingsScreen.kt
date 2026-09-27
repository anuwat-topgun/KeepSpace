package com.smartstorage.cleaner.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/** 09 — Settings. Cloud (v1.1) and Storage Rules (v1.2) are pushed from here. */
@Composable
fun SettingsScreen(onOpen: (Screen) -> Unit) {
    val colors = SmartTheme.colors
    ScreenScaffold {
        ScreenHeader("Settings", modifier = Modifier.padding(bottom = 8.dp))

        SmartCard(style = CardStyle.Hero) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.Rounded.Security, Tint.Blue)
                Text("On-device AI only", style = SmartType.optionTitle, color = colors.textPrimary)
                Text(
                    "AI processing never leaves your device. Files are uploaded only to the cloud destinations you choose.",
                    style = SmartType.body,
                    color = colors.textSecondary,
                )
            }
        }

        CardRow(Icons.Rounded.Cloud, Screen.CloudOverview.title, tint = Tint.Blue, subtitle = "Google Drive · OneDrive") {
            onOpen(Screen.CloudOverview)
        }
        CardRow(Icons.Rounded.AccountTree, Screen.StorageRules.title, tint = Tint.Teal, subtitle = "Automatic filing") {
            onOpen(Screen.StorageRules)
        }
        CardRow(Icons.Rounded.Photo, "Photo Access", tint = Tint.Coral, subtitle = "Full Access")
        CardRow(Icons.Rounded.Notifications, "Notifications", tint = Tint.Purple, subtitle = "Weekly Smart Clean")
        CardRow(Icons.Rounded.WorkspacePremium, "Subscription", tint = Tint.Mint, subtitle = "Smart Storage Pro")
        CardRow(Icons.Rounded.VerifiedUser, "Privacy & Security", tint = Tint.Blue)
        CardRow(Icons.Rounded.Info, "About", tint = Tint.Gray)
    }
}
