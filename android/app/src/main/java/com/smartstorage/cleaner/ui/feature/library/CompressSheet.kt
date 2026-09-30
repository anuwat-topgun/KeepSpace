package com.smartstorage.cleaner.ui.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.media.CompressionPreset
import com.smartstorage.cleaner.media.LocalLibraryStore
import com.smartstorage.cleaner.media.LocalMediaActions
import com.smartstorage.cleaner.model.VideoItem
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.AssetImage
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import kotlinx.coroutines.launch

/**
 * Compression as an alternative to deletion: pick a quality, see the estimated saving, then Android
 * asks to move the original to Trash. Mirrors `CompressSheet.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressSheet(video: VideoItem, onDismiss: () -> Unit) {
    val colors = SmartTheme.colors
    val store = LocalLibraryStore.current
    val actions = LocalMediaActions.current
    val scope = rememberCoroutineScope()
    val presets = CompressionPreset.entries.filter { video.estimatedSavings(it) != null }
    var preset by remember { mutableStateOf(presets.firstOrNull() ?: CompressionPreset.Hd1080) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Don't let a swipe dismiss the sheet mid-export.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { progress == null })

    ModalBottomSheet(onDismissRequest = { if (progress == null) onDismiss() }, sheetState = sheetState, containerColor = colors.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Compress Video", style = SmartType.cardHeadline, color = colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AssetImage(video.assetUri, video.style, Modifier.size(width = 96.dp, height = 64.dp))
                Column {
                    Text(video.title, style = SmartType.cardHeadline, color = colors.textPrimary)
                    Text("${video.bytes.formattedBytes()} · ${video.metadata}", style = SmartType.metadata, color = colors.textSecondary)
                }
            }
            presets.forEach { option ->
                val selected = option == preset
                SmartCard(
                    style = if (selected) CardStyle.Selected else CardStyle.Plain,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.clickable(enabled = progress == null, role = Role.RadioButton) { preset = option },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(option.title, style = SmartType.cardHeadline, color = colors.textPrimary)
                            Text(
                                "Saves about ${(video.estimatedSavings(option) ?: 0).formattedBytes()}",
                                style = SmartType.metadata,
                                color = colors.textSecondary,
                            )
                        }
                        Icon(
                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) colors.accent else colors.textSecondary,
                        )
                    }
                }
            }
            progress?.let { p ->
                Text(if (p < 1f) "Compressing on device…" else "Waiting for your confirmation…", style = SmartType.metadata, color = colors.textSecondary)
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.surfaceMuted)
            }
            error?.let {
                SmartCard(style = CardStyle.Info) { Text(it, style = SmartType.metadata, color = colors.textSecondary) }
            }
            PrimaryButton(
                "Compress",
                onClick = {
                    val act = actions ?: return@PrimaryButton
                    val id = video.assetUri ?: return@PrimaryButton
                    scope.launch {
                        error = null
                        progress = 0f
                        val message = store.compress(id, preset, act) { progress = it }
                        progress = null
                        if (message == null) onDismiss() else error = message
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                showsArrow = false,
                enabled = progress == null && presets.isNotEmpty() && actions?.canCompress == true,
            )
            Text(
                "Capture date is kept. The original moves to Trash for 30 days after you confirm.",
                style = TextStyle(fontSize = 12.sp),
                color = colors.textSecondary,
            )
        }
    }
}
