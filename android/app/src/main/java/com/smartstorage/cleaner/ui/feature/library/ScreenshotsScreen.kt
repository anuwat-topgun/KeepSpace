package com.smartstorage.cleaner.ui.feature.library

import com.smartstorage.cleaner.model.ThumbnailStyle
import com.smartstorage.cleaner.ui.components.AssetImage
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import com.smartstorage.cleaner.media.ReviewKind
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.model.ExpiredScreenshot
import com.smartstorage.cleaner.media.libraryState
import androidx.compose.material.icons.rounded.CropFree
import com.smartstorage.cleaner.media.ScreenshotKind
import com.smartstorage.cleaner.model.formattedBytes
import com.smartstorage.cleaner.ui.components.AdaptiveGrid
import com.smartstorage.cleaner.ui.components.CardRow
import com.smartstorage.cleaner.ui.components.CardStyle
import com.smartstorage.cleaner.ui.components.ScreenHeader
import com.smartstorage.cleaner.ui.components.ScreenScaffold
import com.smartstorage.cleaner.ui.components.SmartCard
import com.smartstorage.cleaner.ui.components.StatusBadge
import com.smartstorage.cleaner.ui.theme.SmartMetrics
import com.smartstorage.cleaner.ui.theme.SmartTheme
import com.smartstorage.cleaner.ui.theme.SmartType
import com.smartstorage.cleaner.ui.theme.Tint

/** 11 — Screenshots grouped by what they contain, plus time-sensitive content that has expired. */
@Composable
fun ScreenshotsScreen(onReview: (ReviewKind) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(maxWidth = SmartMetrics.wideContentWidth, onBack = onBack) {
        val state = libraryState()
        ScreenHeader("Screenshots", "${state.content.screenshotsBytes.formattedBytes()} recoverable", Modifier.padding(bottom = 8.dp))
        if (state.content.screenshotCategories.isEmpty()) {
            SmartCard(style = CardStyle.Info) {
                Text(
                    if (state.isScanning) "Reading your library…" else "No screenshots found.",
                    style = SmartType.metadata,
                    color = SmartTheme.colors.textSecondary,
                )
            }
        }
        AdaptiveGrid(state.content.screenshotCategories) { category ->
            CardRow(
                category.kind.icon,
                category.title,
                tint = category.kind.tint,
                subtitle = if (category.count > 0) "${category.count} · ${category.bytes.formattedBytes()}" else category.bytes.formattedBytes(),
                onClick = if (category.reviewable) ({ onReview(ReviewKind.Screenshots(category.kind)) }) else null,
            )
        }
        if (state.content.expiredScreenshots.isNotEmpty()) {
            val real = state.content.expiredScreenshots.any { it.assetUri != null } // demo content has nothing to review
            Box(if (real) Modifier.clickable(role = Role.Button) { onReview(ReviewKind.Expired) } else Modifier) {
                ExpiredContentCard(state.content.expiredScreenshots.take(3))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = SmartTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
            Text(
                "Screenshots are read on this device to sort them. The text is never stored or uploaded.",
                style = TextStyle(fontSize = 12.sp),
                color = SmartTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ExpiredContentCard(items: List<ExpiredScreenshot>) {
    val colors = SmartTheme.colors
    SmartCard(style = CardStyle.Info) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Tint.Blue.foreground(colors.isDark))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Expired Content", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text(
                        "Screenshots from past events, trips, and time-sensitive content you may not need.",
                        style = SmartType.metadata,
                        color = colors.textSecondary,
                    )
                }
            }
            items.forEach { item ->
                SmartCard(contentPadding = PaddingValues(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.assetUri != null) {
                            AssetImage(item.assetUri, ThumbnailStyle.Screen, Modifier.width(120.dp).height(96.dp), cornerRadius = 10.dp)
                        } else {
                            BoardingPassArt()
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = SmartType.cardHeadline, color = colors.textPrimary)
                            Text(item.detail, style = SmartType.metadata, color = colors.textSecondary)
                            Text(item.status, style = SmartType.metadata, color = colors.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            StatusBadge("Review for deletion", Icons.Rounded.DeleteOutline, Tint.Coral, compact = true)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
                    }
                }
            }
        }
    }
}

/** Small illustrated boarding pass (BKK → TYO) used for the expired-content preview. */
@Composable
private fun BoardingPassArt() {
    val colors = SmartTheme.colors
    val blue = Tint.Blue.foreground(colors.isDark)
    Column(
        Modifier
            .width(120.dp)
            .height(96.dp)
            .shadow(3.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().height(26.dp).background(blue).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Flight, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(30.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("BKK", style = style, color = colors.textPrimary)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = blue, modifier = Modifier.size(12.dp))
                Text("TYO", style = style, color = colors.textPrimary)
            }
            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
        }
    }
}

private val ScreenshotKind.icon: ImageVector
    get() = when (this) {
        ScreenshotKind.Shopping -> Icons.Rounded.ShoppingBag
        ScreenshotKind.Receipts -> Icons.AutoMirrored.Rounded.ReceiptLong
        ScreenshotKind.Chats -> Icons.Rounded.Forum
        ScreenshotKind.QrCodes -> Icons.Rounded.QrCodeScanner
        ScreenshotKind.Tickets -> Icons.Rounded.ConfirmationNumber
        ScreenshotKind.Other -> Icons.Rounded.CropFree
    }

private val ScreenshotKind.tint: Tint
    get() = when (this) {
        ScreenshotKind.Shopping -> Tint.Coral
        ScreenshotKind.Receipts -> Tint.Amber
        ScreenshotKind.Chats -> Tint.Mint
        ScreenshotKind.QrCodes -> Tint.Blue
        ScreenshotKind.Tickets -> Tint.Purple
        ScreenshotKind.Other -> Tint.Gray
    }
