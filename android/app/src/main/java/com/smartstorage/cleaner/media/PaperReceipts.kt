package com.smartstorage.cleaner.media

// Mirrors ios/SmartStorage/MediaEngine/PaperReceipts.swift.

/**
 * Picks the camera photos worth reading as paper receipts. Reading text is slow, so only photos
 * that look like a document get the full OCR pass; everything else is skipped. Pure, for tests.
 */
object PaperReceiptDetector {
    /** Classifier confidence that alone makes a photo worth reading (iOS provides it). */
    const val DOCUMENT_SCORE = 0.15
    /**
     * Text lines that make a photo worth reading even when the classifier is unsure. Generous: a
     * false candidate only costs one OCR pass and is then ignored unless it reads as a receipt.
     */
    const val TEXT_LINES = 5
    /** Long-edge size to read photos at: receipt print is small in a camera shot. */
    const val READ_SIDE = 2048

    fun isCandidate(photo: AnalyzedPhoto): Boolean {
        val f = photo.features
        // People photos with a sign in the background aren't receipts.
        if (photo.item.kind != MediaItem.Kind.Photo || f.faceCount != 0) return false
        return f.documentScore >= DOCUMENT_SCORE || f.textLines >= TEXT_LINES
    }
}
