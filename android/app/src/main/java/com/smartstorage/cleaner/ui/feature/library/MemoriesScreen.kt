package com.smartstorage.cleaner.ui.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.model.MemoryEvent
import com.smartstorage.cleaner.ui.components.AdaptiveGrid
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.AssetImage
import com.smartstorage.cleaner.ui.components.ProtectedBadge
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** 10 — Memories: events and trips recognized on device and protected by default. */
@Composable
fun MemoriesScreen(onOpen: (String) -> Unit, onOpenSimilar: () -> Unit, onBack: () -> Unit) {
    val colors = SmartTheme.colors
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth, onBack = onBack) {
        ScreenHeader("Memories", "Important moments are protected by default.", Modifier.padding(bottom = 8.dp))

        val state = libraryState()
        val content = state.content
        if (content.memories.isEmpty()) {
            SmartCard(style = CardStyle.Info) {
                Text(
                    if (state.isScanning) "Looking for trips and events in your photos…"
                    else "No trips or events found yet. They appear as your library grows. Photos are never removed without your review.",
                    style = SmartType.metadata,
                    color = colors.textSecondary,
                )
            }
        }
        AdaptiveGrid(content.memories, minColumnWidth = 400.dp) { MemoryCard(it, onOpen = { onOpen(it.id) }) }

        if (content.tripSimilarPhotos + content.tripBlurryShots > 0) SmartCard(style = CardStyle.Info, modifier = Modifier.clickable(role = Role.Button, onClick = onOpenSimilar)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Tint.Teal.background(colors.isDark)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (content.memories.isEmpty()) "Potential cleanup in your photos" else "Potential cleanup inside trips",
                        style = SmartType.cardHeadline,
                        color = colors.textPrimary,
                    )
                    StatLine(Icons.Rounded.Collections, "${content.tripSimilarPhotos} similar ${if (content.tripSimilarPhotos == 1) "photo" else "photos"}")
                    StatLine(Icons.Rounded.BlurOn, "${content.tripBlurryShots} blurry ${if (content.tripBlurryShots == 1) "shot" else "shots"}")
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun StatLine(icon: ImageVector, text: String) {
    val colors = SmartTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Text(text, style = SmartType.body, color = colors.textSecondary)
    }
}

@Composable
private fun MemoryCard(memory: MemoryEvent, onOpen: () -> Unit) {
    val colors = SmartTheme.colors
    SmartCard(contentPadding = PaddingValues(16.dp), modifier = Modifier.clickable(role = Role.Button, onClick = onOpen)) {
        BoxWithConstraints {
            // Smaller artwork on phones so titles like "Birthday Party" stay on one line.
            val wide = maxWidth >= 480.dp
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AssetImage(
                    memory.coverUri,
                    memory.style,
                    Modifier.size(width = if (wide) 150.dp else 112.dp, height = if (wide) 116.dp else 96.dp),
                    cornerRadius = 16.dp,
                    iconScale = 0.35f,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(memory.title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    Text(memory.summary, style = SmartType.metadata, color = colors.textSecondary)
                    memory.detail()?.let { Text(it, style = TextStyle(fontSize = 12.sp), color = colors.textSecondary) }
                    ProtectedBadge()
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
            }
        }
    }
}
