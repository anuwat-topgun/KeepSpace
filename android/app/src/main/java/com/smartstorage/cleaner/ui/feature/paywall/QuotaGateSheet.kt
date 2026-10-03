package com.smartstorage.cleaner.ui.feature.paywall

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.monetization.QuotaGateKind
import com.smartstorage.cleaner.monetization.QuotaGatePrompt
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.SecondaryButton
import com.smartstorage.cleaner.ui.i18n.Text
import com.smartstorage.cleaner.ui.i18n.localizedFormat
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/**
 * Bottom sheet shown when a deletion is bigger than what's left of this month's free cleanup
 * (store/PAYWALL_DESIGN.md §3.2). It is an offer, never a wall: a smaller delete is always possible while allowance remains.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaGateSheet(
    prompt: QuotaGatePrompt,
    onUnlock: () -> Unit,
    onDeletePartial: (List<String>) -> Unit,
    onNotNow: () -> Unit,
) {
    val colors = SmartTheme.colors
    val context = LocalContext.current
    val resetText = DateUtils.formatDateTime(context, prompt.resetDate, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)
    val message = when (prompt.kind) {
        QuotaGateKind.Exhausted -> localizedFormat("Free includes %@ of cleanup each month. It resets on %@.", prompt.limit.formattedBytes(), resetText)
        QuotaGateKind.NoneFit -> com.smartstorage.cleaner.ui.i18n.localized("None of the selected items fit in what's left. Select fewer or smaller items, or unlock Pro.")
        is QuotaGateKind.Partial -> localizedFormat("Your selection is %@. You can free %@ more on Free.", prompt.selectionBytes.formattedBytes(), prompt.remaining.formattedBytes())
    }
    val fraction = if (prompt.limit > 0) (prompt.used.toDouble() / prompt.limit).coerceIn(0.0, 1.0).toFloat() else 0f
    val meter = localizedFormat("%@ of %@ used this month · Resets %@", prompt.used.formattedBytes(), prompt.limit.formattedBytes(), resetText)

    ModalBottomSheet(
        onDismissRequest = onNotNow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (prompt.kind == QuotaGateKind.Exhausted) "You've used this month's free cleanup" else "You're close to this month's free limit",
                        style = SmartType.metric, color = colors.textPrimary,
                    )
                    Text(message, style = SmartType.body, color = colors.textSecondary)
                }

                Column(Modifier.clearAndSetSemantics { contentDescription = meter }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(colors.surfaceMuted)) {
                        Box(Modifier.fillMaxWidth(maxOf(fraction, 0.02f)).height(8.dp).clip(CircleShape).background(Tint.Amber.foreground(colors.isDark)))
                    }
                    Text(meter, style = SmartType.metadata, color = colors.textSecondary)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Unlock Unlimited Cleanup", onClick = onUnlock, showsArrow = false, modifier = Modifier.fillMaxWidth())
                    val ids = prompt.partialIds
                    val bytes = prompt.partialBytes
                    if (ids != null && bytes != null) {
                        SecondaryButton(localizedFormat("Delete %@ (safest first)", bytes.formattedBytes()), onClick = { onDeletePartial(ids) }, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)) {
                        Text("Not now", style = SmartType.body, color = colors.textSecondary)
                    }
                }

                Text("Nothing is deleted without your confirmation.", style = SmartType.metadata, color = colors.textSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
