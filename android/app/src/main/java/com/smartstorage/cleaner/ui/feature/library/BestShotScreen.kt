package com.smartstorage.cleaner.ui.feature.library

import com.smartstorage.cleaner.ui.i18n.localizedFormat
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.smartstorage.cleaner.media.DeletionOutcome
import com.smartstorage.cleaner.media.LocalMediaActions
import com.smartstorage.cleaner.media.ReviewKind
import com.smartstorage.cleaner.monetization.LocalMonetization
import com.smartstorage.cleaner.monetization.LocalPresentPaywall
import com.smartstorage.cleaner.monetization.ProFeature
import com.smartstorage.cleaner.monetization.QuotaGatePrompt
import com.smartstorage.cleaner.ui.feature.paywall.QuotaGateSheet
import com.smartstorage.cleaner.media.LocalLibraryStore
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.model.BestShotReason
import com.smartstorage.cleaner.model.LibraryMockData
import com.smartstorage.cleaner.model.PhotoGroup
import com.smartstorage.cleaner.model.ReasonKind
import com.smartstorage.cleaner.ui.components.IconTile
import com.smartstorage.cleaner.ui.components.AssetImage
import androidx.compose.material.icons.rounded.Favorite
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

private const val STRIP_COUNT = 4

/**
 * 06 — Best Shot: the AI's pick for a group, with a plain-language explanation.
 * On wide layouts the hero image and the "Why this one" card sit side by side.
 * [onBack] is null when shown as the detail pane next to the group list.
 */
@Composable
fun BestShotScreen(group: PhotoGroup, onBack: (() -> Unit)?) {
    val store = LocalLibraryStore.current
    val actions = LocalMediaActions.current
    val scope = rememberCoroutineScope()
    var deleting by rememberSaveable(group.id) { mutableStateOf(false) }
    val recommended = group.recommendedIndex
    val reasons = group.reasons.ifEmpty { LibraryMockData.bestShotReasons }
    // Up to four photos, always including the recommended one.
    val count = if (group.assetUris.isEmpty()) STRIP_COUNT else group.assetUris.size
    val stripIndices = if (count <= STRIP_COUNT) (0 until count).toList() else {
        val start = (recommended - 1).coerceIn(0, count - STRIP_COUNT)
        (start until start + STRIP_COUNT).toList()
    }
    var kept by rememberSaveable(group.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val monetization = LocalMonetization.current
    val presentPaywall = LocalPresentPaywall.current
    var quotaPrompt by remember { mutableStateOf<QuotaGatePrompt?>(null) }

    fun performKeep(allowed: Set<String>?) {
        val act = actions ?: return
        scope.launch {
            deleting = true
            val outcome = store.keep(group, recommended, act, allowed)
            deleting = false
            if (outcome is DeletionOutcome.Deleted) {
                kept = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack?.invoke()
            }
        }
    }

    quotaPrompt?.let { prompt ->
        QuotaGateSheet(
            prompt = prompt,
            onUnlock = { quotaPrompt = null; presentPaywall(ProFeature.UnlimitedCleanup) },
            onDeletePartial = { ids -> quotaPrompt = null; performKeep(ids.toSet()) },
            onNotNow = { quotaPrompt = null },
        )
    }

    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth, onBack = onBack) {
        ScreenHeader("Best Shot", "AI selected the best photo in this group.", Modifier.padding(bottom = 4.dp))

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stripIndices.forEach { index ->
                StripItem(
                    group = group,
                    index = index,
                    isRecommended = index == recommended,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BoxWithConstraints {
            if (maxWidth >= 740.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing)) {
                    Hero(group, recommended, Modifier.weight(1f))
                    Box(Modifier.width(340.dp)) { ReasonsCard(reasons) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing)) {
                    Hero(group, recommended, Modifier.fillMaxWidth())
                    ReasonsCard(reasons)
                }
            }
        }

        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                if (kept) "Recommended Kept" else "Keep Recommended",
                onClick = {
                    if (group.assetUris.isEmpty()) { kept = true; return@PrimaryButton } // demo content
                    // Deletes every photo in the group except Best Shot's recommendation (system confirmation first).
                    // The free monthly allowance first; over it, the quota gate is offered instead of deleting.
                    val others = group.assetUris.filterIndexed { i, _ -> i != recommended }
                    val candidates = store.cleanupItems(others, ReviewKind.Similar.safety)
                    val allowances = monetization.allowances()
                    val prompt = QuotaGatePrompt.of(allowances.gate(candidates), candidates, allowances)
                    if (prompt != null) { quotaPrompt = prompt; return@PrimaryButton }
                    performKeep(null)
                },
                modifier = Modifier.fillMaxWidth(),
                showsArrow = false,
                enabled = !kept && !deleting,
            )
            if (group.assetUris.isNotEmpty()) {
                Text(
                    localizedFormat("Keeping this photo deletes the other %d after you confirm. They stay in Trash for 30 days.", group.photoCount - 1),
                    style = TextStyle(fontSize = 12.sp),
                    color = SmartTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun StripItem(
    group: PhotoGroup,
    index: Int,
    isRecommended: Boolean,
    modifier: Modifier,
) {
    val colors = SmartTheme.colors
    Box(
        modifier
            .border(3.dp, if (isRecommended) colors.accent else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(4.dp),
    ) {
        AssetImage(group.assetUris.getOrNull(index), group.style, Modifier.fillMaxWidth().aspectRatio(0.72f), variant = index, cornerRadius = 12.dp)
        if (isRecommended) {
            BoxWithConstraints(Modifier.align(Alignment.TopCenter).padding(6.dp)) {
                val showsWord = maxWidth >= 110.dp
                Row(
                    Modifier.clip(CircleShape).background(colors.accent).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = com.smartstorage.cleaner.ui.i18n.localized("Recommended"), tint = Color.White, modifier = Modifier.size(12.dp))
                    // Collapse to the icon alone when the tile is too narrow for the word.
                    if (showsWord) {
                        Text("Recommended", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(group: PhotoGroup, selected: Int, modifier: Modifier) {
    Crossfade(targetState = selected, label = "hero", modifier = modifier) { index ->
        AssetImage(
            group.assetUris.getOrNull(index),
            group.style,
            Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            variant = index,
            cornerRadius = SmartMetrics.cardRadius,
            iconScale = 0.22f,
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ReasonsCard(reasons: List<BestShotReason>) {
    val colors = SmartTheme.colors
    SmartCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Why this one", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
            reasons.forEachIndexed { index, reason ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 58.dp), color = colors.separator)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(reason.kind.icon, reason.kind.tint, size = 44.dp)
                    Column {
                        Text(reason.title, style = SmartType.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                        Text(reason.detail, style = SmartType.metadata, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

private val ReasonKind.icon: ImageVector
    get() = when (this) {
        ReasonKind.Sharp -> Icons.Rounded.CenterFocusStrong
        ReasonKind.EyesOpen -> Icons.Rounded.People
        ReasonKind.Exposure -> Icons.Rounded.WbSunny
        ReasonKind.NoBlur -> Icons.AutoMirrored.Rounded.DirectionsRun
        ReasonKind.Faces -> Icons.Rounded.People
        ReasonKind.Favorite -> Icons.Rounded.Favorite
    }

private val ReasonKind.tint: Tint
    get() = when (this) {
        ReasonKind.Sharp -> Tint.Blue
        ReasonKind.EyesOpen -> Tint.Mint
        ReasonKind.Exposure -> Tint.Purple
        ReasonKind.NoBlur -> Tint.Coral
        ReasonKind.Faces -> Tint.Mint
        ReasonKind.Favorite -> Tint.Coral
    }
