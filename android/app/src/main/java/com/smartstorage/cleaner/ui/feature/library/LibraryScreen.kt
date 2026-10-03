package com.smartstorage.cleaner.ui.feature.library

import com.smartstorage.cleaner.ui.i18n.localized
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.MediaController
import android.widget.VideoView
import com.smartstorage.cleaner.cloud.AssetCloudState
import com.smartstorage.cleaner.cloud.CloudUploadStatus
import com.smartstorage.cleaner.cloud.LocalCloudStore
import com.smartstorage.cleaner.media.MediaItem
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.model.ThumbnailStyle
import com.smartstorage.cleaner.ui.components.AssetImage
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.components.StatusBadge
import com.smartstorage.cleaner.ui.i18n.Text
import com.smartstorage.cleaner.ui.shell.Screen
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

private enum class LibraryFilter(val label: String) {
    All("All"), Photos("Photos"), Videos("Videos"), BackedUp("Backed up")
}

/** Photos-style library browser with per-asset cloud backup state. */
@Composable
fun LibraryScreen(onOpen: (Screen) -> Unit) {
    val library = libraryState()
    val cloud by LocalCloudStore.current.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val statuses = library.mediaItems.associate { it.id to AssetCloudState.resolve(it, cloud.uploads) }
    val visible = library.mediaItems.filter { item ->
        when (filter) {
            LibraryFilter.All -> true
            LibraryFilter.Photos -> !item.isVideo
            LibraryFilter.Videos -> item.isVideo
            LibraryFilter.BackedUp -> statuses[item.id]?.isBackedUp == true
        }
    }
    val colors = SmartTheme.colors

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val gutter = if (maxWidth >= SmartMetrics.tabletBreakpoint) SmartMetrics.gutterExpanded else SmartMetrics.gutterCompact
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = SmartMetrics.wideContentWidth + gutter * 2)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = safe.calculateTopPadding() + 8.dp,
                bottom = safe.calculateBottomPadding() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ScreenHeader("Library", "Your full photo library, with cloud backup status.", Modifier.padding(bottom = 8.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge("Backed up", Icons.Rounded.CloudDone, Tint.Mint, compact = true)
                    Text("Verified cloud copies", style = SmartType.metadata, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onOpen(Screen.CloudOverview) }) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = com.smartstorage.cleaner.ui.i18n.localized("Cloud backup"), tint = colors.accent)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = com.smartstorage.cleaner.ui.i18n.localized("Organize"), tint = colors.textPrimary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            listOf(
                                Screen.SimilarPhotos,
                                Screen.Screenshots,
                                Screen.Videos,
                                Screen.Memories,
                                Screen.Receipts,
                                Screen.StorageRules,
                                Screen.ManualBackup,
                            ).forEach { screen ->
                                DropdownMenuItem(
                                    text = { Text(screen.title) },
                                    onClick = { menuOpen = false; onOpen(screen) },
                                )
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryFilterPicker(filter, onSelect = { filter = it }, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SmartCard(style = CardStyle.Info) {
                        Text(if (library.isScanning) "Reading your library…" else "No media found.", style = SmartType.metadata, color = colors.textSecondary)
                    }
                }
            } else {
                items(visible, key = { it.id }) { item ->
                    LibraryTile(item, statuses[item.id] ?: AssetCloudState(), onClick = { selected = item.id })
                }
            }
        }
    }

    if (selected != null) {
        // Keep the same ordered list that was visible when the tile was tapped. Background scanning
        // may update/reorder the library, but must never move the preview to a neighbouring asset.
        val previewItems = remember(selected) { visible.toList() }
        val selectedItem = previewItems.firstOrNull { it.id == selected }
        if (selectedItem != null) {
            LibraryPreview(
                items = previewItems,
                initialId = selectedItem.id,
                statuses = statuses,
                onDismiss = { selected = null },
            )
        }
    }
}

