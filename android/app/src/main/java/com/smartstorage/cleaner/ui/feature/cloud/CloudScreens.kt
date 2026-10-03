package com.smartstorage.cleaner.ui.feature.cloud

import com.smartstorage.cleaner.monetization.LocalMonetization
import com.smartstorage.cleaner.monetization.LocalPresentPaywall
import com.smartstorage.cleaner.monetization.ProFeature
import com.smartstorage.cleaner.ui.components.ProChip
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.i18n.localizedFormat
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.smartstorage.cleaner.cloud.CloudBackupScope
import com.smartstorage.cleaner.cloud.CloudProvider
import com.smartstorage.cleaner.cloud.CloudUploadStatus
import com.smartstorage.cleaner.cloud.LocalCloudStore
import com.smartstorage.cleaner.media.LocalLibraryStore
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.CardRow
import com.smartstorage.cleaner.ui.components.ListTile
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SecondaryButton
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.components.ToggleRow
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

@Composable
fun CloudOverviewScreen(onBackUpNow: () -> Unit, onBack: () -> Unit) {
    val cloud = LocalCloudStore.current
    val activity = LocalContext.current as Activity
    val state by cloud.state.collectAsState()
    var authorizing by remember { mutableStateOf<CloudProvider?>(null) }
    val browserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val provider = authorizing
        if (provider != null && result.resultCode == Activity.RESULT_OK) cloud.completeAuthorization(provider, result.data)
        else if (provider != null) cloud.completeAuthorization(provider, result.data)
        authorizing = null
    }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        cloud.completeGoogleAuthorization(activity, result.data)
        authorizing = null
    }
    val completed = state.uploads.count { it.status == CloudUploadStatus.BackedUp }
    val monetization = LocalMonetization.current
    val proStatus by monetization.status.collectAsState()
    val presentPaywall = LocalPresentPaywall.current
    ScreenScaffold(onBack = onBack) {
        ScreenHeader("Cloud Backup", "Direct from this device to your account. KeepSpace never receives your files.", Modifier.padding(bottom = 8.dp))
        state.connections.forEach { connection ->
            SmartCard {
              // A card's content is a Box, so the tile and its button need a Column or they draw on top of each other.
              Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ListTile(Icons.Rounded.Cloud, connection.provider.title, tint = if (connection.connected) Tint.Teal else Tint.Blue,
                    subtitle = when {
                        connection.connected -> connection.accountName ?: "Connected"
                        !connection.configured -> "OAuth setup required"
                        else -> "Not connected"
                    }, showsChevron = false)
                if (connection.connected) {
                    SecondaryButton("Disconnect", { cloud.disconnect(connection.provider) }, Modifier.fillMaxWidth(), outlined = true)
                } else {
                    // One cloud account is free; connecting another opens the paywall. Connected accounts are never disconnected.
                    val canConnect = monetization.allowances().canConnectCloudAccount(state.connections.count { it.connected })
                    PrimaryButton(if (connection.configured) "Connect ${connection.provider.title}" else "Setup required", {
                        if (!canConnect) { presentPaywall(ProFeature.MultipleCloudAccounts); return@PrimaryButton }
                        authorizing = connection.provider
                        if (connection.provider == CloudProvider.GoogleDrive) {
                            cloud.authorizeGoogle(activity, googleLauncher::launch)
                        } else {
                            cloud.authorizationIntent(connection.provider)?.let(browserLauncher::launch)
                        }
                    }, Modifier.fillMaxWidth(), showsArrow = false, enabled = connection.configured)
                    if (!canConnect) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { ProChip() }
                }
              }
            }
        }
        if (state.notice != null) Text(state.notice.orEmpty(), style = SmartType.metadata, color = SmartTheme.colors.textSecondary)
        if (state.uploads.isNotEmpty()) {
            Text("BACKUP ACTIVITY", style = SmartType.sectionLabel, color = SmartTheme.colors.textSecondary)
            state.uploads.takeLast(6).reversed().forEach { upload ->
                val icon = when (upload.status) {
                    CloudUploadStatus.BackedUp -> Icons.Rounded.CloudDone
                    CloudUploadStatus.Failed -> Icons.Rounded.Error
                    else -> Icons.Rounded.Schedule
                }
                SmartCard {
                    ListTile(icon, upload.sourceName, tint = if (upload.status == CloudUploadStatus.Failed) Tint.Coral else Tint.Teal,
                        subtitle = "${upload.provider.title} · ${upload.status.name} · ${upload.bytes.formattedBytes()}", showsChevron = false)
                    if (upload.status in setOf(CloudUploadStatus.Uploading, CloudUploadStatus.Verifying)) {
                        LinearProgressIndicator(progress = { upload.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    if (upload.error != null) Text(upload.error, style = SmartType.metadata, color = SmartTheme.colors.textSecondary)
                }
            }
            if (state.uploads.any { it.status == CloudUploadStatus.Failed }) {
                SecondaryButton("Retry failed uploads", cloud::retryFailed, Modifier.fillMaxWidth())
            }
            if (state.activeCount > 0) SecondaryButton("Cancel pending backup", cloud::cancelPending, Modifier.fillMaxWidth(), outlined = true)
        }
        PrimaryButton(if (completed > 0) "Back Up More" else "Back Up Now", onBackUpNow, Modifier.fillMaxWidth())
    }
}

@Composable
fun ManualBackupScreen(onBack: () -> Unit) {
    val cloud = LocalCloudStore.current
    val library = LocalLibraryStore.current
    val state by cloud.state.collectAsState()
    val connected = state.connections.filter { it.connected }
    var provider by remember(connected) { mutableStateOf(connected.firstOrNull()?.provider ?: CloudProvider.GoogleDrive) }
    var scope by remember { mutableStateOf(CloudBackupScope.Photos) }
    var folder by remember { mutableStateOf("Photos") }
    val candidates = library.backupCandidates(scope)

    ScreenScaffold(onBack = onBack) {
        ScreenHeader("Back Up Now", "Choose what to copy and where it should go.", Modifier.padding(bottom = 8.dp))
        Text("CONTENT", style = SmartType.sectionLabel, color = SmartTheme.colors.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudBackupScope.entries.forEach { option ->
                FilterChip(selected = scope == option, onClick = { scope = option; folder = option.title }, label = { Text(option.title) })
            }
        }
        Text("${candidates.size} items · ${candidates.sumOf { it.bytes }.formattedBytes()}", style = SmartType.metadata, color = SmartTheme.colors.textSecondary)

        Text("DESTINATION", style = SmartType.sectionLabel, color = SmartTheme.colors.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudProvider.entries.forEach { option ->
                val connection = state.connections.first { it.provider == option }
                FilterChip(selected = provider == option, enabled = connection.connected, onClick = { provider = option }, label = { Text(option.title) })
            }
        }
        OutlinedTextField(value = folder, onValueChange = { folder = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Folder inside KeepSpace") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Folder, null) }, singleLine = true)

        SmartCard {
            ToggleRow(Icons.Rounded.DriveFolderUpload, "Rename automatically", state.preferences.renameAutomatically,
                { cloud.updatePreferences(state.preferences.copy(renameAutomatically = it)) }, subtitle = "Uses date and a stable item suffix")
            ToggleRow(Icons.Rounded.Wifi, "Wi-Fi only", state.preferences.wifiOnly,
                { cloud.updatePreferences(state.preferences.copy(wifiOnly = it)) }, tint = Tint.Blue)
            ListTile(Icons.Rounded.Photo, "Keep local copies", tint = Tint.Coral,
                subtitle = "Always on in v1.1. Cloud Backup never deletes originals.", showsChevron = false)
        }
        if (connected.isEmpty()) {
            CardRow(Icons.Rounded.Cloud, "Connect a cloud account first", tint = Tint.Blue, subtitle = "Open Cloud in Settings")
        }
        // Free backs up about 100 files a month. Over that, the first files that fit go; the rest wait for next month or Pro.
        val monetization = LocalMonetization.current
        val proStatus by monetization.status.collectAsState()
        val presentPaywall = LocalPresentPaywall.current
        val allowed = remember(proStatus, candidates.size) { monetization.allowances().backupAllowedCount(candidates.size) }
        val exhausted = candidates.isNotEmpty() && allowed == 0
        if (allowed < candidates.size) {
            SmartCard(style = CardStyle.Info) {
                Text(
                    if (exhausted) "You've used this month's free backups."
                    else localizedFormat("Free backs up %d more files this month. The first %d will go; Pro has no limit.", allowed, allowed),
                    style = SmartType.metadata, color = SmartTheme.colors.textSecondary,
                )
            }
        }
        PrimaryButton(
            when {
                exhausted -> "Unlock Unlimited Backup"
                allowed < candidates.size -> localizedFormat("Back Up %d Files", allowed)
                else -> "Start Backup"
            },
            {
                if (exhausted) presentPaywall(ProFeature.UnlimitedBackup)
                else {
                    cloud.enqueue(provider, scope, folder, candidates.take(allowed))
                    monetization.recordBackups(allowed)
                }
            },
            Modifier.fillMaxWidth(),
            enabled = exhausted || (connected.any { it.provider == provider } && candidates.isNotEmpty() && folder.isNotBlank()),
        )
    }
}
