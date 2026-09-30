package com.smartstorage.cleaner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/**
 * Rounded list tile: icon tile + title + subtitle + trailing accessory (chevron by default).
 * Used for cleanup categories, settings rows, screenshot categories, rules.
 */
@Composable
fun ListTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    tint: Tint = Tint.Teal,
    subtitle: String? = null,
    showsChevron: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = SmartTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = SmartType.cardHeadline, color = colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = SmartType.metadata, color = colors.textSecondary)
            }
        }
        trailing()
        if (showsChevron) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
        }
    }
}

/** A [ListTile] wrapped in its own plain card — the dominant row pattern in the mockups. */
@Composable
fun CardRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    tint: Tint = Tint.Teal,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    SmartCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        ListTile(icon = icon, title = title, tint = tint, subtitle = subtitle, modifier = clickable)
    }
}

/** Form row with a switch ("Upload over Wi-Fi only"). */
@Composable
fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: Tint = Tint.Teal,
    subtitle: String? = null,
) {
    val colors = SmartTheme.colors
    ListTile(
        icon = icon,
        title = title,
        tint = tint,
        subtitle = subtitle,
        showsChevron = false,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White),
        )
    }
}
