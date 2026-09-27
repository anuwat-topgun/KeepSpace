package com.smartstorage.cleaner.media

// Mirrors ios/SmartStorage/MediaEngine/ReviewModels.swift.

/** A set of cleanup candidates the user reviews before anything is deleted. */
enum class ReviewKind(val title: String, val explanation: String) {
    Similar("Similar Photos", "Extra shots from bursts. The best photo of each group is kept."),
    Blurry("Blurry Photos", "Photos that came out blurry or shaky."),
    OldScreenshots("Old Screenshots", "Screenshots older than 30 days."),
    OldRecordings("Old Screen Recordings", "Screen recordings older than 30 days."),
    LargeVideos("Large Videos", "Your biggest videos. Nothing is selected until you choose."),
}

data class ReviewItem(
    val id: String,
    val bytes: Long,
    val isVideo: Boolean,
    val durationMs: Long,
    val createdAt: Long,
    /** Selected when the review opens. Keepers and personal videos never are. */
    val preselected: Boolean,
    /** The group's keeper: shown for context, never deletable from here. */
    val isKeeper: Boolean = false,
)

val List<ReviewItem>.defaultSelection: Set<String>
    get() = filter { it.preselected && !it.isKeeper }.mapTo(HashSet()) { it.id }

fun List<ReviewItem>.bytesOf(selection: Set<String>): Long = filter { it.id in selection }.sumOf { it.bytes }

sealed interface DeletionOutcome {
    data class Deleted(val count: Int, val bytes: Long) : DeletionOutcome
    data object Cancelled : DeletionOutcome
    data class Failed(val message: String) : DeletionOutcome
}

enum class CompressionPreset(val title: String, val bitsPerSecond: Double, val shortSide: Int) {
    Hd1080("1080p · High quality", 6_500_000.0, 1080),
    Hd720("720p · Smaller file", 3_500_000.0, 720),
}

/** Estimates compression savings so the UI only offers compression when it is worth it. */
object CompressionEstimator {
    /** Replace the original only when the new file is at least this much smaller. */
    const val MIN_SAVINGS_RATIO = 0.2
    const val MIN_SAVINGS_BYTES = 5_000_000L

    fun estimatedBytes(durationSec: Double, preset: CompressionPreset): Long = (preset.bitsPerSecond / 8 * durationSec).toLong()

    /** Estimated savings, or null when compressing wouldn't help (already small or low resolution). */
    fun estimatedSavings(bytes: Long, durationSec: Double, shortSide: Int, preset: CompressionPreset): Long? {
        if (durationSec <= 0) return null
        val highBitrate = bytes * 8 / durationSec > preset.bitsPerSecond * 1.5
        if (shortSide <= preset.shortSide && !highBitrate) return null
        val savings = bytes - estimatedBytes(durationSec, preset)
        return savings.takeIf { isWorthIt(bytes, it) }
    }

    /** After the fact: keep the compressed file only if it really saved enough. */
    fun shouldReplace(originalBytes: Long, compressedBytes: Long): Boolean = isWorthIt(originalBytes, originalBytes - compressedBytes)

    private fun isWorthIt(original: Long, savings: Long) = savings >= MIN_SAVINGS_BYTES && savings >= original * MIN_SAVINGS_RATIO
}
