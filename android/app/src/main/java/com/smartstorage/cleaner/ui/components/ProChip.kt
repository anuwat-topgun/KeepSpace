package com.smartstorage.cleaner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.ui.i18n.localized
import com.smartstorage.cleaner.ui.theme.SmartTheme

/** Small "PRO" mark on anything that opens a Pro feature. Never used to hide or blur content (store/PAYWALL_DESIGN.md §3.4). */
@Composable
fun ProChip(modifier: Modifier = Modifier) {
    val colors = SmartTheme.colors
    val label = localized("Pro")
    Text(
        "PRO",
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
        color = colors.accent,
        modifier = modifier.clip(CircleShape).background(colors.icyBlue).padding(horizontal = 7.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = label },
    )
}
