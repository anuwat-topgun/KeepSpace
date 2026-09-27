package com.smartstorage.cleaner.ui.feature.clean

import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.media.LocalLibraryStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SectionLabel
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.shell.Screen
import com.smartstorage.cleaner.ui.theme.SmartStorageTheme
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

enum class CleanupTarget(val title: String, val subtitle: String, val icon: ImageVector, val tint: Tint) {
    FiveGB("5 GB", "Quick cleanup of common files.", Icons.Rounded.AutoAwesome, Tint.Teal),
    TenGB("10 GB", "Good balance for everyday use.", Icons.Rounded.AutoAwesome, Tint.Teal),
    TwentyGB("20 GB", "Deeper cleanup for more space.", Icons.Rounded.Storage, Tint.Purple),
    MaximumSafe(
        "Maximum Safe Cleanup",
        "Frees up as much space as possible without deleting important data.",
        Icons.Rounded.VerifiedUser,
        Tint.Mint,
    );

    /** null = everything that is safe to suggest. */
    val bytes: Long?
        get() = when (this) {
            FiveGB -> 5_000_000_000
            TenGB -> 10_000_000_000
            TwentyGB -> 20_000_000_000
            MaximumSafe -> null
        }
}

/** 03 — Clean target selection. */
@Composable
fun CleanScreen(onOpen: (Screen) -> Unit) {
    val library = LocalLibraryStore.current
    val state = libraryState()
    var target by rememberSaveable { mutableStateOf(CleanupTarget.TenGB) }
    val colors = SmartTheme.colors

    ScreenScaffold {
        ScreenHeader("Clean", "How much space do you need?", Modifier.padding(bottom = 8.dp))

        CleanupTarget.entries.forEach { option ->
            val selected = option == target
            SmartCard(
                style = if (selected) CardStyle.Selected else CardStyle.Plain,
                modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                    target = option
                    library.setCleanupTarget(option.bytes)
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconTile(option.icon, option.tint)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(option.title, style = SmartType.optionTitle, color = colors.textPrimary)
                        Text(option.subtitle, style = SmartType.metadata, color = colors.textSecondary)
                    }
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }

        SmartCard(style = CardStyle.Info) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconTile(Icons.Rounded.Schedule, Tint.Blue)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel("Estimated review time")
                    Text(state.cleanupPlan.reviewTime, style = SmartType.metric, color = colors.textPrimary)
                }
            }
        }

        PrimaryButton(
            "Build Cleanup Plan",
            onClick = {
                library.setCleanupTarget(target.bytes)
                onOpen(Screen.CleanupPlan)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Preview(widthDp = 393, heightDp = 852)
@Composable
private fun CleanPreview() {
    SmartStorageTheme { CleanScreen(onOpen = {}) }
}
