package com.smartstorage.cleaner.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
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

    fun analyze(uri: Uri): ImageFeatures? {
        val bitmap = resolver.thumbnail(uri, 512) ?: return null
        return try {
            val side = ImageMetrics.MEASURE_SIDE
            val measured = lumaOf(Bitmap.createScaledBitmap(bitmap, side, side, true))
            val grid = lumaOf(Bitmap.createScaledBitmap(bitmap, 9, 8, true))
            val (faceQuality, faceCount) = faces(bitmap)
            ImageFeatures(
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

    private fun lumaOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return IntArray(pixels.size) { ImageMetrics.luma(pixels[it]) }
    }

    override fun close() = faceDetector.close()
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
