package com.smartstorage.cleaner.ui.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.model.MemoryEvent
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.AssetImage
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ListTile
import com.smartstorage.cleaner.ui.components.MediaThumbnail
import com.smartstorage.cleaner.ui.components.ProtectedBadge
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SectionLabel
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** Enough to get a feel for the memory without loading hundreds of thumbnails. */
private const val PREVIEW_LIMIT = 60

/**
 * One trip or event: what it holds, why it is protected, and the little that could be cleaned.
 * Read-only on purpose — nothing here deletes anything. Mirrors `MemoryDetailView.swift`.
 */
@Composable
fun MemoryDetailScreen(memoryId: String, onOpenSimilar: () -> Unit, onReviewBlurry: () -> Unit, onBack: () -> Unit) {
    val memories = libraryState().content.memories
    val memory = memories.firstOrNull { it.id == memoryId } ?: memories.firstOrNull().takeIf { memoryId == "first" }
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth, onBack = onBack) {
        if (memory == null) {
            ScreenHeader("Memory", "This memory is no longer in your library.")
            return@ScreenScaffold
        }
        MemoryContent(memory, onOpenSimilar, onReviewBlurry)
    }
}

@Composable
private fun MemoryContent(memory: MemoryEvent, onOpenSimilar: () -> Unit, onReviewBlurry: () -> Unit) {
    val colors = SmartTheme.colors
    val noun = if (memory.kind == MemoryEvent.Kind.Trip) "trip" else "event"
    ScreenHeader(memory.title, memory.detail() ?: memory.summary, Modifier.padding(bottom = 8.dp))

    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        Box(Modifier.fillMaxWidth().height(if (wide) 320.dp else 220.dp)) {
            AssetImage(memory.coverUri, memory.style, Modifier.fillMaxWidth().height(if (wide) 320.dp else 220.dp),
                cornerRadius = SmartMetrics.cardRadius, iconScale = 0.2f)
            Box(Modifier.padding(14.dp)) { ProtectedBadge() }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("%,d".format(memory.photoCount), if (memory.photoCount == 1) "Photo" else "Photos", Modifier.weight(1f))
        Stat("%,d".format(memory.videoCount), if (memory.videoCount == 1) "Video" else "Videos", Modifier.weight(1f))
        Stat(if (memory.bytes > 0) memory.bytes.formattedBytes() else "—", "Size", Modifier.weight(1f))
    }

    SmartCard(style = CardStyle.Info) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = Tint.Teal.foreground(colors.isDark), modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Protected $noun", style = SmartType.cardHeadline, color = colors.textPrimary)
                Text(
                    "Photos from this $noun are never suggested on their own. Only extra shots of similar photos are offered, and blurry ones are never preselected.",
                    style = SmartType.metadata,
                    color = colors.textSecondary,
                )
            }
        }
    }

    if (memory.similarCount + memory.blurryCount > 0) {
        SmartCard(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.clickable(role = Role.Button, onClick = if (memory.similarCount > 0) onOpenSimilar else onReviewBlurry),
        ) {
            ListTile(Icons.Rounded.AutoAwesome, "Potential cleanup inside", tint = Tint.Teal,
                subtitle = "${memory.similarCount} similar ${if (memory.similarCount == 1) "photo" else "photos"} · ${memory.blurryCount} blurry ${if (memory.blurryCount == 1) "shot" else "shots"}")
        }
    }

    SectionLabel("Photos & videos")
    BoxWithConstraints {
        val columns = maxOf(3, (maxWidth / 110.dp).toInt())
        val cells: List<String?> = if (memory.assetUris.isEmpty()) List(9) { null } else memory.assetUris.take(PREVIEW_LIMIT)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.chunked(columns).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEachIndexed { i, uri ->
                        val cell = Modifier.weight(1f).aspectRatio(1f)
                        // Demo memories have placeholder art only.
                        if (uri == null) MediaThumbnail(memory.style, cell, variant = row * columns + i, cornerRadius = 10.dp)
                        else AssetImage(uri, memory.style, cell, cornerRadius = 10.dp)
                    }
                    repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (memory.assetUris.size > PREVIEW_LIMIT) {
        Text("and %,d more".format(memory.assetUris.size - PREVIEW_LIMIT), style = SmartType.metadata, color = colors.textSecondary)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = SmartTheme.colors
    SmartCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.Start) {
            Text(value, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1)
            Text(label, style = SmartType.metadata, color = colors.textSecondary)
        }
    }
}
