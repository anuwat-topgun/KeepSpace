package com.smartstorage.cleaner.ui.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.media.DeletionOutcome
import com.smartstorage.cleaner.media.LibraryState
import com.smartstorage.cleaner.media.LocalLibraryStore
import com.smartstorage.cleaner.media.LocalMediaActions
import com.smartstorage.cleaner.media.ReviewItem
import com.smartstorage.cleaner.media.ReviewKind
import com.smartstorage.cleaner.media.bytesOf
import com.smartstorage.cleaner.media.defaultSelection
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.model.ThumbnailStyle
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.AssetImage
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.DurationBadge
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.feature.clean.tint
import com.smartstorage.cleaner.ui.feature.clean.icon
import com.smartstorage.cleaner.ui.components.StatusBadge
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import kotlinx.coroutines.launch

/** What to review: a whole candidate set, or one similar-photo group. */
sealed interface ReviewSource {
    data class Kind(val kind: ReviewKind) : ReviewSource
    data class Group(val groupId: String) : ReviewSource
}

/**
 * Review-before-delete grid. Nothing is removed until the user taps Delete *and* confirms
 * (system trash prompt on Android 11+, in-app dialog before that). Mirrors `ReviewView.swift`.
 */
@Composable
fun ReviewScreen(source: ReviewSource, onBack: () -> Unit) {
    val state = libraryState()
    val store = LocalLibraryStore.current
    val actions = LocalMediaActions.current
    val scope = rememberCoroutineScope()
    val colors = SmartTheme.colors

    val items = reviewItems(source, state)
    // null until the user changes it, so the default follows the current items.
    var selection by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    val selected = selection ?: items.defaultSelection
    var deleting by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }

    val title = when (source) {
        is ReviewSource.Kind -> source.kind.title
        is ReviewSource.Group -> "Review Group"
    }
    val explanation = when (source) {
        is ReviewSource.Kind -> source.kind.explanation
        is ReviewSource.Group -> "The best photo is kept. Select the others you don't need."
    }

    fun runDelete() {
        val act = actions ?: return
        scope.launch {
            deleting = true
            val outcome = store.delete(selected, act)
            deleting = false
            // Everything selected is gone; what's left was deliberately unselected, so keep it that way.
            if (outcome is DeletionOutcome.Deleted) selection = emptySet()
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                    ScreenHeader(title, explanation)
                    (source as? ReviewSource.Kind)?.kind?.let { kind ->
                        StatusBadge(kind.safety.title, kind.safety.icon, kind.safety.tint)
                    }
                    if (items.isEmpty()) {
                        SmartCard(style = CardStyle.Info) {
                            Text("Nothing left to review here.", style = SmartType.metadata, color = colors.textSecondary)
                        }
                    } else {
                        val selectable = items.filterNot { it.isKeeper }
                        val allSelected = selected.size == selectable.size
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${selected.size} of ${selectable.size} selected · ${items.bytesOf(selected).formattedBytes()}",
                                style = SmartType.metadata,
                                color = colors.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { selection = if (allSelected) emptySet() else selectable.mapTo(HashSet()) { it.id } }) {
                                Text(if (allSelected) "Deselect All" else "Select All", fontWeight = FontWeight.SemiBold, color = colors.accent)
                            }
                        }
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                ReviewTile(item, isSelected = item.id in selected) {
                    selection = if (item.id in selected) selected - item.id else selected + item.id
                }
            }
        }

        if (items.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.widthIn(max = SmartMetrics.readableWidth).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (deleting) {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
                    } else {
                        PrimaryButton(
                            "Delete ${selected.size} · ${items.bytesOf(selected).formattedBytes()}",
                            onClick = { if (actions?.needsInAppConfirmation == true) confirming = true else runDelete() },
                            modifier = Modifier.fillMaxWidth(),
                            showsArrow = false,
                            enabled = selected.isNotEmpty(),
                        )
                    }
                }
                Text(
                    if (actions?.needsInAppConfirmation == true) "Deleted items can't be recovered on this Android version."
                    else "You'll confirm in the next step. Items stay in Trash for 30 days.",
                    style = TextStyle(fontSize = 12.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete ${selected.size} items?") },
            text = { Text("This frees ${items.bytesOf(selected).formattedBytes()}. On this Android version deleted items can't be restored.") },
            confirmButton = { TextButton(onClick = { confirming = false; runDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

private fun reviewItems(source: ReviewSource, state: LibraryState): List<ReviewItem> = when (source) {
    is ReviewSource.Kind -> state.content.reviewSets[source.kind].orEmpty()
    is ReviewSource.Group -> {
        val group = state.content.photoGroups.firstOrNull { it.id == source.groupId }
        if (group == null) emptyList() else {
            val extras = state.content.reviewSets[ReviewKind.Similar].orEmpty().filter { it.id in group.assetUris }
            val keeperId = group.assetUris[group.recommendedIndex]
            val keeper = ReviewItem(keeperId, group.bytes - group.reclaimableBytes, false, 0, 0, preselected = false, isKeeper = true)
            // Keep capture order so the burst reads naturally.
            val byId = (extras + keeper).associateBy { it.id }
            group.assetUris.mapNotNull { byId[it] }
        }
    }
}

@Composable
private fun ReviewTile(item: ReviewItem, isSelected: Boolean, onToggle: () -> Unit) {
    val colors = SmartTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(2.5.dp, if (isSelected) colors.accent else Color.Transparent, shape)
            .toggleable(value = isSelected, enabled = !item.isKeeper, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        AssetImage(item.id, if (item.isVideo) ThumbnailStyle.Mountain else ThumbnailStyle.Sunset, Modifier.fillMaxSize(), cornerRadius = 12.dp)
        if (isSelected) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
        if (!item.isKeeper) {
            Icon(
                if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) colors.accent else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.Black.copy(alpha = 0.25f)),
            )
        }
        Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
            when {
                item.isKeeper -> Row(
                    Modifier.clip(CircleShape).background(colors.accent).padding(horizontal = 7.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    Text("Best", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
                }
                item.isVideo -> {
                    val s = (item.durationMs / 1000).toInt()
                    DurationBadge("%02d:%02d".format(s / 60, s % 60))
                }
            }
        }
    }
}
