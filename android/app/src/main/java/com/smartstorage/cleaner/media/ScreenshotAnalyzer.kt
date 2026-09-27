package com.smartstorage.cleaner.media

import android.content.ContentResolver
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable

/**
 * Reads a screenshot (or a photo of a paper receipt) on device (text + QR/boarding-pass codes) and classifies it. Mirrors
 * `ScreenshotAnalyzer.swift`. The recognised text is used only for classification and is never
 * stored. ML Kit's bundled recogniser reads Latin script only, so Thai text isn't recognised here.
 * Call from a background thread: results are awaited synchronously.
 */
class ScreenshotAnalyzer(private val resolver: ContentResolver) : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC, Barcode.FORMAT_PDF417)
            .build(),
    )

    /**
     * For camera photos ([isPhoto]) only receipts matter, so barcodes are skipped, and printed rows
     * are rebuilt from the text's position (labels and amounts come back as separate columns).
     */
    fun analyze(uri: Uri, side: Int = READ_SIDE, isPhoto: Boolean = false): ScreenshotInfo? {
        // MediaStore thumbnails are capped well below what small receipt print needs, so photos are decoded.
        val bitmap = (if (isPhoto) resolver.decodeUpright(uri, side) else resolver.thumbnail(uri, side)) ?: return null
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = runCatching {
                val result = Tasks.await(recognizer.process(image))
                if (!isPhoto) return@runCatching result.text
                TextLayout.rows(result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    val c = line.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
                    fun p(i: Int) = TextLayout.Point(c[i].x.toDouble(), c[i].y.toDouble())
                    // ML Kit corners run clockwise from top-left.
                    TextLayout.Fragment(line.text, p(0), p(1), p(2), p(3))
                }).joinToString("\n")
            }.getOrDefault("")
            val hasCode = !isPhoto && runCatching { Tasks.await(scanner.process(image)).isNotEmpty() }.getOrDefault(false)
            ScreenshotClassifier.classify(text, hasCode)
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        recognizer.close()
        scanner.close()
    }

    companion object {
        /** Long-edge size to read at: large enough for small UI text. */
        const val READ_SIDE = 1600
    }
}

