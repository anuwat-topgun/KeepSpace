package com.smartstorage.cleaner.ui.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.ui.components.AdaptiveGrid
import com.smartstorage.cleaner.ui.components.CardRow
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.shell.Screen
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.Tint

private data class LibraryEntry(val screen: Screen, val icon: ImageVector, val tint: Tint, val subtitle: String)

private val entries = listOf(
    LibraryEntry(Screen.SimilarPhotos, Icons.Rounded.Collections, Tint.Coral, "Grouped look-alike shots"),
    LibraryEntry(Screen.Screenshots, Icons.Rounded.CropFree, Tint.Blue, "Understood by content"),
    LibraryEntry(Screen.Videos, Icons.Rounded.Videocam, Tint.Purple, "Large files and recordings"),
    LibraryEntry(Screen.Memories, Icons.Rounded.Favorite, Tint.Coral, "Protected by default"),
    LibraryEntry(Screen.ManualBackup, Icons.Rounded.CloudUpload, Tint.Teal, "Back up to Drive or OneDrive"),
)

/** Library tab root. Not in the mockups — lists the media review flows as entry points. */
@Composable
fun LibraryScreen(onOpen: (Screen) -> Unit) {
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth) {
        ScreenHeader("Library", "Review and organize your media.", Modifier.padding(bottom = 8.dp))
        AdaptiveGrid(entries) { entry ->
            CardRow(
                icon = entry.icon,
                title = entry.screen.title,
                tint = entry.tint,
                subtitle = entry.subtitle,
                onClick = { onOpen(entry.screen) },
            )
        }
    }
}
