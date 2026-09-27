package com.smartstorage.cleaner.media

import android.content.ContentResolver
import android.graphics.Bitmap
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
 * Reads a screenshot (or a photo of a paper receipt) on device (text + QR/boarding-pass codes) and
 * classifies it. Mirrors `ScreenshotAnalyzer.swift`. The recognised text is used only for
 * classification and is never stored. ML Kit's bundled recogniser reads Latin script only; Thai is
 * read by [ThaiOcr] when the Latin read suggests it's needed.
 * Call from a background thread: results are awaited synchronously.
 */
class ScreenshotAnalyzer(private val resolver: ContentResolver, private val thai: ThaiOcr? = null) : Closeable {
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
            val latin = ScreenshotClassifier.classify(text, hasCode)
            // ML Kit reads Latin only. Receipt photos, receipts, QR screenshots (Thai payment slips always
            // carry one) and text that looks like misread Thai get a second, Thai-capable read — slower,
            // so only when it matters.
            val needsThai = isPhoto || latin.kind == ScreenshotKind.Receipts || latin.kind == ScreenshotKind.QrCodes ||
                ThaiText.looksLikeMisreadThai(text)
            val thaiText = if (needsThai && thai != null) readThai(thai, uri, side, bitmap, isPhoto) else null
            if (thaiText == null) latin else combine(latin, ScreenshotClassifier.classify(thaiText, hasCode))
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Tesseract needs full detail: MediaStore thumbnails (used for the quick Latin read of
     * screenshots) are capped around 600–800 px, which is too small for Thai marks.
     */
    private fun readThai(thai: ThaiOcr, uri: Uri, side: Int, latinBitmap: Bitmap, isPhoto: Boolean): String? {
        val full = if (isPhoto) latinBitmap else resolver.decodeUpright(uri, side) ?: latinBitmap
        return try {
            thai.read(full)?.takeIf { it.isNotBlank() }
        } finally {
            if (full !== latinBitmap) full.recycle()
        }
    }

    /** Prefer the Thai-capable reading, but never lose a receipt or a detail the Latin read found. */
    private fun combine(latin: ScreenshotInfo, thai: ScreenshotInfo): ScreenshotInfo {
        if (latin.kind == ScreenshotKind.Receipts && thai.kind != ScreenshotKind.Receipts) return latin
        val a = thai.receipt ?: return thai
        val b = latin.receipt ?: return thai
        return thai.copy(receipt = a.copy(
            merchant = a.merchant ?: b.merchant, date = a.date ?: b.date, amount = a.amount ?: b.amount,
            currency = a.currency ?: b.currency, category = if (a.category == ReceiptCategory.Other) b.category else a.category,
        ))
    }

    override fun close() {
        recognizer.close()
        scanner.close()
        thai?.close()
    }

    companion object {
        /** Long-edge size to read at: large enough for small UI text. */
        const val READ_SIDE = 1600
    }
}

