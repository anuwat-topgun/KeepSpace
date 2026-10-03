package com.smartstorage.cleaner.ui.feature.settings

import com.smartstorage.cleaner.ui.i18n.localizedFormat
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.smartstorage.cleaner.media.LibraryAccess
import com.smartstorage.cleaner.media.LocalLibraryStore
import com.smartstorage.cleaner.media.LocalRequestLibraryAccess
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.ListTile
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SecondaryButton
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.components.StatusBadge
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

// Mirrors ios/SmartStorage/Features/Settings/{PhotoAccess,Privacy,About}View.swift.

/** Photo Access: what KeepSpace can see, and how to change it. */
@Composable
fun PhotoAccessScreen(onBack: () -> Unit) {
    val access = libraryState().access
    val context = LocalContext.current
    val requestAccess = LocalRequestLibraryAccess.current
    val colors = SmartTheme.colors
    val (title, detail) = when (access) {
        LibraryAccess.Authorized -> "Full access" to "KeepSpace can analyze your whole library on this device to find space you can recover."
        LibraryAccess.Limited -> "Limited access" to "KeepSpace only sees the photos you selected, so suggestions cover just those. Choose more for a fuller picture."
        LibraryAccess.Denied -> "Access is off" to "Without access KeepSpace can't find anything to clean up. Turn it on in Settings."
        LibraryAccess.NotDetermined -> "Not set yet" to "KeepSpace asks once, then works entirely on this device."
    }
    ScreenScaffold(onBack = onBack) {
        ScreenHeader("Photo Access", "Your photos are analyzed on this device only.", Modifier.padding(bottom = 8.dp))
        SmartCard(style = CardStyle.Hero) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge(title, if (access.canRead) Icons.Rounded.CheckCircle else Icons.Rounded.Error, if (access.canRead) Tint.Mint else Tint.Amber)
                Text(detail, style = SmartType.body, color = colors.textSecondary)
            }
        }
        if (access == LibraryAccess.NotDetermined) {
            PrimaryButton("Allow Photo Access", onClick = requestAccess, modifier = Modifier.fillMaxWidth())
        }
        if (access == LibraryAccess.Limited) {
            // Android 14+ lets the user pick more photos from the same permission dialog.
            PrimaryButton("Select More Photos", onClick = requestAccess, modifier = Modifier.fillMaxWidth())
        }
        val openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        if (access == LibraryAccess.Limited) SecondaryButton("Open Android Settings", openSettings, Modifier.fillMaxWidth(), outlined = true)
        else if (access != LibraryAccess.NotDetermined) PrimaryButton("Open Android Settings", onClick = openSettings, modifier = Modifier.fillMaxWidth())
        SmartCard(style = CardStyle.Info) {
            Text(
                "Deleting always asks you first, and items stay in Trash for 30 days.",
                style = SmartType.metadata,
                color = colors.textSecondary,
            )
        }
    }
}

/** Privacy & Security: what stays on the device, and a way to forget what was learned about the library. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val store = LocalLibraryStore.current
    val demo = libraryState().isDemo
    var confirming by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(onBack = onBack) {
        ScreenHeader("Privacy & Security", "AI stays here. Backups go only where you choose.", Modifier.padding(bottom = 8.dp))
        Fact(Icons.Rounded.Memory, Tint.Blue, "AI runs on this device",
            "Similar photos, faces, and text in screenshots and receipts are found with models bundled inside the app. Nothing is sent to a server.")
        Fact(Icons.AutoMirrored.Rounded.TextSnippet, Tint.Purple, "Text is never stored",
            "Screenshots and receipts are read only to sort them. KeepSpace keeps the category, a ticket's date, and a receipt's merchant, date and amount — not the text.")
        Fact(Icons.Rounded.MyLocation, Tint.Mint, "Locations stay here too",
            "Photo locations are used on this device to tell trips from home. They are never looked up online or shared.")
        Fact(Icons.Rounded.CloudUpload, Tint.Teal, "Direct cloud backup",
            "When you start a backup, selected files go over encrypted HTTPS directly to your Google Drive or OneDrive. KeepSpace has no media server and never receives them.")
        Fact(Icons.Rounded.DeleteForever, Tint.Coral, "You confirm every delete",
            "Android asks before anything is removed, and items stay in Trash for 30 days. Favorites and memories are never preselected.")
        SmartCard(contentPadding = PaddingValues(16.dp)) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListTile(Icons.Rounded.RestartAlt, "Clear analysis cache", tint = Tint.Amber, showsChevron = false,
                    subtitle = "Forget what KeepSpace learned about your library. It will look again on the next scan.")
                SecondaryButton("Clear Cache", onClick = { if (!demo) confirming = true }, modifier = Modifier.fillMaxWidth(), outlined = true)
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Clear the analysis cache?") },
            text = { Text("Your photos aren't touched. The next scan takes longer while KeepSpace re-analyzes them.") },
            confirmButton = { TextButton(onClick = { confirming = false; store.clearAnalysisCache() }) { Text("Clear Cache") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Fact(icon: ImageVector, tint: Tint, title: String, detail: String) {
    val colors = SmartTheme.colors
    SmartCard(contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            IconTile(icon, tint, size = 44.dp)
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = SmartType.cardHeadline, color = colors.textPrimary)
                Text(detail, style = SmartType.metadata, color = colors.textSecondary)
            }
        }
    }
}

/** About: version and the open-source pieces KeepSpace is built on. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = SmartTheme.colors
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("1.0")
    }
    val openWebPage: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    ScreenScaffold(onBack = onBack) {
        ScreenHeader("About", "Keep space. Keep what matters.", Modifier.padding(bottom = 8.dp))
        SmartCard(contentPadding = PaddingValues(16.dp)) {
            ListTile(Icons.Rounded.Info, "KeepSpace", tint = Tint.Teal, subtitle = localizedFormat("Version %@", version), showsChevron = false)
        }
        SmartCard(style = CardStyle.Info) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Text(
                    "AI processing never leaves your device. Files go only to the cloud destinations you choose.",
                    style = SmartType.metadata,
                    color = colors.textSecondary,
                )
            }
        }
        SmartCard(contentPadding = PaddingValues(16.dp)) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    "Privacy Policy",
                    onClick = { openWebPage(PRIVACY_POLICY_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    outlined = true,
                )
                SecondaryButton(
                    "Terms of Use",
                    onClick = { openWebPage(TERMS_OF_USE_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    outlined = true,
                )
            }
        }
        SmartCard(contentPadding = PaddingValues(16.dp)) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Open-source software", style = SmartType.cardHeadline, color = colors.textPrimary)
                LICENSES.forEach { (name, license) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // The name takes the remaining width and wraps, so a long name never runs into the licence.
                        Text(name, style = SmartType.metadata, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(license, style = SmartType.metadata, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

private const val PRIVACY_POLICY_URL = "https://github.com/anuwat-topgun/KeepSpace/blob/main/legal/privacy-policy.md"
private const val TERMS_OF_USE_URL = "https://github.com/anuwat-topgun/KeepSpace/blob/main/legal/terms-of-use.md"

/** Libraries shipped in the app. Keep in step with app/build.gradle.kts. */
private val LICENSES = listOf(
    "Tesseract OCR (Thai text)" to "Apache 2.0",
    "Tesseract4Android" to "Apache 2.0",
    "Tesseract Thai & English models" to "Apache 2.0",
    "ML Kit (text, face, barcode)" to "Google APIs terms",
    "AndroidX, Jetpack Compose, Room, Media3" to "Apache 2.0",
    "Kotlin, Kotlin coroutines" to "Apache 2.0",
)
