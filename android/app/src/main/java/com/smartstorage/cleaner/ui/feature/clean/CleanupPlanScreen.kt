package com.smartstorage.cleaner.ui.feature.clean

import com.smartstorage.cleaner.media.ReviewKind
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.model.CleanupPlan
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.model.PlanTarget
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.CardRow
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.feature.home.icon
import com.smartstorage.cleaner.ui.feature.home.tint
import com.smartstorage.cleaner.ui.shell.Screen
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** 04 — Cleanup plan: how the chosen target will be reached, with protected memories excluded. */
@Composable
fun CleanupPlanScreen(onOpen: (Screen) -> Unit, onReview: (ReviewKind) -> Unit, onBack: () -> Unit) {
    val state = libraryState()
    val plan = state.cleanupPlan
    val colors = SmartTheme.colors

    ScreenScaffold(onBack = onBack) {
        ScreenHeader("Cleanup Plan", "${plan.estimatedBytes.formattedBytes()} recommended", Modifier.padding(bottom = 8.dp))
        PlanSummaryCard(plan)

        if (plan.items.isEmpty()) {
            SmartCard(style = CardStyle.Info) {
                Text(
                    if (state.isScanning) "Still analyzing your library. Suggestions appear here as soon as the scan finishes."
                    else "Nothing to clean up right now. Your library is in good shape.",
                    style = SmartType.metadata,
                    color = colors.textSecondary,
                )
            }
        }

        plan.items.forEach { item ->
            CardRow(
                icon = item.kind.icon,
                title = item.title,
                tint = item.kind.tint,
                subtitle = item.bytes.formattedBytes(),
                onClick = { item.review?.let(onReview) ?: onOpen(item.target.screen) },
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text("Estimated review time ${plan.reviewTime}", style = SmartType.metadata, color = colors.textSecondary)
        }

        plan.items.firstOrNull()?.let { first ->
            PrimaryButton("Review Items", onClick = { first.review?.let(onReview) ?: onOpen(first.target.screen) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val PlanTarget.screen: Screen
    get() = when (this) {
        PlanTarget.Videos -> Screen.Videos
        PlanTarget.SimilarPhotos -> Screen.SimilarPhotos
        PlanTarget.Screenshots -> Screen.Screenshots
    }

@Composable
private fun PlanSummaryCard(plan: CleanupPlan) {
    val colors = SmartTheme.colors
    SmartCard(style = CardStyle.Hero) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Metric(Icons.Rounded.GpsFixed, Tint.Teal, "Target", plan.targetBytes.formattedBytes(), Modifier.weight(1f))
            VerticalDivider(color = colors.separator)
            Metric(Icons.Rounded.AutoAwesome, Tint.Teal, "Estimated cleanup", plan.estimatedBytes.formattedBytes(), Modifier.weight(1f))
            VerticalDivider(color = colors.separator)
            Metric(Icons.Rounded.VerifiedUser, Tint.Mint, "Protected memories excluded", null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(icon: ImageVector, tint: Tint, title: String, value: String?, modifier: Modifier) {
    val colors = SmartTheme.colors
    Column(
        modifier.fillMaxHeight().padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(tint.background(colors.isDark)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint.foreground(colors.isDark))
        }
        Text(title, style = SmartType.metadata, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (value != null) {
            Text(value, style = SmartType.metric, color = colors.textPrimary, maxLines = 1)
        }
    }
}
