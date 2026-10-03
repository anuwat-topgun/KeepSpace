package com.smartstorage.cleaner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme

/**
 * Standard scrolling screen: warm background, width-based gutters and a capped content width
 * so cards never stretch edge-to-edge on tablets. Mirrors `ScreenScaffold` on iOS.
 */
@Composable
fun ScreenScaffold(
    maxWidth: Dp = SmartMetrics.readableWidth,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SmartTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val gutter = if (this.maxWidth >= SmartMetrics.tabletBreakpoint) SmartMetrics.gutterExpanded else SmartMetrics.gutterCompact
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth + gutter * 2)
                    .fillMaxWidth()
                    .padding(top = insets.calculateTopPadding() + 8.dp, bottom = 32.dp)
                    .padding(horizontal = gutter),
                verticalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = com.smartstorage.cleaner.ui.i18n.localized("Back"), tint = colors.textPrimary)
                    }
                }
                content()
            }
        }
    }
}

/**
 * Grid that shows one column on phones; on tablets it adds columns only when each can stay
 * at least [minColumnWidth] wide. Non-lazy on purpose: it lives inside [ScreenScaffold]'s scroll.
 */
@Composable
fun <T> AdaptiveGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = SmartMetrics.minGridColumnWidth,
    spacing: Dp = SmartMetrics.stackSpacing,
    itemContent: @Composable (T) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = ((maxWidth + spacing) / (minColumnWidth + spacing)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    rowItems.forEach { item ->
                        Box(Modifier.weight(1f)) { itemContent(item) }
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
