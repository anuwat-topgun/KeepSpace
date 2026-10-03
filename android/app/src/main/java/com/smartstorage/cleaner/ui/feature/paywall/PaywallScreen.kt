package com.smartstorage.cleaner.ui.feature.paywall

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.monetization.LocalMonetization
import com.smartstorage.cleaner.monetization.LocalProBilling
import com.smartstorage.cleaner.monetization.PaywallCta
import com.smartstorage.cleaner.monetization.PaywallFinePrint
import com.smartstorage.cleaner.monetization.PaywallModel
import com.smartstorage.cleaner.monetization.ProBenefit
import com.smartstorage.cleaner.monetization.ProFeature
import com.smartstorage.cleaner.monetization.ProOffer
import com.smartstorage.cleaner.monetization.ProProduct
import com.smartstorage.cleaner.monetization.ProStatus
import com.smartstorage.cleaner.monetization.PurchaseOutcome
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.InlinePillButton
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.i18n.Text
import com.smartstorage.cleaner.ui.i18n.localized
import com.smartstorage.cleaner.ui.i18n.localizedFormat
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://github.com/anuwat-topgun/KeepSpace/blob/main/legal/privacy-policy.md"
private const val TERMS_URL = "https://github.com/anuwat-topgun/KeepSpace/blob/main/legal/terms-of-use.md"

private enum class PaywallMessage { Pending, Failed, RestoredNothing, RestoreFailed }

/**
 * KeepSpace Pro paywall (store/PAYWALL_DESIGN.md §3.1). Full screen; the primary button and the disclosure under it follow
 * the selected plan. Prices, trial length and eligibility all come from Play.
 */
