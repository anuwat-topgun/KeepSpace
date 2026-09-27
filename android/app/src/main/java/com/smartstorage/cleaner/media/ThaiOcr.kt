package com.smartstorage.cleaner.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import java.io.File

/**
 * Thai + English OCR on device with Tesseract (ML Kit's bundled model reads Latin only). Slower
 * than ML Kit, so [ScreenshotAnalyzer] only asks it when Thai is likely. One engine, used by one
 * thread at a time; the models (bundled assets) are copied to no-backup storage on first use.
 * The recognised text is returned for classification only and is never stored.
 */
class ThaiOcr(private val context: Context) : Closeable {
    private var api: TessBaseAPI? = null
    private var unavailable = false

    /** Confident lines of text, or null if the engine can't start. */
    @Synchronized
    fun read(bitmap: Bitmap): String? {
        val engine = api ?: start() ?: return null
        return try {
            engine.setImage(bitmap)
            engine.utF8Text // runs recognition
            val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
            val lines = mutableListOf<String>()
            val iterator = engine.resultIterator
            iterator.begin()
            do {
                val line = iterator.getUTF8Text(level)?.trim().orEmpty()
                // Low-confidence lines are mostly noise read from blank areas and photo backgrounds.
                if (line.isNotEmpty() && iterator.confidence(level) >= MIN_CONFIDENCE) lines += line
            } while (iterator.next(level))
            iterator.delete()
            lines.joinToString("\n")
        } catch (e: Exception) {
            Log.w(TAG, "Thai OCR failed: ${e.javaClass.simpleName}")
            null
        } finally {
            engine.clear()
        }
    }

    private fun start(): TessBaseAPI? {
        if (unavailable) return null
        return runCatching {
            val root = File(context.noBackupFilesDir, "tesseract")
            val marker = File(root, "models.$MODELS_VERSION")
            if (!marker.exists()) {
                val dir = File(root, "tessdata").apply { mkdirs() }
                for (lang in LANGUAGES) {
                    val out = File(dir, "$lang.traineddata")
                    val partial = File(dir, "$lang.traineddata.part")
                    context.assets.open("tessdata/$lang.traineddata").use { input -> partial.outputStream().use(input::copyTo) }
                    check(partial.renameTo(out) || (out.delete() && partial.renameTo(out))) { "copy failed" }
                }
                marker.createNewFile()
            }
            TessBaseAPI().apply {
                check(init(root.absolutePath, LANGUAGES.joinToString("+"), TessBaseAPI.OEM_LSTM_ONLY)) { "init failed" }
                // One block: keeps receipt labels and amounts on the same line.
                pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            }
        }.onFailure {
            unavailable = true
            Log.w(TAG, "Thai OCR unavailable: ${it.javaClass.simpleName}")
        }.getOrNull()?.also { api = it }
    }

    @Synchronized
    override fun close() {
        api?.recycle()
        api = null
    }

    private companion object {
        const val TAG = "KeepSpaceOcr"
        val LANGUAGES = listOf("tha", "eng")
        /** Bump when the bundled models change so they are copied again. */
        const val MODELS_VERSION = 1
        /** Tesseract line confidence (0–100) below which a line is treated as noise. */
        const val MIN_CONFIDENCE = 50f
    }
}
