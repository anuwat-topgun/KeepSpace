package com.smartstorage.cleaner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

enum class CardStyle {
    /** Plain white rounded card. */
    Plain,
    /** Soft icy-blue wash for hero / summary cards. */
    Hero,
    /** Pale info card (privacy notes, estimates). */
    Info,
    /** Selected state: icy wash with an accent outline. */
    Selected,
}

@Composable
fun SmartCard(
    modifier: Modifier = Modifier,
    style: CardStyle = CardStyle.Plain,
    contentPadding: PaddingValues = PaddingValues(SmartMetrics.cardPadding),
    content: @Composable () -> Unit,
) {
    val colors = SmartTheme.colors
    val shape = RoundedCornerShape(SmartMetrics.cardRadius)
    val base = modifier
        .fillMaxWidth()
        .shadow(elevation = 2.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.06f))
        .clip(shape)
    val filled = when (style) {
        CardStyle.Plain -> base.background(colors.surface)
        CardStyle.Hero, CardStyle.Selected -> base.background(colors.heroGradient)
        CardStyle.Info -> base.background(colors.icyBlue.copy(alpha = 0.6f))
    }
    val bordered = if (style == CardStyle.Selected) {
        filled.border(1.5.dp, colors.accent, shape)
    } else {
        filled.border(1.dp, colors.separator.copy(alpha = 0.6f), shape)
    }
    // A Column, not a Box: a card with several children (a tile and its button, a tile and a progress bar) stacks them
    // instead of drawing them on top of each other. A single child lays out exactly as before.
    Column(bordered.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

/** Rounded square holding an icon on a soft tint. */
@Composable
fun IconTile(icon: ImageVector, tint: Tint = Tint.Teal, size: Dp = 52.dp) {
    val dark = SmartTheme.colors.isDark
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(SmartMetrics.tileRadius))
            .background(tint.background(dark)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint.foreground(dark), modifier = Modifier.size(size * 0.46f))
    }
}

/** Uppercase letter-spaced caption ("DEVICE STORAGE"). */
@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = SmartType.sectionLabel, color = SmartTheme.colors.textSecondary)
}

/** Large left-aligned screen title with optional subtitle. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val colors = SmartTheme.colors
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = SmartType.screenTitle, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        if (subtitle != null) {
            Text(subtitle, style = SmartType.screenSubtitle, color = colors.textSecondary)
        }
    }
}

/** Rounded capsule progress bar used for storage usage. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = SmartTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(colors.surfaceMuted),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.accentGradient),
        )
    }
}