@Composable
fun PaywallScreen(focus: ProFeature?, onDismiss: () -> Unit) {
    val colors = SmartTheme.colors
    val monetization = LocalMonetization.current
    val billing = LocalProBilling.current
    val offers by billing.offers.collectAsState()
    val offersFailed by billing.offersFailed.collectAsState()
    val status by monetization.status.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selected by remember { mutableStateOf<ProProduct?>(null) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<PaywallMessage?>(null) }
    val scroll = rememberScrollState()

    val selectedOffer = offers.firstOrNull { it.product == selected }
        ?: offers.firstOrNull { it.product == PaywallModel.defaultSelection(offers) }

    BackHandler(onBack = onDismiss)
    // Success: back to where the person was; the gated action is offered again, not run.
    LaunchedEffect(status) { if (status.isPro) onDismiss() }

    fun openUrl(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val gutter = if (maxWidth >= SmartMetrics.tabletBreakpoint) SmartMetrics.gutterExpanded else SmartMetrics.gutterCompact
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = localized("Close"), tint = colors.accent)
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = !working,
                    onClick = {
                        scope.launch {
                            working = true
                            message = null
                            val reachable = billing.restore()
                            if (!reachable) message = PaywallMessage.RestoreFailed
                            else if (!monetization.isPro) message = PaywallMessage.RestoredNothing
                            working = false
                        }
                    },
                ) { Text("Restore", style = SmartType.body.copy(fontWeight = FontWeight.Medium), color = colors.accent) }
            }

            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 560.dp + gutter * 2).fillMaxWidth().padding(horizontal = gutter, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Header()
                    Plans(
                        offers = offers, failed = offersFailed, selected = selectedOffer?.product,
                        onSelect = { selected = it; message = null }, onRetry = { scope.launch { billing.loadOffers() } },
                    )
                    Benefits(focus = focus?.let(ProBenefit::of))
                    when (message) {
                        PaywallMessage.Pending -> Note("Waiting for approval", "Pro unlocks by itself once the purchase is approved.")
                        PaywallMessage.Failed -> Note("The purchase didn't go through", "Please try again.")
                        PaywallMessage.RestoredNothing -> Note("No active Pro purchase found", "Restore only works for purchases made with this store account.")
                        PaywallMessage.RestoreFailed -> Note("Can't reach the store", "Check your connection and try again.")
                        null -> Unit
                    }
                }
            }

            // Pinned footer: content above scrolls, so large text never pushes the button off screen.
            Column(
                Modifier.fillMaxWidth().background(colors.surface).padding(bottom = insets.calculateBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp + gutter * 2).fillMaxWidth().padding(horizontal = gutter).padding(top = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PrimaryButton(
                            text = if (working) "" else ctaTitle(selectedOffer),
                            enabled = selectedOffer != null && !working,
                            showsArrow = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val offer = selectedOffer ?: return@PrimaryButton
                                val activity = context.findActivity() ?: return@PrimaryButton
                                working = true
                                message = null
                                billing.purchase(activity, offer.product) { outcome ->
                                    working = false
                                    when (outcome) {
                                        PurchaseOutcome.Success, PurchaseOutcome.Cancelled -> Unit // Pro flips and the screen dismisses; cancelled returns to the picker
                                        PurchaseOutcome.Pending -> message = PaywallMessage.Pending
                                        PurchaseOutcome.Failed -> message = PaywallMessage.Failed
                                    }
                                }
                            },
                        )
                        if (working) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.height(24.dp))
                    }
                    selectedOffer?.let {
                        Text(finePrint(it), style = SmartType.metadata.copy(fontSize = 13.sp), color = colors.textSecondary, textAlign = TextAlign.Center)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("Terms of Use", style = SmartType.metadata.copy(fontWeight = FontWeight.Medium), color = colors.accent,
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable(role = Role.Button) { openUrl(TERMS_URL) }.padding(vertical = 10.dp))
                        Text("Privacy Policy", style = SmartType.metadata.copy(fontWeight = FontWeight.Medium), color = colors.accent,
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable(role = Role.Button) { openUrl(PRIVACY_URL) }.padding(vertical = 10.dp))
                    }
                    Text(
                        "Your photos stay yours. Analysis stays on your device, and backups go only where you choose.",
                        style = SmartType.metadata.copy(fontSize = 12.sp), color = colors.textSecondary, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ctaTitle(offer: ProOffer?): String {
    if (offer == null) return localized("Continue")
    return when (val cta = PaywallModel.cta(offer)) {
        is PaywallCta.StartTrial -> localizedFormat("Start %d-Day Free Trial", cta.days)
        is PaywallCta.SubscribeYearly -> localizedFormat("Subscribe · %@ / year", cta.price)
        is PaywallCta.SubscribeMonthly -> localizedFormat("Subscribe · %@ / month", cta.price)
        is PaywallCta.BuyLifetime -> localizedFormat("Buy Lifetime · %@", cta.price)
    }
}

@Composable
private fun finePrint(offer: ProOffer): String = when (val fine = PaywallModel.finePrint(offer)) {
    is PaywallFinePrint.TrialThenYearly ->
        localizedFormat("%d days free, then %@ per year. Renews automatically until you cancel in your account settings.", fine.days, fine.price)
    is PaywallFinePrint.Yearly -> localizedFormat("Renews automatically at %@ per year until you cancel.", fine.price)
    is PaywallFinePrint.Monthly -> localizedFormat("Renews automatically at %@ per month until you cancel.", fine.price)
    PaywallFinePrint.Lifetime -> localized("One-time purchase. No subscription, no renewal.")
}

@Composable
private fun Header() {
    val colors = SmartTheme.colors
    Row(
        Modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.AutoAwesome, Tint.Teal, size = 56.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("KeepSpace Pro", style = SmartType.metric, color = colors.textPrimary)
            Text("Keep space. Keep what matters.", style = SmartType.metadata, color = colors.textSecondary)
        }
    }
}

