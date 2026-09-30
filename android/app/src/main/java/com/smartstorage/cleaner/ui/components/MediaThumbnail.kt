package com.smartstorage.cleaner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.cleaner.model.ThumbnailStyle
import com.smartstorage.cleaner.ui.theme.SmartTheme

val ThumbnailStyle.icon: ImageVector
    get() = when (this) {
        ThumbnailStyle.Sunset -> Icons.Rounded.WbTwilight
        ThumbnailStyle.Dinner -> Icons.Rounded.Restaurant
        ThumbnailStyle.Portrait -> Icons.Rounded.Person
        ThumbnailStyle.Family -> Icons.Rounded.Groups
        ThumbnailStyle.Mountain -> Icons.Rounded.Landscape
        ThumbnailStyle.Concert -> Icons.Rounded.Mic
        ThumbnailStyle.Cake -> Icons.Rounded.Cake
        ThumbnailStyle.Beach -> Icons.Rounded.BeachAccess
        ThumbnailStyle.Baby -> Icons.Rounded.ChildCare
        ThumbnailStyle.Screen -> Icons.Rounded.PhoneIphone
        ThumbnailStyle.Tokyo -> Icons.Rounded.AccountBalance
    }

/**
 * Placeholder media tile. [variant] nudges the gradient direction so a strip of one style still
 * reads as distinct shots. Replaced by real MediaStore thumbnails once the media engine lands.
 */
@Composable
fun MediaThumbnail(
    style: ThumbnailStyle,
    modifier: Modifier = Modifier,
    variant: Int = 0,
    cornerRadius: Dp = 12.dp,
    iconScale: Float = 0.3f,
) {
    val colors = style.colors(SmartTheme.colors.isDark)
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .drawBehind { drawRect(gradient(colors, variant, size)) },
        contentAlignment = Alignment.Center,
    ) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        Icon(
            style.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size((side * iconScale).coerceAtLeast(12.dp)),
        )
    }
}

/** Four gradient directions (diagonal, vertical, anti-diagonal, horizontal), sized to the tile. */
private fun gradient(colors: List<Color>, variant: Int, size: Size): Brush {
    val w = size.width
    val h = size.height
    val (start, end) = when (variant % 4) {
        0 -> Offset(0f, 0f) to Offset(w, h)
        1 -> Offset(w / 2, 0f) to Offset(w / 2, h)
        2 -> Offset(w, 0f) to Offset(0f, h)
        else -> Offset(0f, h / 2) to Offset(w, h / 2)
    }
    return Brush.linearGradient(colors, start = start, end = end)
}

/** Translucent "+8" tile that ends a thumbnail strip. */
@Composable
fun OverflowTile(count: Int, modifier: Modifier = Modifier, cornerRadius: Dp = 12.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Gray.copy(alpha = 0.55f))
            .semantics { contentDescription = "$count more" },
        contentAlignment = Alignment.Center,
    ) {
        Text("+$count", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}

/** Row of thumbnails that fills the available width, ending in an overflow tile. */
@Composable
fun ThumbnailStrip(
    style: ThumbnailStyle,
    totalCount: Int,
    visibleCount: Int = 5,
    aspectRatio: Float = 0.78f,
    /** Real assets to show; empty = gradient placeholders. */
    assetUris: List<String> = emptyList(),
) {
    val shown = minOf(visibleCount, totalCount)
    val overflow = totalCount - shown
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(shown) { index ->
            AssetImage(assetUris.getOrNull(index), style, Modifier.weight(1f).aspectRatio(aspectRatio), variant = index)
        }
        if (overflow > 0) {
            OverflowTile(overflow, Modifier.weight(1f).aspectRatio(aspectRatio))
        }
    }
}

/** Duration pill overlaid on video thumbnails ("08:42"). */
@Composable
fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
