package com.smartstorage.cleaner.media

// Mirrors ios/SmartStorage/MediaEngine/MediaItem.swift.

/** What the library layer knows about one asset. [id] is the MediaStore content URI string. */
data class MediaItem(
    val id: String,
    val kind: Kind,
    /** Capture time, epoch millis. */
    val createdAt: Long,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val isFavorite: Boolean,
    /** Last edit time, epoch millis; a change invalidates cached analysis. */
    val modifiedAt: Long = 0,
) {
    enum class Kind { Photo, Screenshot, Video, ScreenRecording }

    val isVideo: Boolean get() = kind == Kind.Video || kind == Kind.ScreenRecording
}

/** Per-image results from the on-device analyzer. */
data class ImageFeatures(
    /** 64-bit difference hash; similar images differ in few bits. */
    val dHash: Long,
    /** Variance of the Laplacian on a 256px grayscale thumbnail; higher = sharper. */
    val sharpness: Double,
    /** Mean luminance 0..1. */
    val exposure: Double,
    /** Best face score 0..1 (eyes open), null when no faces were found. */
    val faceQuality: Double?,
    val faceCount: Int,
) {
    /** Hamming distance between hashes, 0..64. */
    fun distanceTo(other: ImageFeatures): Int = java.lang.Long.bitCount(dHash xor other.dHash)
}

data class AnalyzedPhoto(val item: MediaItem, val features: ImageFeatures) {
    val id: String get() = item.id
}