@Composable
private fun Plans(offers: List<ProOffer>, failed: Boolean, selected: ProProduct?, onSelect: (ProProduct) -> Unit, onRetry: () -> Unit) {
    val colors = SmartTheme.colors
    when {
        offers.isEmpty() && !failed -> {
            val loading = localized("Loading prices")
            Column(Modifier.semantics { contentDescription = loading }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(SmartMetrics.cardRadius)).background(colors.surfaceMuted)) }
            }
        }
        offers.isEmpty() -> SmartCard(style = CardStyle.Info) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Can't reach the store. Check your connection.", style = SmartType.body, color = colors.textPrimary)
                InlinePillButton("Retry", onRetry)
            }
        }
        else -> {
            val saving = PaywallModel.savePercent(offers)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                offers.forEach { offer ->
                    PlanCard(offer, isSelected = selected == offer.product, isBestValue = offer.product == ProProduct.Annual,
                        savePercent = if (offer.product == ProProduct.Annual) saving else null) { onSelect(offer.product) }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(offer: ProOffer, isSelected: Boolean, isBestValue: Boolean, savePercent: Int?, onClick: () -> Unit) {
    val colors = SmartTheme.colors
    val shape = RoundedCornerShape(SmartMetrics.cardRadius)
    val title = localized(when (offer.product) { ProProduct.Annual -> "Yearly"; ProProduct.Monthly -> "Monthly"; ProProduct.Lifetime -> "Lifetime" })
    val detail = when (offer.product) {
        ProProduct.Annual -> offer.trialDays?.let { localizedFormat("%d days free", it) } ?: localized("Billed yearly")
        ProProduct.Monthly -> localized("Billed monthly")
        ProProduct.Lifetime -> localized("One-time payment")
    }
    val unit = localized(when (offer.product) { ProProduct.Annual -> "per year"; ProProduct.Monthly -> "per month"; ProProduct.Lifetime -> "once" })
    val bestValue = localized("Best value")
    val saving = savePercent?.let { localizedFormat("Save %d%%", it) }
    val perMonth = offer.perMonthDisplay?.let { localizedFormat("≈ %@ / month", it) }
    // "Yearly, $19.99 per year, 7 days free, best value" — the selected state comes from the semantics.
    val spoken = listOfNotNull(title, "${offer.displayPrice} $unit", detail.takeIf { offer.trialDays != null }, bestValue.takeIf { isBestValue }).joinToString(", ")

    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).clip(shape)
            .background(if (isSelected) colors.heroGradient else androidx.compose.ui.graphics.SolidColor(colors.surface))
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.accent else colors.separator.copy(alpha = 0.6f), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken; selected = isSelected }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null,
            tint = if (isSelected) colors.accent else colors.textSecondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = SmartType.cardHeadline, color = colors.textPrimary)
                if (isBestValue) {
                    Text(bestValue, style = SmartType.metadata.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = colors.accent,
                        modifier = Modifier.clip(CircleShape).background(colors.icyBlue).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Text(detail, style = SmartType.metadata, color = colors.textSecondary)
            saving?.let { Text(it, style = SmartType.metadata.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = Tint.Mint.foreground(colors.isDark)) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(offer.displayPrice, style = SmartType.metric, color = colors.textPrimary)
            Text(unit, style = SmartType.metadata, color = colors.textSecondary)
            perMonth?.let { Text(it, style = SmartType.metadata.copy(fontSize = 12.sp), color = colors.textSecondary) }
        }
    }
}

private data class BenefitContent(val icon: ImageVector, val tint: Tint, val title: String, val detail: String)

private fun content(benefit: ProBenefit) = when (benefit) {
    ProBenefit.UnlimitedCleanup -> BenefitContent(Icons.Rounded.Delete, Tint.Coral, "Unlimited Cleanup", "Clear whole categories at once, with no monthly limit.")
    ProBenefit.CompressVideos -> BenefitContent(Icons.Rounded.Movie, Tint.Purple, "Compress Videos", "Shrink big videos on your device and keep the quality.")
    ProBenefit.SmartScreenshots -> BenefitContent(Icons.Rounded.Screenshot, Tint.Blue, "Smart Screenshots", "Browse screenshots by what's in them.")
    ProBenefit.ReceiptFiling -> BenefitContent(Icons.Rounded.Receipt, Tint.Mint, "Receipt Filing", "Find receipts and file them in the right place.")
    ProBenefit.AiTaste -> BenefitContent(Icons.Rounded.AutoFixHigh, Tint.Amber, "AI Taste", "Suggestions that learn what you like to keep.")
    ProBenefit.UnlimitedBackupAndRules -> BenefitContent(Icons.Rounded.Cloud, Tint.Teal, "Unlimited Backup & Rules", "Back up as much as you want, with every rule and cloud account.")
}

@Composable
private fun Benefits(focus: ProBenefit?) {
    val colors = SmartTheme.colors
    SmartCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Column {
            ProBenefit.entries.forEachIndexed { index, benefit ->
                val item = content(benefit)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (focus == benefit) colors.icyBlue else Color.Transparent)
                        .padding(vertical = 5.dp, horizontal = 6.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconTile(item.icon, item.tint, size = 34.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, style = SmartType.metadata.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                        Text(item.detail, style = SmartType.metadata.copy(fontSize = 13.sp), color = colors.textSecondary)
                    }
                }
                if (index < ProBenefit.entries.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
private fun Note(title: String, detail: String) {
    val colors = SmartTheme.colors
    SmartCard(style = CardStyle.Info) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = SmartType.cardHeadline, color = colors.textPrimary)
            Text(detail, style = SmartType.metadata, color = colors.textSecondary)
        }
    }
}
