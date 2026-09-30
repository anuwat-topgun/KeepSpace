package com.smartstorage.cleaner.ui.feature.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.media.LibraryAccess
import com.smartstorage.cleaner.media.LibraryState
import com.smartstorage.cleaner.media.LocalLibraryStore
import com.smartstorage.cleaner.media.LocalRequestLibraryAccess
import com.smartstorage.cleaner.media.ScanPhase
import com.smartstorage.cleaner.model.CleanupCategory
import com.smartstorage.cleaner.model.StorageSummary
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.AdaptiveGrid
import com.smartstorage.cleaner.ui.components.CardRow
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SectionLabel
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.components.UsageBar
import com.smartstorage.cleaner.ui.shell.Screen
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** 02 — Home dashboard. */
@Composable
fun HomeScreen(onOpen: (Screen) -> Unit, onFreeUp: () -> Unit) {
    val state by LocalLibraryStore.current.state.collectAsState()
    val storage = state.content.storage
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth) {
        ScreenHeader("KeepSpace", "Your storage, organized intelligently.", Modifier.padding(bottom = 8.dp))
        StorageHeroCard(storage, showsCleanup = state.access.canRead, onFreeUp)
        LibraryStatusCard(state)
        if (state.access.canRead) {
            AdaptiveGrid(CleanupCategory.entries - CleanupCategory.Duplicates) { category ->
                CardRow(
                    icon = category.icon,
                    title = category.title,
                    tint = category.tint,
                    subtitle = subtitle(category, storage, state),
                    onClick = { onOpen(category.screen) },
                )
            }
        }
    }
}

/** Similar/blurry need the AI pass; say so rather than showing a misleading 0. */
private fun subtitle(category: CleanupCategory, storage: StorageSummary, state: LibraryState): String {
    val bytes = storage.categoryBytes[category] ?: 0
    val needsAnalysis = category == CleanupCategory.SimilarPhotos || category == CleanupCategory.BlurryPhotos
    return if (bytes == 0L && state.isScanning && needsAnalysis) "Analyzing…" else bytes.formattedBytes()
}

/** Permission prompt, limited-access note, or scan progress — whichever applies. */
@Composable
private fun LibraryStatusCard(state: LibraryState) {
    val colors = SmartTheme.colors
    val context = LocalContext.current
    val requestAccess = LocalRequestLibraryAccess.current
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }
    when (state.access) {
        LibraryAccess.NotDetermined -> StatusCard(
            Icons.Rounded.PhotoLibrary, Tint.Teal, "Allow photo access",
            "KeepSpace analyzes your library on this device to find space you can safely recover.",
            "Allow Access", requestAccess,
        )
        LibraryAccess.Denied -> StatusCard(
            Icons.Rounded.Lock, Tint.Coral, "Photo access is off",
            "Turn on photo access in Settings so KeepSpace can find similar photos, large videos and screenshots.",
            "Open Settings", openSettings,
        )
        LibraryAccess.Authorized, LibraryAccess.Limited -> when (val phase = state.phase) {
            is ScanPhase.Analyzing -> if (phase.total > 0) {
                SmartCard(style = CardStyle.Info) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                            Text("Analyzing on device · ${phase.done} of ${phase.total} items", style = SmartType.metadata, color = colors.textSecondary)
                        }
                        LinearProgressIndicator(
                            progress = { phase.done.toFloat() / phase.total },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accent,
                            trackColor = colors.surfaceMuted,
                        )
                    }
                }
            }
            ScanPhase.LoadingLibrary -> SmartCard(style = CardStyle.Info) {
                Text("Reading your library…", style = SmartType.metadata, color = colors.textSecondary)
            }
            else -> if (state.access == LibraryAccess.Limited) {
                StatusCard(
                    Icons.Rounded.Warning, Tint.Amber, "Limited access",
                    "KeepSpace only sees the photos you selected. Allow full access for a complete cleanup.",
                    "Change Access", requestAccess,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(icon: ImageVector, tint: Tint, title: String, detail: String, action: String, onAction: () -> Unit) {
    val colors = SmartTheme.colors
    SmartCard(style = CardStyle.Info) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(icon, tint, size = 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = SmartType.cardHeadline, color = colors.textPrimary)
                    Text(detail, style = SmartType.metadata, color = colors.textSecondary)
                }
            }
            PrimaryButton(action, onClick = onAction, modifier = Modifier.fillMaxWidth(), showsArrow = false)
        }
    }
}

@Composable
private fun StorageHeroCard(storage: StorageSummary, showsCleanup: Boolean, onFreeUp: () -> Unit) {
    val colors = SmartTheme.colors
    SmartCard(style = CardStyle.Hero) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel("Device Storage")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(storage.usedBytes.formattedBytes(), style = SmartType.metricLarge, color = colors.textPrimary)
                Text(
                    "/ ${storage.totalBytes.formattedBytes()}",
                    style = TextStyle(fontSize = 22.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            UsageBar(storage.usedFraction)
            Text("${storage.freeBytes.formattedBytes()} Free", style = SmartType.body, color = colors.textSecondary)
            if (showsCleanup) {
                HorizontalDivider(color = colors.separator)
                // Side-by-side when there is room (tablets), stacked on narrow phones.
                BoxWithConstraints {
                    if (maxWidth >= 420.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PotentialCleanup(storage)
                            Spacer(Modifier.weight(1f))
                            PrimaryButton("Free Up Space", onFreeUp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PotentialCleanup(storage)
                            PrimaryButton("Free Up Space", onFreeUp, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PotentialCleanup(storage: StorageSummary) {
    val colors = SmartTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(Icons.Rounded.AutoAwesome, Tint.Teal, size = 48.dp)
        Column {
            Text("Potential Cleanup", style = SmartType.metadata, color = colors.textSecondary)
            Text(storage.potentialCleanupBytes.formattedBytes(), style = SmartType.metric, color = colors.textPrimary)
        }
    }
}

internal val CleanupCategory.icon: ImageVector
    get() = when (this) {
        CleanupCategory.SimilarPhotos -> Icons.Rounded.Collections
        CleanupCategory.Screenshots -> Icons.Rounded.CropFree
        CleanupCategory.LargeVideos -> Icons.Rounded.Videocam
        CleanupCategory.ScreenRecordings -> Icons.Rounded.RadioButtonChecked
        CleanupCategory.BlurryPhotos -> Icons.Rounded.BlurOn
        CleanupCategory.Duplicates -> Icons.Rounded.FileCopy
    }

internal val CleanupCategory.tint: Tint
    get() = when (this) {
        CleanupCategory.SimilarPhotos, CleanupCategory.ScreenRecordings -> Tint.Coral
        CleanupCategory.Screenshots -> Tint.Blue
        CleanupCategory.LargeVideos -> Tint.Purple
        CleanupCategory.BlurryPhotos -> Tint.Mint
        CleanupCategory.Duplicates -> Tint.Blue
    }

private val CleanupCategory.screen: Screen
    get() = when (this) {
        CleanupCategory.SimilarPhotos, CleanupCategory.BlurryPhotos -> Screen.SimilarPhotos
        CleanupCategory.Screenshots -> Screen.Screenshots
        CleanupCategory.LargeVideos, CleanupCategory.ScreenRecordings -> Screen.Videos
        CleanupCategory.Duplicates -> Screen.CleanupPlan
    }
