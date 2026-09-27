package com.smartstorage.cleaner.media

import com.smartstorage.cleaner.model.BestShotReason
import com.smartstorage.cleaner.model.CleanupCategory
import com.smartstorage.cleaner.model.ForecastPoint
import com.smartstorage.cleaner.model.GroupIcon
import com.smartstorage.cleaner.model.LibraryContent
import com.smartstorage.cleaner.model.PhotoGroup
import com.smartstorage.cleaner.model.PlanItem
import com.smartstorage.cleaner.model.PlanTarget
import com.smartstorage.cleaner.model.ReasonKind
import com.smartstorage.cleaner.model.ScreenshotCategory
import com.smartstorage.cleaner.model.ScreenshotKind
import com.smartstorage.cleaner.model.StorageForecast
import com.smartstorage.cleaner.model.StorageSummary
import com.smartstorage.cleaner.model.ThumbnailStyle
import com.smartstorage.cleaner.model.VideoItem
import com.smartstorage.cleaner.model.VideoKind
import java.text.DateFormat
import java.util.Date
import kotlin.math.min
import kotlin.math.roundToInt

/** Turns raw scan output into what the screens show. Pure; mirrors `LibraryReportBuilder.swift`. */
data class LibraryReportBuilder(
    /** Below this Laplacian variance (256px grayscale) a photo reads as blurry. */
    val blurThreshold: Double = 60.0,
    /** Videos at least this big count as "large". */
    val largeVideoBytes: Long = 50_000_000,
    /** Screenshots and recordings older than this are suggested for cleanup. */
    val staleAgeMs: Long = 30L * 24 * 3600 * 1000,
    val maxGroupsShown: Int = 60,
    val maxVideosShown: Int = 50,
    val grouper: SimilarityGrouper = SimilarityGrouper(),
    val scorer: BestShotScorer = BestShotScorer(),
) {
    fun build(
        items: List<MediaItem>,
        analyzed: List<AnalyzedPhoto>,
        deviceTotalBytes: Long,
        deviceFreeBytes: Long,
        now: Long = System.currentTimeMillis(),
    ): LibraryContent {
        val screenshots = items.filter { it.kind == MediaItem.Kind.Screenshot }
        val recordings = items.filter { it.kind == MediaItem.Kind.ScreenRecording }
        val videos = items.filter { it.kind == MediaItem.Kind.Video }
        val largeVideos = videos.filter { it.bytes >= largeVideoBytes }

        val inGroup = mutableSetOf<String>()
        val groups = grouper.groups(analyzed).mapNotNull { members ->
            val pick = scorer.pick(members) ?: return@mapNotNull null
            members.forEach { inGroup += it.id }
            photoGroup(members, pick)
        }.sortedByDescending { it.reclaimableBytes }
        val similarBytes = groups.sumOf { it.reclaimableBytes }
        val similarCount = groups.sumOf { it.photoCount - 1 }

        // Blurry photos not already covered by a similar group; favourites are never suggested.
        val blurry = analyzed.filter { it.features.sharpness < blurThreshold && it.id !in inGroup && !it.item.isFavorite }
        val blurryBytes = blurry.sumOf { it.item.bytes }

        val staleScreenshots = screenshots.filter { now - it.createdAt > staleAgeMs }
        val recentScreenshots = screenshots.filter { now - it.createdAt <= staleAgeMs }
        val staleRecordings = recordings.filter { now - it.createdAt > staleAgeMs }

        val candidates = listOf(
            PlanItem("Old Screen Recordings", CleanupCategory.ScreenRecordings, staleRecordings.totalBytes, PlanTarget.Videos, staleRecordings.size),
            PlanItem("Similar Photos", CleanupCategory.SimilarPhotos, similarBytes, PlanTarget.SimilarPhotos, similarCount),
            PlanItem("Old Screenshots", CleanupCategory.Screenshots, staleScreenshots.totalBytes, PlanTarget.Screenshots, staleScreenshots.size),
            PlanItem("Blurry Photos", CleanupCategory.BlurryPhotos, blurryBytes, PlanTarget.SimilarPhotos, blurry.size),
        )
        val potential = candidates.sumOf { it.bytes }

        return LibraryContent(
            storage = StorageSummary(
                usedBytes = deviceTotalBytes - deviceFreeBytes,
                totalBytes = deviceTotalBytes,
                potentialCleanupBytes = potential,
                categoryBytes = mapOf(
                    CleanupCategory.SimilarPhotos to similarBytes,
                    CleanupCategory.Screenshots to screenshots.totalBytes,
                    CleanupCategory.LargeVideos to largeVideos.totalBytes,
                    CleanupCategory.ScreenRecordings to recordings.totalBytes,
                    CleanupCategory.BlurryPhotos to blurryBytes,
                ),
            ),
            similarBytes = similarBytes,
            photoGroups = groups.take(maxGroupsShown),
            screenshotsBytes = staleScreenshots.totalBytes,
            screenshotCategories = listOf(
                ScreenshotCategory("Older than 30 days", ScreenshotKind.Old, staleScreenshots.totalBytes),
                ScreenshotCategory("Last 30 days", ScreenshotKind.Recent, recentScreenshots.totalBytes),
            ).filter { it.bytes > 0 },
            expiredScreenshots = emptyList(),
            largeVideoBytes = largeVideos.totalBytes,
            recordingBytes = recordings.totalBytes,
            videos = (videos + recordings).sortedByDescending { it.bytes }.take(maxVideosShown).map(::videoItem),
            forecast = forecast(items, deviceTotalBytes, deviceFreeBytes, potential, now),
            memories = emptyList(),
            tripSimilarPhotos = similarCount,
            tripBlurryShots = blurry.size,
            cleanupCandidates = candidates,
        )
    }

    private fun photoGroup(members: List<AnalyzedPhoto>, pick: BestShotScorer.Pick): PhotoGroup {
        val total = members.sumOf { it.item.bytes }
        val keeper = members[pick.index]
        val hasFaces = members.any { it.features.faceCount > 0 }
        return PhotoGroup(
            id = keeper.id,
            title = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(keeper.item.createdAt)),
            icon = if (hasFaces) GroupIcon.Person else GroupIcon.Photos,
            photoCount = members.size,
            bytes = total,
            style = ThumbnailStyle.Sunset,
            recommendedIndex = pick.index,
            assetUris = members.map { it.id },
            reasons = pick.reasons.map(::reason),
            reclaimableBytes = total - keeper.item.bytes,
        )
    }

    private fun reason(reason: BestShotScorer.Reason) = when (reason) {
        BestShotScorer.Reason.Sharpest -> BestShotReason("Sharpest image", "Details are the clearest in this group.", ReasonKind.Sharp)
        BestShotScorer.Reason.BestFaces -> BestShotReason("Best faces", "Eyes open and faces clearly visible.", ReasonKind.Faces)
        BestShotScorer.Reason.BestExposure -> BestShotReason("Best exposure", "Well-balanced lighting.", ReasonKind.Exposure)
        BestShotScorer.Reason.Favorite -> BestShotReason("Your favorite", "You marked this photo as a favorite.", ReasonKind.Favorite)
    }

    private fun videoItem(item: MediaItem): VideoItem {
        val shortSide = min(item.width, item.height)
        val recording = item.kind == MediaItem.Kind.ScreenRecording
        val quality = when {
            recording -> null
            shortSide >= 2160 -> "4K"
            shortSide >= 1080 -> "1080p"
            shortSide >= 720 -> "720p"
            else -> null
        }
        val seconds = (item.durationMs / 1000.0).roundToInt()
        return VideoItem(
            id = item.id,
            title = if (recording) "Screen Recording" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.createdAt)),
            bytes = item.bytes,
            quality = quality,
            duration = "%02d:%02d".format(seconds / 60, seconds % 60),
            kind = if (recording) VideoKind.Recording else VideoKind.Large,
            style = if (recording) ThumbnailStyle.Screen else ThumbnailStyle.Mountain,
            isMeaningful = item.isFavorite,
            assetUri = item.id,
        )
    }

    /**
     * History = device usage minus media added since each point; projection = recent weekly media
     * growth extended until the device is full (capped at 12 weeks for the chart).
     */
    private fun forecast(items: List<MediaItem>, total: Long, free: Long, potential: Long, now: Long): StorageForecast {
        val week = 7L * 24 * 3600 * 1000
        val used = (total - free) / 1e9
        val history = (4 downTo 0).map { back ->
            val since = now - back * week
            ForecastPoint(-back.toFloat(), (used - items.filter { it.createdAt > since }.totalBytes / 1e9).toFloat())
        }
        val weeklyGrowth = (used - history.first().usedGB) / 4
        val projection = mutableListOf(ForecastPoint(0f, used.toFloat()))
        var daysUntilFull: Int? = null
        if (weeklyGrowth > 0.05) {
            val weeksToFull = free / 1e9 / weeklyGrowth
            daysUntilFull = (weeksToFull * 7).roundToInt()
            val end = min(weeksToFull, 12.0)
            projection += ForecastPoint(end.toFloat(), (used + weeklyGrowth * end).toFloat())
        }
        return StorageForecast(
            capacityGB = (total / 1e9).toFloat(),
            remainingBytes = free,
            daysUntilFull = daysUntilFull,
            history = history,
            projection = projection,
            photosAddedThisWeek = items.count { !it.isVideo && it.createdAt > now - week },
            videosAddedThisWeek = items.count { it.isVideo && it.createdAt > now - week },
            potentialCleanupBytes = potential,
        )
    }

    private val List<MediaItem>.totalBytes: Long get() = sumOf { it.bytes }
}
