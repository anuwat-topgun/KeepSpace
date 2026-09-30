package com.smartstorage.cleaner.ui.feature.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.model.GroupIcon
import com.smartstorage.cleaner.media.LibraryState
import com.smartstorage.cleaner.media.libraryState
import androidx.compose.material.icons.rounded.Collections
import com.smartstorage.cleaner.model.PhotoGroup
import com.smartstorage.cleaner.model.PhotoGroupFilter
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ChipPicker
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.components.StatusBadge
import com.smartstorage.cleaner.ui.components.ThumbnailStrip
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** Below this width the two-pane layout would cramp both panes. */
private val TwoPaneMinWidth = 820.dp

/**
 * 05 — Similar photo groups.
 * Phone: list → navigate to Best Shot. Wide window: list on the left, Best Shot for the selected group on the right.
 */
@Composable
fun SimilarPhotosScreen(onOpenGroup: (String) -> Unit, onReviewGroup: (String) -> Unit, onBack: () -> Unit) {
    val state = libraryState()
    val groups = state.content.photoGroups
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveId = selectedId ?: groups.firstOrNull()?.id

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(420.dp)) {
                    GroupList(state, selectedId = effectiveId, onBack = onBack, onSelect = { selectedId = it })
                }
                VerticalDivider(color = SmartTheme.colors.separator)
                Crossfade(targetState = effectiveId, label = "bestShot", modifier = Modifier.weight(1f)) { id ->
                    groups.firstOrNull { it.id == id }?.let { BestShotScreen(it, onBack = null, onReviewGroup = onReviewGroup) }
                }
            }
        } else {
            GroupList(state, selectedId = null, onBack = onBack, onSelect = onOpenGroup)
        }
    }
}

@Composable
private fun GroupList(state: LibraryState, selectedId: String?, onBack: () -> Unit, onSelect: (String) -> Unit) {
    val groups = state.content.photoGroups
    var filter by rememberSaveable { mutableStateOf(PhotoGroupFilter.All) }
    ScreenScaffold(onBack = onBack) {
        ScreenHeader(
            "Similar Photos",
            "${state.content.similarBytes.formattedBytes()} recoverable · ${groups.size} ${if (groups.size == 1) "group" else "groups"}",
        )
        if (groups.isEmpty()) {
            SmartCard(style = CardStyle.Info) {
                Text(
                    if (state.isScanning) "Looking for similar photos on this device…" else "No similar photos found.",
                    style = SmartType.metadata,
                    color = SmartTheme.colors.textSecondary,
                )
            }
        }
        ChipPicker(
            options = PhotoGroupFilter.entries,
            selected = filter,
            onSelect = { filter = it },
            label = { it.label },
            modifier = Modifier.padding(vertical = 4.dp),
        )
        groups.forEach { group ->
            PhotoGroupCard(group, isSelected = group.id == selectedId, onClick = { onSelect(group.id) })
        }
    }
}

@Composable
private fun PhotoGroupCard(group: PhotoGroup, isSelected: Boolean, onClick: () -> Unit) {
    val colors = SmartTheme.colors
    SmartCard(
        style = if (isSelected) CardStyle.Selected else CardStyle.Plain,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconTile(group.icon.vector, group.icon.tint)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(group.title, style = SmartType.cardHeadline, color = colors.textPrimary)
                    val recoverable = group.reclaimableBytes > 0
                    Text(
                        "${group.photoCount} photos · ${(if (recoverable) group.reclaimableBytes else group.bytes).formattedBytes()}${if (recoverable) " recoverable" else ""}",
                        style = SmartType.metadata,
                        color = colors.textSecondary,
                    )
                    StatusBadge("Recommended keep selected", Icons.Rounded.AutoAwesome, Tint.Teal, compact = true)
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            ThumbnailStrip(group.style, group.photoCount, assetUris = group.assetUris)
        }
    }
}

private val GroupIcon.vector: ImageVector
    get() = when (this) {
        GroupIcon.Beach -> Icons.Rounded.BeachAccess
        GroupIcon.Dinner -> Icons.Rounded.Restaurant
        GroupIcon.Person -> Icons.Rounded.Person
        GroupIcon.Family -> Icons.Rounded.Groups
        GroupIcon.Photos -> Icons.Rounded.Collections
    }

private val GroupIcon.tint: Tint
    get() = when (this) {
        GroupIcon.Beach -> Tint.Coral
        GroupIcon.Dinner -> Tint.Purple
        GroupIcon.Person -> Tint.Blue
        GroupIcon.Family -> Tint.Mint
        GroupIcon.Photos -> Tint.Teal
    }
