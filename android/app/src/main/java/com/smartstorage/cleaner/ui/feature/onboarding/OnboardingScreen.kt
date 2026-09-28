package com.smartstorage.cleaner.ui.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.model.ThumbnailStyle
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ListTile
import com.smartstorage.cleaner.ui.components.MediaThumbnail
import com.smartstorage.cleaner.ui.components.PrimaryButton
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

private data class Benefit(val icon: ImageVector, val tint: Tint, val title: String, val detail: String)

private val benefits = listOf(
    Benefit(Icons.Rounded.AutoAwesome, Tint.Teal, "Find duplicates", "Spot and remove similar photos, videos, and large files."),
    Benefit(Icons.Rounded.Favorite, Tint.Coral, "Protect memories", "Keep your best photos and videos safe and organized."),
    Benefit(Icons.Rounded.Storage, Tint.Purple, "Recover storage safely", "Free up space with smart, on-device analysis."),
)

/**
 * 01 — Onboarding: value proposition and the on-device privacy promise.
 * On tablets the illustration moves beside the copy instead of above the cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    var showsLearnMore by rememberSaveable { mutableStateOf(false) }
    val colors = SmartTheme.colors

    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth) {
        BoxWithConstraints(Modifier.padding(top = 24.dp)) {
            if (maxWidth >= 800.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Headline()
                        BenefitCards()
                    }
                    StorageIllustration(Modifier.weight(1f).height(420.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Headline()
                    StorageIllustration(Modifier.fillMaxWidth().height(280.dp))
                    BenefitCards()
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Next, choose which photos and videos KeepSpace may analyze on this device.",
                style = SmartType.metadata,
                color = colors.textSecondary,
                modifier = Modifier.widthIn(max = SmartMetrics.readableWidth).padding(bottom = 8.dp),
            )
            PrimaryButton("Choose Photo Access", onClick = onContinue, modifier = Modifier.widthIn(max = SmartMetrics.readableWidth).fillMaxWidth())
            TextButton(onClick = { showsLearnMore = true }) {
                Text("Learn More", style = SmartType.body.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
            }
        }
    }

    if (showsLearnMore) {
        ModalBottomSheet(onDismissRequest = { showsLearnMore = false }, containerColor = colors.background) {
            PrivacyDetail()
        }
    }
}

@Composable
private fun Headline() {
    val colors = SmartTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Free up space,\nkeep what matters.",
            style = TextStyle(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold),
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text("AI runs entirely on your device.", style = SmartType.screenSubtitle, color = colors.textSecondary)
    }
}

@Composable
private fun BenefitCards() {
    val colors = SmartTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing)) {
        benefits.forEach { b ->
            SmartCard(contentPadding = PaddingValues(16.dp)) {
                ListTile(b.icon, b.title, tint = b.tint, subtitle = b.detail, showsChevron = false)
            }
        }
        SmartCard(style = CardStyle.Info, contentPadding = PaddingValues(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Tint.Teal.background(colors.isDark)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.accent)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your photos go only where you choose.", style = SmartType.cardHeadline, color = colors.textPrimary)
                    Text(
                        "AI analysis stays on this device. Cloud Backup sends only files you request directly to your Drive or OneDrive.",
                        style = SmartType.metadata,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** Floating glass tiles around a storage block — echoes the mockup hero art. */
@Composable
private fun StorageIllustration(modifier: Modifier) {
    val colors = SmartTheme.colors
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        // Unbounded edges so the glow fades out softly instead of being cut into a square.
        Box(
            Modifier
                .size(h * 0.9f)
                .align(Alignment.Center)
                .blur(40.dp, BlurredEdgeTreatment.Unbounded)
                .background(colors.icyBlue, CircleShape),
        )
        GlassTile(Icons.Rounded.Image, h * 0.36f, Modifier.offset(x = w * 0.24f - h * 0.18f, y = h * 0.3f - h * 0.18f).rotate(-8f))
        GlassTile(Icons.Rounded.PlayArrow, h * 0.3f, Modifier.offset(x = w * 0.76f - h * 0.15f, y = h * 0.24f - h * 0.15f).rotate(8f))
        GlassTile(Icons.Rounded.Description, h * 0.3f, Modifier.offset(x = w * 0.82f - h * 0.15f, y = h * 0.7f - h * 0.15f).rotate(6f))
        MediaThumbnail(
            ThumbnailStyle.Mountain,
            Modifier
                .offset(x = w * 0.2f - h * 0.2f, y = h * 0.74f - h * 0.15f)
                .size(width = h * 0.4f, height = h * 0.3f)
                .rotate(-10f)
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            cornerRadius = 16.dp,
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(h * 0.46f)
                .rotate(-12f)
                .shadow(16.dp, RoundedCornerShape(28.dp), spotColor = colors.accent.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(colors.surface, colors.icyBlue))),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.padding(bottom = 16.dp).size(width = 30.dp, height = 6.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun GlassTile(icon: ImageVector, size: Dp, modifier: Modifier) {
    val colors = SmartTheme.colors
    Box(
        modifier
            .size(size)
            .shadow(8.dp, RoundedCornerShape(size * 0.24f), spotColor = colors.accent.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(size * 0.24f))
            .background(colors.surface.copy(alpha = 0.8f))
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size * 0.6f).clip(RoundedCornerShape(size * 0.14f)).background(Color(if (colors.isDark) 0xFF3F8AA8 else 0xFF7FC3DD)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.32f))
        }
    }
}

@Composable
private fun PrivacyDetail() {
    val points = listOf(
        Triple(Icons.Rounded.Memory, "AI runs on this device", "Duplicate detection, best-shot picks and screenshot understanding use on-device models only."),
        Triple(Icons.Rounded.CloudOff, "No uploads to our servers", "Your photos, videos and extracted text are never sent to KeepSpace."),
        Triple(Icons.Rounded.CloudUpload, "Backups go where you choose", "If you enable cloud backup, files upload directly to your Google Drive or OneDrive."),
        Triple(Icons.Rounded.VerifiedUser, "Nothing is deleted silently", "You review every cleanup, and important memories are protected by default."),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(SmartMetrics.stackSpacing),
    ) {
        ScreenHeader("Your privacy", "How KeepSpace handles your media.")
        points.forEach { (icon, title, detail) ->
            SmartCard(contentPadding = PaddingValues(16.dp)) {
                ListTile(icon, title, subtitle = detail, showsChevron = false)
            }
        }
    }
}
