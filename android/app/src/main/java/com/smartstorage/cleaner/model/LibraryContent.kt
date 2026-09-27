package com.smartstorage.cleaner.model

import com.smartstorage.cleaner.media.ReviewItem
import com.smartstorage.cleaner.media.ReviewKind
import kotlin.math.roundToInt

/** Everything the screens display, from one source: a real scan or the demo data. Mirrors iOS. */
data class LibraryContent(
    val storage: StorageSummary,
    val similarBytes: Long,
    val photoGroups: List<PhotoGroup>,
    val screenshotsBytes: Long,
    val screenshotCategories: List<ScreenshotCategory>,
    val expiredScreenshots: List<ExpiredScreenshot>,
    val largeVideoBytes: Long,
    val recordingBytes: Long,
    val videos: List<VideoItem>,
    val forecast: StorageForecast,
    val memories: List<MemoryEvent>,
    val tripSimilarPhotos: Int,
    val tripBlurryShots: Int,
    /** Candidate cleanup sources, safest first; plans are assembled from these per target. */
    val cleanupCandidates: List<PlanItem>,
    /** Items behind each candidate, for the review screen. Empty for demo content. */
    val reviewSets: Map<ReviewKind, List<ReviewItem>> = emptyMap(),
) {
    /**
     * Greedily adds whole candidate categories (safest first) until the target is met.
     * `null` target = Maximum Safe Cleanup (everything eligible).
     */
    fun plan(targetBytes: Long?): CleanupPlan {
        val chosen = mutableListOf<PlanItem>()
        var total = 0L
        for (item in cleanupCandidates) {
            if (item.bytes <= 0) continue
            if (targetBytes != null && total >= targetBytes) break
            chosen += item
            total += item.bytes
        }
        // ~1.5 s per file reviewed, rounded to the nearest 10 s.
        val seconds = maxOf(10, (chosen.sumOf { it.itemCount } * 1.5 / 10).roundToInt() * 10)
        return CleanupPlan(targetBytes ?: total, total, formatDuration(seconds), chosen)
    }

    companion object {
        /** Demo data matching the mockups (previews, screenshots, emulators without photos). */
        val demo: LibraryContent
            get() = LibraryContent(
                storage = MockData.storage,
                similarBytes = LibraryMockData.SIMILAR_BYTES,
                photoGroups = LibraryMockData.photoGroups,
                screenshotsBytes = LibraryMockData.SCREENSHOTS_RECOVERABLE,
                screenshotCategories = LibraryMockData.screenshotCategories,
                expiredScreenshots = LibraryMockData.expiredScreenshots,
                largeVideoBytes = LibraryMockData.LARGE_VIDEO_BYTES,
                recordingBytes = LibraryMockData.RECORDING_BYTES,
                videos = LibraryMockData.videos,
                forecast = LibraryMockData.forecast,
                memories = LibraryMockData.memories,
                tripSimilarPhotos = LibraryMockData.TRIP_SIMILAR_PHOTOS,
                tripBlurryShots = LibraryMockData.TRIP_BLURRY_SHOTS,
                cleanupCandidates = LibraryMockData.cleanupPlan.items,
            )

        /** Before a scan finishes: real device storage, nothing else yet. */
        fun empty(storage: StorageSummary) = LibraryContent(
            storage = storage,
            similarBytes = 0,
            photoGroups = emptyList(),
            screenshotsBytes = 0,
            screenshotCategories = emptyList(),
            expiredScreenshots = emptyList(),
            largeVideoBytes = 0,
            recordingBytes = 0,
            videos = emptyList(),
            forecast = StorageForecast(storage.totalBytes / 1e9f, storage.freeBytes, null, emptyList(), emptyList(), 0, 0, 0),
            memories = emptyList(),
            tripSimilarPhotos = 0,
            tripBlurryShots = 0,
            cleanupCandidates = emptyList(),
        )

        fun formatDuration(seconds: Int): String {
            val m = seconds / 60
            val s = seconds % 60
            return when {
                m == 0 -> "$s sec"
                s == 0 -> "$m min"
                else -> "$m min $s sec"
            }
        }
    }
}
