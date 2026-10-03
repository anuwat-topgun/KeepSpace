package com.smartstorage.cleaner.model

import java.util.Locale

/** Cleanup categories shown on Home and in the cleanup plan. */
enum class CleanupCategory(val title: String) {
    SimilarPhotos("Similar Photos"),
    BlurryPhotos("Blurry Photos"),
    Screenshots("Screenshots"),
    LargeVideos("Large Videos"),
    ScreenRecordings("Screen Recordings"),
    /** Plan/review only; not a Home tile (Home mirrors the mockup's five categories). */
    Duplicates("Exact Duplicates"),
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

/**
 * "9.8 GB" / "18 GB" / "238 GB" / "890 MB" / "68 KB" — decimal units, one decimal only when it carries
 * information. Matches iOS `ByteCountFormatter` output so both apps show the same numbers.
 */
fun Long.formattedBytes(locale: Locale = Locale.getDefault()): String {
    val gb = this / 1e9
    return when {
        gb >= 100 -> String.format(locale, "%.0f GB", gb)
        gb >= 1 -> {
            val rounded = Math.round(gb * 10) / 10.0
            if (rounded % 1.0 == 0.0) String.format(locale, "%.0f GB", rounded) else String.format(locale, "%.1f GB", rounded)
        }
        this >= 1_000_000 -> String.format(locale, "%.0f MB", this / 1e6)
        this > 0 -> String.format(locale, "%d KB", maxOf(1L, Math.round(this / 1e3)))
        else -> "0 MB"
    }
}
