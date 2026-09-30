package com.smartstorage.cleaner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.Tint

/** Soft capsule badge: "Protected", "Connected", "Verified in Google Drive". */
@Composable
fun StatusBadge(
    text: String,
    icon: ImageVector? = null,
    tint: Tint = Tint.Teal,
    /** Smaller single-line variant for dense cards. */
    compact: Boolean = false,
) {
    val dark = SmartTheme.colors.isDark
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.background(dark))
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 5.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint.foreground(dark), modifier = Modifier.size(if (compact) 14.dp else 16.dp))
        }
        Text(
            text,
            style = TextStyle(fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Medium),
            color = tint.foreground(dark),
            maxLines = 1,
        )
    }
}

@Composable
fun ProtectedBadge() = StatusBadge("Protected", Icons.Rounded.Lock, Tint.Teal)

@Composable
fun ConnectedBadge() = StatusBadge("Connected", Icons.Rounded.CheckCircle, Tint.Mint)

@Composable
fun VerifiedBadge(provider: String) = StatusBadge("Verified in $provider", Icons.Rounded.CloudDone, Tint.Blue)

/** Horizontal filter chips ("All / Recent / Reviewed"). */
@Composable
fun <T> ChipPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = SmartTheme.colors
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val chip = Modifier
                .clip(CircleShape)
                .let { if (isSelected) it.background(colors.accentGradient) else it.background(colors.surfaceMuted) }
                .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                .padding(horizontal = 22.dp, vertical = 10.dp)
            Text(
                text = label(option),
                style = TextStyle(fontSize = 17.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isSelected) Color.White else colors.textPrimary,
                modifier = chip,
            )
        }
    }
}
