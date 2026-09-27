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
import androidx.compose.material3.Text
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
import com.smartstorage.cleaner.model.LibraryMockData
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
fun SimilarPhotosScreen(onOpenGroup: (String) -> Unit, onBack: () -> Unit) {
    val groups = LibraryMockData.photoGroups
    var selectedId by rememberSaveable { mutableStateOf(groups.first().id) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(420.dp)) {
                    GroupList(groups, selectedId = selectedId, onBack = onBack, onSelect = { selectedId = it })
                }
                VerticalDivider(color = SmartTheme.colors.separator)
                Crossfade(targetState = selectedId, label = "bestShot", modifier = Modifier.weight(1f)) { id ->
                    groups.firstOrNull { it.id == id }?.let { BestShotScreen(it, onBack = null) }
                }
            }
        } else {
            GroupList(groups, selectedId = null, onBack = onBack, onSelect = onOpenGroup)
        }
    }
}

@Composable
private fun GroupList(groups: List<PhotoGroup>, selectedId: String?, onBack: () -> Unit, onSelect: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(PhotoGroupFilter.All) }
    ScreenScaffold(onBack = onBack) {
        ScreenHeader(
            "Similar Photos",
            "${LibraryMockData.SIMILAR_BYTES.formattedBytes()} recoverable · ${LibraryMockData.SIMILAR_GROUPS} groups",
        )
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
                    Text("${group.photoCount} photos · ${group.bytes.formattedBytes()}", style = SmartType.metadata, color = colors.textSecondary)
                    StatusBadge("Recommended keep selected", Icons.Rounded.AutoAwesome, Tint.Teal, compact = true)
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            ThumbnailStrip(group.style, group.photoCount)
        }
    }
}

private val GroupIcon.vector: ImageVector
    get() = when (this) {
        GroupIcon.Beach -> Icons.Rounded.BeachAccess
        GroupIcon.Dinner -> Icons.Rounded.Restaurant
        GroupIcon.Person -> Icons.Rounded.Person
        GroupIcon.Family -> Icons.Rounded.Groups
    }

private val GroupIcon.tint: Tint
    get() = when (this) {
        GroupIcon.Beach -> Tint.Coral
        GroupIcon.Dinner -> Tint.Purple
        GroupIcon.Person -> Tint.Blue
        GroupIcon.Family -> Tint.Mint
    }
