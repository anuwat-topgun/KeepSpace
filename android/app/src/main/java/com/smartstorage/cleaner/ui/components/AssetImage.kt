package com.smartstorage.cleaner.ui.components

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartstorage.cleaner.media.thumbnail
import com.smartstorage.cleaner.model.ThumbnailStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows a library asset's thumbnail, or the gradient placeholder for demo content / while loading. */
@Composable
fun AssetImage(
    uri: String?,
    fallback: ThumbnailStyle,
    modifier: Modifier = Modifier,
    variant: Int = 0,
    cornerRadius: Dp = 12.dp,
    iconScale: Float = 0.3f,
    /** Keep the top when cropping (receipts and screenshots read from the top). */
    cropTop: Boolean = false,
    /** Crop compact thumbnails; use Fit for previews where the whole portrait must be visible. */
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (uri == null) {
        MediaThumbnail(fallback, modifier, variant, cornerRadius, iconScale)
        return
    }
    val resolver = LocalContext.current.contentResolver
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (contentScale == ContentScale.Fit) Color.Black.copy(alpha = 0.92f) else Color.Transparent),
    ) {
        val longEdge = if (maxWidth == Dp.Infinity || maxHeight == Dp.Infinity) 256.dp else maxOf(maxWidth, maxHeight)
        val px = with(density) { longEdge.roundToPx() }.coerceIn(64, 1024)
        val bitmap by produceState<ImageBitmap?>(null, uri, px) {
            value = withContext(Dispatchers.IO) { resolver.thumbnail(Uri.parse(uri), px)?.asImageBitmap() }
        }
        Crossfade(bitmap, label = "asset") { image ->
            if (image != null) {
                Image(
                    image,
                    contentDescription = null,
                    contentScale = contentScale,
                    alignment = if (cropTop) Alignment.TopCenter else Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MediaThumbnail(fallback, Modifier.fillMaxSize(), variant, 0.dp, iconScale)
            }
        }
    }
}
