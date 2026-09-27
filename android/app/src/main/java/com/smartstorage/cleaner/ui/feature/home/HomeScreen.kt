package com.smartstorage.cleaner.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.model.CleanupCategory
import com.smartstorage.cleaner.model.MockData
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
import com.smartstorage.cleaner.ui.theme.SmartStorageTheme
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** 02 — Home dashboard. */
@Composable
fun HomeScreen(onOpen: (Screen) -> Unit, onFreeUp: () -> Unit) {
    val storage = MockData.storage
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth) {
        ScreenHeader("Smart Storage", "Your storage, organized intelligently.", Modifier.padding(bottom = 8.dp))
        StorageHeroCard(storage, onFreeUp)
        AdaptiveGrid(CleanupCategory.entries) { category ->
            CardRow(
                icon = category.icon,
                title = category.title,
                tint = category.tint,
                subtitle = storage.categoryBytes[category]?.formattedBytes(),
                onClick = { onOpen(category.screen) },
            )
        }
    }
}

@Composable
private fun StorageHeroCard(storage: StorageSummary, onFreeUp: () -> Unit) {
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
            HorizontalDivider(color = colors.separator)

            // Side-by-side when there is room (tablets), stacked on narrow phones.
            BoxWithConstraints {
                if (maxWidth >= 420.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PotentialCleanup(storage)
                        Spacer(Modifier.weight(1f))
                        PrimaryButton("Free Up 10 GB", onFreeUp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PotentialCleanup(storage)
                        PrimaryButton("Free Up 10 GB", onFreeUp, Modifier.fillMaxWidth())
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
    }

internal val CleanupCategory.tint: Tint
    get() = when (this) {
        CleanupCategory.SimilarPhotos, CleanupCategory.ScreenRecordings -> Tint.Coral
        CleanupCategory.Screenshots -> Tint.Blue
        CleanupCategory.LargeVideos -> Tint.Purple
        CleanupCategory.BlurryPhotos -> Tint.Mint
    }

private val CleanupCategory.screen: Screen
    get() = when (this) {
        CleanupCategory.SimilarPhotos, CleanupCategory.BlurryPhotos -> Screen.SimilarPhotos
        CleanupCategory.Screenshots -> Screen.Screenshots
        CleanupCategory.LargeVideos, CleanupCategory.ScreenRecordings -> Screen.Videos
    }

@Preview(name = "Phone", widthDp = 393, heightDp = 852)
@Preview(name = "Tablet", widthDp = 1024, heightDp = 768)
@Composable
private fun HomePreview() {
    SmartStorageTheme { HomeScreen(onOpen = {}, onFreeUp = {}) }
}
