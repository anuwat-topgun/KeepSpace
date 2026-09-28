package com.smartstorage.cleaner.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable

/**
 * On-device analysis for one photo. Uses a small thumbnail and ML Kit's bundled face model, so
 * nothing leaves the device and no model is downloaded at runtime.
 * Call from a background thread: face detection is awaited synchronously.
 */
class ImageAnalyzer(private val resolver: ContentResolver) : Closeable {
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build(),
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun analyze(uri: Uri): ImageFeatures? {
        val bitmap = resolver.thumbnail(uri, 512) ?: return null
        return try {
            val side = ImageMetrics.MEASURE_SIDE
            val measured = lumaOf(Bitmap.createScaledBitmap(bitmap, side, side, true))
            val grid = lumaOf(Bitmap.createScaledBitmap(bitmap, 9, 8, true))
            val (faceQuality, faceCount) = faces(bitmap)
            val location = location(uri)
            ImageFeatures(
                latitude = location?.get(0),
                longitude = location?.get(1),
                // Only photos without people can be receipts, so skip the text pass for the rest.
                textLines = if (faceCount == 0) textLines(bitmap) else 0,
                dHash = ImageMetrics.dHash(grid),
                sharpness = ImageMetrics.laplacianVariance(measured, side),
                exposure = ImageMetrics.exposure(measured),
                faceQuality = faceQuality,
                faceCount = faceCount,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Best "eyes open" score across faces (0..1), or null when there are no faces. */
    private fun faces(bitmap: Bitmap): Pair<Double?, Int> = try {
        val faces = Tasks.await(faceDetector.process(InputImage.fromBitmap(bitmap, 0)))
        val quality = faces.mapNotNull { face ->
            val eyes = listOfNotNull(face.leftEyeOpenProbability, face.rightEyeOpenProbability)
            if (eyes.isEmpty()) null else eyes.average()
        }.maxOrNull()
        quality to faces.size
    } catch (_: Exception) {
        null to 0
    }

    /**
     * EXIF GPS as [lat, lon]. Android redacts it unless the original is requested, which needs
     * ACCESS_MEDIA_LOCATION; without that permission photos simply have no location.
     */
    private fun location(uri: Uri): DoubleArray? = try {
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.provider.MediaStore.setRequireOriginal(uri) else uri
        resolver.openInputStream(source)?.use { stream ->
            ExifInterface(stream).latLong?.takeUnless { it[0] == 0.0 && it[1] == 0.0 }
        }
    } catch (_: Exception) {
        null // SecurityException without the permission, or unreadable EXIF
    }

    /** How many lines of text the thumbnail holds; the text itself is discarded. */
    private fun textLines(bitmap: Bitmap): Int = try {
        Tasks.await(textRecognizer.process(InputImage.fromBitmap(bitmap, 0))).textBlocks.sumOf { it.lines.size }
    } catch (_: Exception) {
        0
    }

    private fun lumaOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return IntArray(pixels.size) { ImageMetrics.luma(pixels[it]) }
    }

    override fun close() {
        faceDetector.close()
        textRecognizer.close()
    }
}

/** Local, downscaled bitmap for [uri] (≈ [side] px on the long edge), or null if unreadable. */
fun ContentResolver.thumbnail(uri: Uri, side: Int): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        loadThumbnail(uri, Size(side, side), null)
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
        openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
} catch (_: Exception) {
    null
}

/**
 * Full image decoded to about [side] px on the long edge, turned upright per its EXIF orientation,
 * or null if unreadable. Slower than [thumbnail]; used where detail matters (reading receipts).
 */
fun ContentResolver.decodeUpright(uri: Uri, side: Int): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder applies EXIF orientation itself.
        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(this, uri)) { decoder, info, _ ->
            val scale = minOf(1.0, side.toDouble() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val orientation = openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        thumbnail(uri, side)?.let { bitmap ->
            if (degrees == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }, true).also { bitmap.recycle() }
        }
    }
} catch (_: Exception) {
    null
}
