package com.smartstorage.cleaner.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.media.LocalLibraryStore
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint
import kotlinx.coroutines.delay

/** Calm confirmation after a delete or compress. Dismisses itself after a few seconds or on tap. */
@Composable
fun NoticeBanner(modifier: Modifier = Modifier) {
    val store = LocalLibraryStore.current
    val notice = libraryState().notice
    val colors = SmartTheme.colors
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(notice) {
        if (notice != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(6_000)
            store.clearNotice()
        }
    }
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Row(
            Modifier
                .widthIn(max = SmartMetrics.readableWidth)
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .clickable { store.clearNotice() }
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Tint.Mint.foreground(colors.isDark))
            Text(notice.orEmpty(), style = SmartType.metadata, color = colors.textPrimary)
        }
    }
}
