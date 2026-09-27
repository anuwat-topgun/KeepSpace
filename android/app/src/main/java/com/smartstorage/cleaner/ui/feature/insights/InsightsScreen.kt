package com.smartstorage.cleaner.ui.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.model.ForecastPoint
import com.smartstorage.cleaner.model.LibraryMockData
import com.smartstorage.cleaner.model.StorageForecast
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.ListTile
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SectionLabel
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartColors
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** 07 — Insights: storage forecast and this week's changes. */
@Composable
fun InsightsScreen(onOpenPhotos: () -> Unit, onOpenVideos: () -> Unit, onSmartClean: () -> Unit) {
    val forecast = LibraryMockData.forecast
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth) {
        ScreenHeader("Insights", "Understand how your storage changes over time.", Modifier.padding(bottom = 8.dp))
        BoxWithConstraints {
            if (maxWidth >= 900.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing)) {
                    Box(Modifier.weight(1f)) { ForecastCard(forecast) }
                    Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing)) {
                        WeeklyCard(forecast, onOpenPhotos, onOpenVideos, onSmartClean)
                        SmartCleanCard(onSmartClean)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing)) {
                    ForecastCard(forecast)
                    WeeklyCard(forecast, onOpenPhotos, onOpenVideos, onSmartClean)
                    SmartCleanCard(onSmartClean)
                }
            }
        }
    }
}

@Composable
private fun ForecastCard(forecast: StorageForecast) {
    val colors = SmartTheme.colors
    SmartCard(style = CardStyle.Hero) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Storage forecast")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(forecast.remainingBytes.formattedBytes(), style = SmartType.metricLarge, color = colors.textPrimary)
                Text("remaining", style = TextStyle(fontSize = 22.sp), color = colors.textPrimary.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
            }
            Text("Estimated full in ${forecast.daysUntilFull} days", style = SmartType.body, color = colors.textSecondary)
            ForecastChart(
                forecast,
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(top = 8.dp)
                    .semantics {
                        contentDescription = "Storage forecast: ${forecast.remainingBytes.formattedBytes()} remaining, full in about ${forecast.daysUntilFull} days"
                    },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(dashed = false, text = "Used")
                Legend(dashed = true, text = "Forecast")
            }
        }
    }
}

private const val MIN_WEEK = -4f
private const val MAX_WEEK = 7f
private const val MIN_GB = 220f
private const val MAX_GB = 262f
private val yTicks = listOf(224, 232, 240, 248, 256)
private val xTicks = listOf(-4, -2, 0, 2, 4, 6)

/** Hand-drawn line chart (no chart dependency): history solid with area fill, projection dashed, capacity rule. */
@Composable
private fun ForecastChart(forecast: StorageForecast, modifier: Modifier) {
    val colors = SmartTheme.colors
    val coral = Tint.Coral.foreground(colors.isDark)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = colors.textSecondary)

    Canvas(modifier) {
        val leftAxis = 52.dp.toPx()
        val bottomAxis = 22.dp.toPx()
        val plotW = size.width - leftAxis
        val plotH = size.height - bottomAxis
        fun x(week: Float) = leftAxis + (week - MIN_WEEK) / (MAX_WEEK - MIN_WEEK) * plotW
        fun y(gb: Float) = plotH - (gb - MIN_GB) / (MAX_GB - MIN_GB) * plotH
        fun path(points: List<ForecastPoint>) = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.week), y(p.usedGB)) else lineTo(x(p.week), y(p.usedGB)) }
        }

        yTicks.forEach { gb ->
            drawLine(colors.separator, Offset(leftAxis, y(gb.toFloat())), Offset(size.width, y(gb.toFloat())), strokeWidth = 1f)
            drawLabel(measurer, "$gb GB", labelStyle, Offset(0f, y(gb.toFloat()) - 8.dp.toPx()))
        }
        xTicks.forEach { week ->
            val label = when {
                week == 0 -> "Today"
                week < 0 -> "${-week}w ago"
                else -> "+${week}w"
            }
            val layout = measurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(x(week.toFloat()) - layout.size.width / 2f, plotH + 6.dp.toPx()))
        }

        // Area under history.
        val history = forecast.history
        val area = path(history).apply {
            lineTo(x(history.last().week), plotH)
            lineTo(x(history.first().week), plotH)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.25f), colors.accent.copy(alpha = 0.02f))))

        val stroke = 2.5.dp.toPx()
        drawPath(path(history), colors.accent, style = Stroke(stroke, cap = StrokeCap.Round))
        drawPath(
            path(forecast.projection),
            colors.accent,
            style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
        history.forEach { p ->
            drawCircle(colors.accent, radius = if (p.week == 0f) 5.dp.toPx() else 3.dp.toPx(), center = Offset(x(p.week), y(p.usedGB)))
        }

        // Capacity rule.
        val capY = y(forecast.capacityGB)
        drawLine(
            coral.copy(alpha = 0.7f),
            Offset(leftAxis, capY),
            Offset(size.width, capY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        drawLabel(
            measurer,
            "Full · ${forecast.capacityGB.toInt()} GB",
            labelStyle.copy(color = coral, fontWeight = FontWeight.SemiBold),
            Offset(leftAxis + 4.dp.toPx(), capY - 16.dp.toPx()),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    topLeft: Offset,
) {
    drawText(measurer.measure(text, style), topLeft = topLeft)
}

@Composable
private fun Legend(dashed: Boolean, text: String) {
    val colors = SmartTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(18.dp).height(3.dp)) {
            drawLine(
                colors.accent,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.5.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(text, style = TextStyle(fontSize = 12.sp), color = colors.textSecondary)
    }
}

@Composable
private fun WeeklyCard(forecast: StorageForecast, onOpenPhotos: () -> Unit, onOpenVideos: () -> Unit, onSmartClean: () -> Unit) {
    val colors = SmartTheme.colors
    SmartCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("This week")
            WeeklyRow(Icons.Rounded.Collections, Tint.Coral, "${forecast.photosAddedThisWeek}", "photos added", onOpenPhotos, colors)
            HorizontalDivider(color = colors.separator)
            WeeklyRow(Icons.Rounded.Videocam, Tint.Purple, "${forecast.videosAddedThisWeek}", "videos added", onOpenVideos, colors)
            HorizontalDivider(color = colors.separator)
            ListTile(
                icon = Icons.Rounded.AutoAwesome,
                title = forecast.potentialCleanupBytes.formattedBytes(),
                subtitle = "Potential cleanup",
                modifier = Modifier.clickable(role = Role.Button, onClick = onSmartClean),
            )
        }
    }
}

@Composable
private fun WeeklyRow(icon: ImageVector, tint: Tint, value: String, label: String, onClick: () -> Unit, colors: SmartColors) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = SmartType.metric, color = colors.textPrimary)
            Text(label, style = SmartType.body, color = colors.textSecondary, modifier = Modifier.padding(bottom = 2.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
    }
}

@Composable
private fun SmartCleanCard(onClick: () -> Unit) {
    val colors = SmartTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SmartMetrics.cardRadius))
            .background(colors.accentGradient)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text("Run Weekly Smart Clean", style = SmartType.cardHeadline.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text("Find and remove unnecessary files.", style = SmartType.metadata, color = Color.White.copy(alpha = 0.9f))
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White)
    }
}
