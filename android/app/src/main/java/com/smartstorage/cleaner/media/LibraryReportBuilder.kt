package com.smartstorage.cleaner.media

import com.smartstorage.cleaner.model.BestShotReason
import com.smartstorage.cleaner.model.CleanupCategory
import com.smartstorage.cleaner.model.ExpiredScreenshot
import com.smartstorage.cleaner.model.ForecastPoint
import com.smartstorage.cleaner.model.GroupIcon
import com.smartstorage.cleaner.model.LibraryContent
import com.smartstorage.cleaner.model.MemoryEvent
import com.smartstorage.cleaner.model.PhotoGroup
import com.smartstorage.cleaner.model.ReceiptEntry
import com.smartstorage.cleaner.model.PlanItem
import com.smartstorage.cleaner.model.PlanTarget
import com.smartstorage.cleaner.model.ReasonKind
import com.smartstorage.cleaner.model.ScreenshotCategory
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
    val eventGrouper: EventGrouper = EventGrouper(),
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
) {
    fun build(
        items: List<MediaItem>,
        analyzed: List<AnalyzedPhoto>,
        deviceTotalBytes: Long,
        deviceFreeBytes: Long,
        now: Long = System.currentTimeMillis(),
        screenshotInfo: Map<String, ScreenshotInfo> = emptyMap(),
    ): LibraryContent {
        val byId = items.associateBy { it.id }
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

        // Photos of paper receipts: read on device like receipt screenshots.
        val paperReceipts = analyzed.map { it.item }.filter { it.kind == MediaItem.Kind.Photo && screenshotInfo[it.id]?.kind == ScreenshotKind.Receipts }
        val paperIds = paperReceipts.mapTo(HashSet()) { it.id }

        // Blurry photos not already covered by a similar group; favourites and receipts are never suggested.
        val blurry = analyzed.filter { it.features.sharpness < blurThreshold && it.id !in inGroup && !it.item.isFavorite && it.id !in paperIds }
        val blurryBytes = blurry.sumOf { it.item.bytes }

        // Trips and events. Their photos are protected: blurry ones there are only listed, never preselected.
        val memories = memories(items, analyzed, groups, blurry.mapTo(HashSet()) { it.id })
        val protectedIds = memories.flatMapTo(HashSet()) { it.assetUris }

        // Screenshots: categorised by content; unread ones count as "Other" until the OCR pass reaches them.
        fun info(item: MediaItem) = screenshotInfo[item.id] ?: ScreenshotInfo(ScreenshotKind.Other)
        val expired = screenshots.filter { info(it).isExpired(now) }
        val expiredIds = expired.mapTo(HashSet()) { it.id }
        // Old screenshots never include receipts or tickets: people may need those later.
        val staleScreenshots = screenshots.filter { now - it.createdAt > staleAgeMs && !info(it).kind.isImportant && it.id !in expiredIds }
        val byKind = screenshots.groupBy { info(it).kind }
        val staleRecordings = recordings.filter { now - it.createdAt > staleAgeMs }

        val candidates = listOf(
            PlanItem("Old Screen Recordings", CleanupCategory.ScreenRecordings, staleRecordings.totalBytes, PlanTarget.Videos, staleRecordings.size, ReviewKind.OldRecordings),
            PlanItem("Similar Photos", CleanupCategory.SimilarPhotos, similarBytes, PlanTarget.SimilarPhotos, similarCount, ReviewKind.Similar),
            PlanItem("Expired Tickets", CleanupCategory.Screenshots, expired.totalBytes, PlanTarget.Screenshots, expired.size, ReviewKind.Expired),
            PlanItem("Old Screenshots", CleanupCategory.Screenshots, staleScreenshots.totalBytes, PlanTarget.Screenshots, staleScreenshots.size, ReviewKind.OldScreenshots),
            PlanItem("Blurry Photos", CleanupCategory.BlurryPhotos, blurryBytes, PlanTarget.SimilarPhotos, blurry.size, ReviewKind.Blurry),
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
            screenshotsBytes = staleScreenshots.totalBytes + expired.totalBytes,
            screenshotCategories = ScreenshotKind.entries.mapNotNull { kind ->
                byKind[kind]?.takeIf { it.isNotEmpty() }?.let { ScreenshotCategory(kind.title, kind, it.totalBytes, it.size, reviewable = true) }
            }.sortedByDescending { it.bytes },
            expiredScreenshots = expired.map { item ->
                val details = info(item)
                val isFlight = details.route != null
                ExpiredScreenshot(
                    title = if (isFlight) "Boarding pass" else "Ticket",
                    detail = details.route ?: details.eventDate?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }.orEmpty(),
                    status = if (isFlight) "Trip completed" else "Event has passed",
                    assetUri = item.id,
                )
            },
            largeVideoBytes = largeVideos.totalBytes,
            recordingBytes = recordings.totalBytes,
            videos = (videos + recordings).sortedByDescending { it.bytes }.take(maxVideosShown).map(::videoItem),
            forecast = forecast(items, deviceTotalBytes, deviceFreeBytes, potential, now),
            memories = memories,
            tripSimilarPhotos = if (memories.isEmpty()) similarCount else memories.sumOf { it.similarCount },
            tripBlurryShots = if (memories.isEmpty()) blurry.size else memories.sumOf { it.blurryCount },
            cleanupCandidates = candidates,
            reviewSets = mapOf(
                // Non-keepers from every similar group, biggest groups first.
                ReviewKind.Similar to groups.flatMap { group ->
                    group.assetUris.filterIndexed { i, _ -> i != group.recommendedIndex }.mapNotNull { byId[it]?.let { item -> review(item, true) } }
                },
                ReviewKind.Blurry to blurry.map { review(it.item, it.id !in protectedIds) },
                ReviewKind.OldScreenshots to staleScreenshots.map { review(it, true) },
                ReviewKind.Expired to expired.map { review(it, true) },
                ReviewKind.OldRecordings to staleRecordings.map { review(it, true) },
                // Personal footage: listed biggest first, never preselected.
                ReviewKind.LargeVideos to largeVideos.sortedByDescending { it.bytes }.map { review(it, false) },
            ) + byKind.map { (kind, members) ->
                // Browsing a category: nothing is selected until the user chooses.
                ReviewKind.Screenshots(kind) to members.sortedByDescending { it.createdAt }.map { review(it, false) }
            },
            receipts = (byKind[ScreenshotKind.Receipts].orEmpty() + paperReceipts).map { item ->
                ReceiptEntry(
                    item.id, info(item).receipt ?: ReceiptDetails(), item.createdAt, item.bytes, item.fileName,
                    source = if (item.kind == MediaItem.Kind.Photo) ReceiptEntry.Source.Photo else ReceiptEntry.Source.Screenshot,
                )
            }.sortedByDescending { it.details.date ?: it.capturedAt },
        )
    }

    private fun memories(items: List<MediaItem>, analyzed: List<AnalyzedPhoto>, groups: List<PhotoGroup>, blurryIds: Set<String>): List<MemoryEvent> {
        val features = analyzed.associate { it.id to it.features }
        val byId = items.associateBy { it.id }
        val candidates = items.filter { it.kind == MediaItem.Kind.Photo || it.kind == MediaItem.Kind.Video }.map {
            val f = features[it.id]
            EventCandidate(it.id, it.createdAt, it.isVideo, f?.latitude, f?.longitude)
        }
        // Extra shots of similar groups (everything but the keeper).
        val extras = groups.flatMapTo(HashSet()) { g -> g.assetUris.filterIndexed { i, _ -> i != g.recommendedIndex } }
        return eventGrouper.events(candidates, zone).map { event ->
            val ids = event.itemIds.toSet()
            // Cover: the best-looking photo — faces with eyes open first, then sharpness.
            val cover = event.itemIds.mapNotNull { id -> features[id]?.let { id to it } }
                .maxWithOrNull(compareBy<Pair<String, ImageFeatures>> { it.second.faceQuality ?: 0.0 }.thenBy { min(it.second.sharpness, 500.0) })
                ?.first ?: event.itemIds.first()
            MemoryEvent(
                title = event.title,
                photoCount = event.photoCount,
                videoCount = event.videoCount,
                style = if (event.kind == DetectedEvent.Kind.Trip) ThumbnailStyle.Mountain else ThumbnailStyle.Sunset,
                id = event.id,
                kind = if (event.kind == DetectedEvent.Kind.Trip) MemoryEvent.Kind.Trip else MemoryEvent.Kind.Event,
                start = event.start,
                end = event.end,
                distanceKm = event.distanceKm,
                coverUri = cover,
                assetUris = event.itemIds,
                bytes = event.itemIds.sumOf { byId[it]?.bytes ?: 0 },
                similarCount = ids.count { it in extras },
                blurryCount = ids.count { it in blurryIds },
            )
        }
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

    /** Favourites are protected: never preselected, whatever the set. */
    private fun review(item: MediaItem, preselected: Boolean) =
        ReviewItem(item.id, item.bytes, item.isVideo, item.durationMs, item.createdAt, preselected && !item.isFavorite)

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
            durationSec = item.durationMs / 1000.0,
            shortSide = shortSide,
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
