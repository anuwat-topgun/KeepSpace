package com.smartstorage.cleaner.ui.feature.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** Stand-in for screens that are routed but not built yet. */
@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    ScreenScaffold(onBack = onBack) {
        ScreenHeader(title, "This screen is coming soon.")
        SmartCard(style = CardStyle.Info) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconTile(Icons.Rounded.Build, Tint.Gray)
                Text(
                    "Routed from the app shell; implementation lands in a later step.",
                    style = SmartType.metadata,
                    color = SmartTheme.colors.textSecondary,
                )
            }
        }
    }
}