/** Four equal-width controls keep Backed up visible and tappable on compact phones. */
@Composable
private fun LibraryFilterPicker(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit, modifier: Modifier = Modifier) {
    val colors = SmartTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LibraryFilter.entries.forEach { option ->
            val isSelected = option == selected
            Text(
                text = option.label,
                style = SmartType.metadata.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isSelected) Color.White else colors.textPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .let { if (isSelected) it.background(colors.accentGradient) else it.background(colors.surfaceMuted) }
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun LibraryTile(item: MediaItem, state: AssetCloudState, onClick: () -> Unit) {
    val photoWord = localized("Photo")
    val stateTitle = localized(state.title)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${item.fileName ?: photoWord}, $stateTitle" },
    ) {
        AssetImage(item.id, item.fallbackStyle, Modifier.fillMaxSize(), cornerRadius = 2.dp)
        CloudStateIcon(state, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        if (item.isVideo) {
            Icon(
                Icons.Rounded.Videocam,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(17.dp),
            )
        }
    }
}

@Composable
private fun CloudStateIcon(state: AssetCloudState, modifier: Modifier = Modifier) {
    val (icon, color) = when (state.status) {
        CloudUploadStatus.BackedUp -> Icons.Rounded.Check to Tint.Mint.foreground(SmartTheme.colors.isDark)
        CloudUploadStatus.Waiting -> Icons.Rounded.Schedule to Tint.Blue.foreground(SmartTheme.colors.isDark)
        CloudUploadStatus.Uploading, CloudUploadStatus.Verifying -> Icons.Rounded.CloudUpload to Tint.Blue.foreground(SmartTheme.colors.isDark)
        CloudUploadStatus.Failed -> Icons.Rounded.Error to Tint.Coral.foreground(SmartTheme.colors.isDark)
        CloudUploadStatus.Cancelled, null -> Icons.Rounded.CloudOff to Color.Black.copy(alpha = 0.58f)
    }
    Box(
        modifier
            .size(25.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = state.title },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun LibraryPreview(
    items: List<MediaItem>,
    initialId: String,
    statuses: Map<String, AssetCloudState>,
    onDismiss: () -> Unit,
) {
    val initialPage = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initialPage) { items.size }
    var immersive by rememberSaveable(initialId) { mutableStateOf(false) }
    var verticalDrag by remember { mutableStateOf(0f) }
    var zoomedPhotoId by remember { mutableStateOf<String?>(null) }
    val item = items.getOrNull(pager.currentPage)
    val state = item?.let { statuses[it.id] } ?: AssetCloudState()
    LaunchedEffect(pager.currentPage) {
        immersive = false
        zoomedPhotoId = null
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { verticalDrag = 0f },
                        onVerticalDrag = { _, amount -> verticalDrag += amount },
                        onDragCancel = { verticalDrag = 0f },
                        onDragEnd = {
                            if (verticalDrag > 120f) onDismiss()
                            verticalDrag = 0f
                        },
                    )
                },
        ) {
            HorizontalPager(
                state = pager,
                key = { items[it].id },
                userScrollEnabled = zoomedPhotoId == null,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageItem = items[page]
                if (pageItem.isVideo) {
                    PhotoLibraryVideoPlayer(
                        pageItem.id,
                        active = page == pager.currentPage,
                        onToggleFullscreen = { immersive = !immersive },
                    )
                } else {
                    ZoomableLibraryPhoto(
                        item = pageItem,
                        active = page == pager.currentPage,
                        onZoomChanged = { zoomed ->
                            if (zoomed) zoomedPhotoId = pageItem.id
                            else if (zoomedPhotoId == pageItem.id) zoomedPhotoId = null
                        },
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (immersive) Color.Transparent else Color.Black.copy(alpha = 0.45f))
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!immersive) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = com.smartstorage.cleaner.ui.i18n.localized("Close"), tint = Color.White)
                    }
                }
                Box(Modifier.weight(1f))
                if (item?.isVideo == true) {
                    IconButton(
                        onClick = { immersive = !immersive },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(
                            if (immersive) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (immersive) "Exit full screen" else "Full screen",
                            tint = Color.White,
                        )
                    }
                }
                if (!immersive) {
                    StatusBadge(state.title, Icons.Rounded.CloudDone, if (state.isBackedUp) Tint.Mint else Tint.Gray, compact = true)
                }
            }
            if (items.isNotEmpty() && !immersive) {
                Text(
                    "${pager.currentPage + 1} / ${items.size}",
                    color = Color.White,
                    style = SmartType.metadata,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(WindowInsets.safeDrawing.asPaddingValues())
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** Double-tap and multi-touch zoom for photos; one-finger paging stays enabled at 1x. */
@Composable
private fun ZoomableLibraryPhoto(item: MediaItem, active: Boolean, onZoomChanged: (Boolean) -> Unit) {
    var zoom by remember(item.id) { mutableStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var size by remember(item.id) { mutableStateOf(IntSize.Zero) }

    fun applyZoom(nextZoom: Float, pan: Offset = Offset.Zero) {
        zoom = nextZoom.coerceIn(1f, 5f)
        if (zoom == 1f) {
            offset = Offset.Zero
        } else {
            val maxX = size.width * (zoom - 1f) / 2f
            val maxY = size.height * (zoom - 1f) / 2f
            offset = Offset(
                (offset.x + pan.x).coerceIn(-maxX, maxX),
                (offset.y + pan.y).coerceIn(-maxY, maxY),
            )
        }
        onZoomChanged(zoom > 1f)
    }

    LaunchedEffect(active) {
        if (!active) applyZoom(1f)
    }

    AssetImage(
        item.id,
        item.fallbackStyle,
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        // At 1x, a single finger belongs to HorizontalPager. Two fingers start
                        // zooming; once zoomed, one finger pans the image instead of paging.
                        if (pressed >= 2 || zoom > 1f) {
                            applyZoom(zoom * event.calculateZoom(), event.calculatePan())
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onDoubleTap = {
                        offset = Offset.Zero
                        applyZoom(if (zoom > 1f) 1f else 2.5f)
                    },
                )
            },
        cornerRadius = 0.dp,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun PhotoLibraryVideoPlayer(uri: String, active: Boolean, onToggleFullscreen: () -> Unit) {
    var videoView by remember(uri) { mutableStateOf<VideoView?>(null) }
    var playing by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (!active) {
            videoView?.pause()
            playing = false
        }
    }
    DisposableEffect(uri) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(uri) { detectTapGestures(onDoubleTap = { onToggleFullscreen() }) },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(android.net.Uri.parse(uri))
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setOnPreparedListener { seekTo(1) }
                    setOnCompletionListener { playing = false }
                    videoView = this
                }
            },
            update = { videoView = it },
            modifier = Modifier.fillMaxSize(),
        )
        if (!playing) {
            IconButton(
                onClick = { videoView?.start(); playing = true },
                modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = com.smartstorage.cleaner.ui.i18n.localized("Play"),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

private val MediaItem.fallbackStyle: ThumbnailStyle
    get() = when (kind) {
        MediaItem.Kind.Screenshot, MediaItem.Kind.ScreenRecording -> ThumbnailStyle.Screen
        MediaItem.Kind.Video -> ThumbnailStyle.Mountain
        MediaItem.Kind.Photo -> ThumbnailStyle.Sunset
    }
