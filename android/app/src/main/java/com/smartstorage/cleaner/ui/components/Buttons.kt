package com.smartstorage.cleaner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** Teal gradient pill — the primary CTA on every screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showsArrow: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = SmartTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(), label = "press")

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .scale(scale)
            .shadow(8.dp, CircleShape, ambientColor = colors.accent.copy(alpha = 0.25f), spotColor = colors.accent.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(colors.accentGradient)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = SmartType.button, color = Color.White)
        if (showsArrow) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** Pale filled pill ("Review All") or outlined pill ("Keep Local Copies"). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    val colors = SmartTheme.colors
    val base = modifier.clip(CircleShape)
    val styled = if (outlined) base.border(1.5.dp, colors.accent, CircleShape) else base.background(colors.icyBlue)
    Row(
        modifier = styled
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = SmartType.button, color = colors.accent)
    }
}

/** Small inline pill action inside rows ("Compress", "Review"). [isProLocked] adds a PRO mark inside the pill. */
@Composable
fun InlinePillButton(text: String, onClick: () -> Unit, tint: Tint = Tint.Teal, isProLocked: Boolean = false) {
    val dark = SmartTheme.colors.isDark
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.background(dark))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = if (isProLocked) 10.dp else 16.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = tint.foreground(dark),
            maxLines = 1,
            softWrap = false,
        )
        if (isProLocked) ProChip()
    }
}
