package com.smartstorage.cleaner.ui.feature.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.monetization.LocalMonetization
import com.smartstorage.cleaner.monetization.LocalPresentPaywall
import com.smartstorage.cleaner.monetization.ProFeature
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ProChip
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.i18n.Text
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

private data class ExplainerContent(val icon: ImageVector, val tint: Tint, val title: String, val what: String, val free: String)

private fun explainer(feature: ProFeature) = when (feature) {
    ProFeature.VideoCompression -> ExplainerContent(Icons.Rounded.Movie, Tint.Purple, "Compress Videos",
        "Shrinks a big video on your device and keeps the quality. You review the result before the original is removed.",
        "On Free you can still see which videos take the most space and review them for deletion.")
    ProFeature.ReceiptFiling -> ExplainerContent(Icons.Rounded.Receipt, Tint.Mint, "Receipt Filing",
        "Finds receipts in your screenshots and photos, reads the merchant, date and amount on this device, and shows where each one would be filed.",
        "On Free your receipts stay where they are, and you can still review old screenshots.")
    ProFeature.AiTaste -> ExplainerContent(Icons.Rounded.AutoFixHigh, Tint.Amber, "AI Taste",
        "Best Shot learns from the photos you keep and starts recommending the kind of shot you like. It learns on this device and nothing leaves it.",
        "On Free, Best Shot still picks the best photo in every group.")
    else -> ExplainerContent(Icons.Rounded.AutoAwesome, Tint.Teal, "KeepSpace Pro",
        "Unlimited cleanup, video compression, smart screenshots, Receipt Filing, AI Taste, and unlimited backup and rules.",
        "Scanning, the Safety Score and review stay free.")
}

/** The explainer's cards and button, for use in a screen or a sheet. */
@Composable
private fun ExplainerBody(feature: ProFeature, onUnlock: () -> Unit) {
    val colors = SmartTheme.colors
    val item = explainer(feature)
    SmartCard(style = CardStyle.Hero) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(item.icon, item.tint)
                ProChip()
            }
            Text(item.what, style = SmartType.body, color = colors.textPrimary)
        }
    }
    SmartCard(style = CardStyle.Info) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.CheckCircleOutline, null, tint = colors.accent)
            Text(item.free, style = SmartType.metadata, color = colors.textSecondary)
        }
    }
    PrimaryButton("Unlock KeepSpace Pro", onClick = onUnlock, showsArrow = false, modifier = Modifier.fillMaxWidth())
}

/** One-screen explanation shown where a Pro feature is tapped on Free: what it does, what Free still has, and a way to unlock. */
@Composable
fun ProExplainerScreen(feature: ProFeature, onBack: () -> Unit) {
    val presentPaywall = LocalPresentPaywall.current
    ScreenScaffold(onBack = onBack) {
        ScreenHeader(explainer(feature).title, modifier = Modifier.padding(bottom = 8.dp))
        ExplainerBody(feature) { presentPaywall(feature) }
    }
}

/** The same explanation as a bottom sheet (used by Compress, which opens from a row). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProExplainerSheet(feature: ProFeature, onDismiss: () -> Unit) {
    val presentPaywall = LocalPresentPaywall.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SmartTheme.colors.background) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 12.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(explainer(feature).title, style = SmartType.metric, color = SmartTheme.colors.textPrimary)
            ExplainerBody(feature) { onDismiss(); presentPaywall(feature) }
        }
    }
}

/** Shows [content] to Pro, and the explainer to everyone else. Pro features stay visible; only what's behind them is locked. */
@Composable
fun ProGate(feature: ProFeature, onBack: () -> Unit, content: @Composable () -> Unit) {
    val monetization = LocalMonetization.current
    val status by monetization.status.collectAsState()
    if (status.allows(feature)) content() else ProExplainerScreen(feature, onBack)
}
