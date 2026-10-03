package com.smartstorage.cleaner.ui.feature.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.media.LocalRuleStore
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.monetization.Allowances
import com.smartstorage.cleaner.monetization.LocalMonetization
import com.smartstorage.cleaner.monetization.LocalPresentPaywall
import com.smartstorage.cleaner.monetization.LocalProBilling
import com.smartstorage.cleaner.monetization.ProProduct
import com.smartstorage.cleaner.monetization.ProStatus
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SecondaryButton
import com.smartstorage.cleaner.ui.components.SectionLabel
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.i18n.Text
import com.smartstorage.cleaner.ui.i18n.localized
import com.smartstorage.cleaner.ui.i18n.localizedFormat
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint
import kotlinx.coroutines.launch

/**
 * Settings → Subscription (store/PAYWALL_DESIGN.md §3.3): the plan, this month's free allowance, and the way to upgrade,
 * restore or manage. A person on Pro never sees the paywall from here — only their plan.
 */
@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    val colors = SmartTheme.colors
    val context = LocalContext.current
    val monetization = LocalMonetization.current
    val billing = LocalProBilling.current
    val presentPaywall = LocalPresentPaywall.current
    val status by monetization.status.collectAsState()
    val ledger by monetization.ledger.collectAsState()
    val rules by LocalRuleStore.current.rules.collectAsState()
    val scope = rememberCoroutineScope()
    var restoreResult by remember { mutableStateOf<Boolean?>(null) }
    // Re-evaluated with the ledger so a month rollover shows fresh numbers.
    val allowances = remember(status, ledger) { monetization.allowances() }

    ScreenScaffold(onBack = onBack) {
        ScreenHeader("Subscription", modifier = Modifier.padding(bottom = 8.dp))

        SmartCard(style = CardStyle.Hero) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(planTitle(status), style = SmartType.metric, color = colors.textPrimary)
                planDetail(status)?.let { Text(it, style = SmartType.metadata, color = colors.textSecondary) }
            }
        }

        if (!allowances.isPro) UsageCard(allowances, ruleCount = rules.size, limits = monetization.limits)

        if (allowances.isPro) {
            if ((status as? ProStatus.Pro)?.product?.isSubscription == true) {
                SecondaryButton("Manage Subscription", modifier = Modifier.fillMaxWidth(), onClick = {
                    val sku = (status as ProStatus.Pro).product.id
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=$sku&package=${context.packageName}")))
                })
            }
        } else {
            PrimaryButton("Upgrade to Pro", showsArrow = false, modifier = Modifier.fillMaxWidth(), onClick = { presentPaywall(null) })
        }

        SecondaryButton("Restore Purchases", outlined = true, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch { restoreResult = billing.restore() }
        })
        restoreResult?.let { reachable ->
            Text(
                when {
                    monetization.isPro -> "Purchases restored."
                    reachable -> "No active Pro purchase found."
                    else -> "Can't reach the store. Check your connection."
                },
                style = SmartType.metadata, color = colors.textSecondary,
            )
        }

        if (!allowances.isPro) {
            SectionLabel("Pro adds")
            SmartCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Unlimited Cleanup", "Compress Videos", "Smart Screenshots", "Receipt Filing", "AI Taste", "Unlimited Backup & Rules").forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent)
                            Text(item, style = SmartType.body, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageCard(a: Allowances, ruleCount: Int, limits: com.smartstorage.cleaner.monetization.UsageLimits) {
    val colors = SmartTheme.colors
    val context = LocalContext.current
    val usedBytes = minOf(a.ledger.cleanupBytes, limits.cleanupBytesPerMonth)
    val usedFiles = minOf(a.ledger.backupFiles, limits.backupFilesPerMonth)
    val usedRules = minOf(ruleCount, limits.maxRules)
    SmartCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel("This month")
            Meter("Cleanup", usedBytes.toDouble(), limits.cleanupBytesPerMonth.toDouble(),
                localizedFormat("%@ of %@ used", usedBytes.formattedBytes(), limits.cleanupBytesPerMonth.formattedBytes()))
            Meter("Backup", usedFiles.toDouble(), limits.backupFilesPerMonth.toDouble(),
                localizedFormat("%d of %d files used", usedFiles, limits.backupFilesPerMonth))
            Meter("Rules", usedRules.toDouble(), limits.maxRules.toDouble(), localizedFormat("%d of %d used", usedRules, limits.maxRules))
            Text(localizedFormat("Resets %@", shortDate(context, a.resetDate)), style = SmartType.metadata, color = colors.textSecondary)
        }
    }
}

/** A labelled usage bar. Turns amber near the limit; the text is the accessible value. */
@Composable
private fun Meter(title: String, value: Double, limit: Double, text: String) {
    val colors = SmartTheme.colors
    val fraction = if (limit > 0) (value / limit).coerceIn(0.0, 1.0).toFloat() else 0f
    val label = localized(title)
    Column(
        Modifier.clearAndSetSemantics { contentDescription = label; stateDescription = text },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = SmartType.cardHeadline, color = colors.textPrimary)
            Text(text, style = SmartType.metadata, color = colors.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(colors.surfaceMuted)) {
            Box(
                Modifier.fillMaxWidth(if (fraction > 0f) maxOf(fraction, 0.02f) else 0f).height(8.dp).clip(CircleShape)
                    .background(if (fraction >= 0.8f) Tint.Amber.foreground(colors.isDark) else colors.accent),
            )
        }
    }
}

private fun shortDate(context: android.content.Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)

@Composable
private fun planTitle(status: ProStatus): String = localized(
    when ((status as? ProStatus.Pro)?.product) {
        null -> "You're on the Free plan."
        ProProduct.Annual -> "KeepSpace Pro · Yearly"
        ProProduct.Monthly -> "KeepSpace Pro · Monthly"
        ProProduct.Lifetime -> "KeepSpace Pro · Lifetime"
    },
)

@Composable
private fun planDetail(status: ProStatus): String? {
    val pro = status as? ProStatus.Pro ?: return localized("Everything you see is free to scan and review.")
    if (pro.product == ProProduct.Lifetime) return localized("Thank you for supporting KeepSpace.")
    if (pro.isGrace) return localized("Can't reach the store. Pro stays on for now.")
    // Play doesn't expose an end date on the device, so none is shown (expiresAt is only a re-check horizon).
    return localized(if (pro.willRenew) "Renews automatically until you cancel in Google Play." else "Ends at the end of the current period.")
}

/** The row's subtitle in Settings: "Free", "Pro · Lifetime" (Play doesn't expose renewal dates on device). */
@Composable
fun subscriptionSettingsSubtitle(status: ProStatus): String = when ((status as? ProStatus.Pro)?.product) {
    null -> localized("Free")
    ProProduct.Lifetime -> localized("Pro · Lifetime")
    else -> localized("Pro")
}
