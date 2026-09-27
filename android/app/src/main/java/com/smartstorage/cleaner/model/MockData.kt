package com.smartstorage.cleaner.model

import java.util.Locale

/** Cleanup categories shown on Home and in the cleanup plan. */
enum class CleanupCategory(val title: String) {
    SimilarPhotos("Similar Photos"),
    Screenshots("Screenshots"),
    LargeVideos("Large Videos"),
    ScreenRecordings("Screen Recordings"),
    BlurryPhotos("Blurry Photos"),
}

data class StorageSummary(
    val usedBytes: Long,
    val totalBytes: Long,
    val potentialCleanupBytes: Long,
    val categoryBytes: Map<CleanupCategory, Long>,
) {
    val freeBytes: Long get() = totalBytes - usedBytes
    val usedFraction: Float get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

/** Placeholder data matching the mockups until the media engine exists. */
object MockData {
    private const val GB = 1_000_000_000L

    val storage = StorageSummary(
        usedBytes = 238 * GB,
        totalBytes = 256 * GB,
        potentialCleanupBytes = 42_700_000_000L,
        categoryBytes = mapOf(
            CleanupCategory.SimilarPhotos to 9_800_000_000L,
            CleanupCategory.Screenshots to 5_100_000_000L,
            CleanupCategory.LargeVideos to 18_200_000_000L,
            CleanupCategory.ScreenRecordings to 4_300_000_000L,
            CleanupCategory.BlurryPhotos to 2_200_000_000L,
        ),
    )
}

/** "9.8 GB" / "238 GB" / "890 MB" — decimal units to match the mockups and system storage screens. */
fun Long.formattedBytes(locale: Locale = Locale.getDefault()): String {
    val gb = this / 1e9
    return when {
        gb >= 100 -> String.format(locale, "%.0f GB", gb)
        gb >= 1 -> String.format(locale, "%.1f GB", gb)
        else -> String.format(locale, "%.0f MB", this / 1e6)
    }
}
