package com.smartstorage.cleaner.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Type scale from the design handoff (§2.2). Mirrors `Typography.swift` on iOS. */
object SmartType {
    val screenTitle = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
    val screenSubtitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp)
    val cardHeadline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val metadata = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val metricLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val metric = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val optionTitle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val sectionLabel = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
    val button = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
}

/** Spacing and shape tokens. Mirrors `Metrics` on iOS. */
object SmartMetrics {
    val cardRadius = 24.dp
    val tileRadius = 16.dp
    val cardPadding = 20.dp
    val stackSpacing = 14.dp
    val gutterCompact = 16.dp
    val gutterExpanded = 32.dp

    /** Single-column content never stretches wider than this on tablets. */
    val readableWidth = 680.dp
    /** Grid content (e.g. Home category tiles) may use a wider column. */
    val wideContentWidth = 960.dp
    /** Grids add a column only when each column can stay at least this wide. */
    val minGridColumnWidth = 320.dp
    /** Width at which the layout switches from phone to tablet behaviour (Material "medium"). */
    val tabletBreakpoint = 600.dp
}
