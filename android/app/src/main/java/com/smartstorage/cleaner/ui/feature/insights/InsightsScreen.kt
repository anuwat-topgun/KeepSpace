package com.smartstorage.cleaner.ui.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SectionLabel
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType

/** 07 — Insights. Forecast chart and weekly summary land in step 4. */
@Composable
fun InsightsScreen() {
    ScreenScaffold {
        ScreenHeader("Insights", "Understand how your storage changes over time.", Modifier.padding(bottom = 8.dp))
        SmartCard(style = CardStyle.Hero) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Storage forecast")
                Text("Coming soon", style = SmartType.metric, color = SmartTheme.colors.textPrimary)
            }
        }
    }
}
