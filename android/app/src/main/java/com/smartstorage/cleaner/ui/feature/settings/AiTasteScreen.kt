package com.smartstorage.cleaner.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import com.smartstorage.cleaner.media.LocalLibraryStore
import com.smartstorage.cleaner.media.ScoreWeights
import com.smartstorage.cleaner.media.TasteProfile
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ListTile
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SecondaryButton
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint
import kotlin.math.roundToInt

// Mirrors ios/SmartStorage/Features/Settings/AITasteView.swift.

/** AI Taste: what Best Shot has learned from the photos kept, with an off switch and a reset. */
@Composable
fun AiTasteScreen(onBack: () -> Unit) {
    val taste = LocalLibraryStore.current.taste
    val state by taste.state.collectAsState()
    val colors = SmartTheme.colors
    var confirmingReset by rememberSaveable { mutableStateOf(false) }
    val decisions = state.profile.decisions
    val progress = when {
        decisions == 0 -> "No choices yet. Keep a photo other than the recommended one in Best Shot and it starts learning."
        decisions >= TasteProfile.FULL_TRUST_AFTER -> "Learned from $decisions choices. Your taste now fully guides Best Shot."
        else -> "Learning from $decisions ${if (decisions == 1) "choice" else "choices"}. Best Shot leans further your way after ${TasteProfile.FULL_TRUST_AFTER}."
    }

    ScreenScaffold(onBack = onBack) {
        ScreenHeader("AI Taste", "Best Shot learns what you like to keep.", Modifier.padding(bottom = 8.dp))

        SmartCard(contentPadding = PaddingValues(16.dp)) {
            ListTile(Icons.Rounded.AutoFixHigh, "Learn from my choices", tint = Tint.Purple, showsChevron = false,
                subtitle = "Uses the photos you keep to fine-tune recommendations.") {
                Switch(
                    checked = state.enabled,
                    onCheckedChange = taste::setEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White),
                )
            }
        }

        SmartCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("What Best Shot looks for", style = SmartType.cardHeadline, color = colors.textPrimary)
                WeightRow("Sharpness", ScoreWeights.Standard.sharpness, state.weights.sharpness)
                WeightRow("Faces", ScoreWeights.Standard.face, state.weights.face)
                WeightRow("Lighting", ScoreWeights.Standard.exposure, state.weights.exposure)
                Text(
                    if (state.enabled) progress else "Switched off. Best Shot uses the standard mix.",
                    style = SmartType.metadata,
                    color = colors.textSecondary,
                )
            }
        }

        SmartCard(style = CardStyle.Info) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Text(
                    "Only three numbers and a count are stored, on this device. Photos, and what's in them, never are. A favorite you set always wins.",
                    style = SmartType.metadata,
                    color = colors.textSecondary,
                )
            }
        }

        if (decisions > 0) SecondaryButton("Reset What I've Taught", onClick = { confirmingReset = true }, modifier = Modifier.fillMaxWidth(), outlined = true)
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Reset your taste?") },
            text = { Text("Best Shot goes back to its standard mix.") },
            confirmButton = { TextButton(onClick = { confirmingReset = false; taste.reset() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text("Cancel") } },
        )
    }
}

/** A bar for how much a factor counts, with the standard mix marked for comparison. */
@Composable
private fun WeightRow(title: String, standard: Double, yours: Double) {
    val colors = SmartTheme.colors
    val percent = (yours * 100).roundToInt()
    Column(
        Modifier.semantics { contentDescription = "$title: $percent percent, standard ${(standard * 100).roundToInt()} percent" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = SmartType.body, color = colors.textPrimary)
            Text("$percent%", style = SmartType.metadata, color = colors.textSecondary)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(colors.icyBlue))
            Box(Modifier.width(maxWidth * yours.toFloat()).height(10.dp).clip(CircleShape).background(colors.accent))
            // Where the standard mix sits.
            Box(Modifier.offset(x = maxWidth * standard.toFloat() - 1.dp).width(2.dp).height(14.dp).background(colors.textSecondary.copy(alpha = 0.6f)))
        }
    }
}
