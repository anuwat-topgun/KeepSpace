package com.smartstorage.cleaner.ui.feature.library

import com.smartstorage.cleaner.media.LocalMediaActions
import com.smartstorage.cleaner.media.CompressionPreset
import com.smartstorage.cleaner.media.ReviewKind
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Videocam
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.ui.components.AssetImage
import com.smartstorage.cleaner.model.VideoFilter
import com.smartstorage.cleaner.model.VideoItem
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.AdaptiveGrid
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ChipPicker
import com.smartstorage.cleaner.ui.components.DurationBadge
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.InlinePillButton
import com.smartstorage.cleaner.ui.components.MediaThumbnail
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SectionLabel
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** 08 — Large videos and screen recordings, with compression offered as an alternative to deletion. */
@Composable
fun VideosScreen(onReview: (ReviewKind) -> Unit, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(VideoFilter.All) }
    val colors = SmartTheme.colors
    val state = libraryState()
    val videos = state.content.videos.filter { filter.includes(it.kind) }

    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth, onBack = onBack) {
        ScreenHeader("Videos", "Large files and recordings.", Modifier.padding(bottom = 8.dp))

        SmartCard(style = CardStyle.Hero) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionLabel("Video storage")
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Metric(Icons.Rounded.Videocam, Tint.Purple, "Large Videos", state.content.largeVideoBytes.formattedBytes(), Modifier.weight(1f))
                    VerticalDivider(color = colors.separator)
                    Metric(Icons.Rounded.RadioButtonChecked, Tint.Coral, "Screen Recordings", state.content.recordingBytes.formattedBytes(), Modifier.weight(1f))
                }
            }
        }

        ChipPicker(
            options = VideoFilter.entries,
            selected = filter,
            onSelect = { filter = it },
            label = { it.label },
            modifier = Modifier.padding(vertical = 4.dp),
        )

        if (videos.isEmpty()) {
            SmartCard(style = CardStyle.Info) {
                Text(if (state.isScanning) "Reading your library…" else "No videos here.", style = SmartType.metadata, color = colors.textSecondary)
            }
        }

        Box(Modifier.animateContentSize()) {
            AdaptiveGrid(videos, minColumnWidth = 400.dp) { video -> VideoRow(video, onReview) }
        }
    }
}

@Composable
private fun Metric(icon: ImageVector, tint: Tint, title: String, value: String, modifier: Modifier) {
    val colors = SmartTheme.colors
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTile(icon, tint, size = 60.dp)
        Text(title, style = SmartType.metadata, color = colors.textSecondary)
        Text(value, style = SmartType.metricLarge, color = colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun VideoRow(video: VideoItem, onReview: (ReviewKind) -> Unit) {
    val colors = SmartTheme.colors
    var compressing by rememberSaveable(video.id) { mutableStateOf(false) }
    val canCompress = LocalMediaActions.current?.canCompress == true &&
        CompressionPreset.entries.any { video.estimatedSavings(it) != null }
    SmartCard(contentPadding = PaddingValues(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 64.dp)) {
                AssetImage(video.assetUri, video.style, Modifier.fillMaxWidth().fillMaxHeight())
                DurationBadge(video.duration, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(video.title, style = SmartType.cardHeadline, color = colors.textPrimary, maxLines = 2)
                Text(video.bytes.formattedBytes(), style = SmartType.metadata, color = colors.textSecondary)
                Text(video.metadata, style = SmartType.metadata, color = colors.textSecondary)
            }
            // Favourites and videos that wouldn't shrink are offered for review, not compression.
            if (video.isMeaningful || !canCompress) {
                InlinePillButton("Review", onClick = { onReview(ReviewKind.LargeVideos) }, tint = Tint.Mint)
            } else {
                InlinePillButton("Compress", onClick = { compressing = true }, tint = Tint.Teal)
            }
        }
    }
    if (compressing) CompressSheet(video, onDismiss = { compressing = false })
}
