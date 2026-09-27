package com.smartstorage.cleaner.model

import androidx.compose.ui.graphics.Color

// Mirrors ios/SmartStorage/Model/LibraryModels.swift — keep the two in sync.

// region Cleanup plan (04)

enum class PlanTarget { Videos, SimilarPhotos, Screenshots }

data class PlanItem(val title: String, val kind: CleanupCategory, val bytes: Long, val target: PlanTarget)

data class CleanupPlan(val targetBytes: Long, val estimatedBytes: Long, val reviewTime: String, val items: List<PlanItem>)

// endregion

// region Media placeholders

/** Stand-in artwork for media until the photo library is wired up. */
enum class ThumbnailStyle(private val light: Pair<Long, Long>, private val dark: Pair<Long, Long>) {
    Sunset(0xFFF6A96B to 0xFFE0607E, 0xFFB86F3A to 0xFF9A3A55),
    Dinner(0xFF6B4A3A to 0xFFD9A066, 0xFF4A3226 to 0xFF9C6D3F),
    Portrait(0xFFF2C6A0 to 0xFFB98B78, 0xFFA27A58 to 0xFF7A574A),
    Family(0xFF8EC5E8 to 0xFF6FA38A, 0xFF4B7FA3 to 0xFF3F6B55),
    Mountain(0xFF7FB8D8 to 0xFF3E7F6E, 0xFF3F7898 to 0xFF264F44),
    Concert(0xFF3A2A7A to 0xFFC04FC9, 0xFF251A52 to 0xFF803488),
    Cake(0xFFF5D9A6 to 0xFFC99A5B, 0xFFA88E5E to 0xFF86663A),
    Beach(0xFFF3B179 to 0xFF5A8FB0, 0xFFA8733F to 0xFF345A73),
    Baby(0xFFF6E3D4 to 0xFFE8C2A8, 0xFFA8958A to 0xFF9C7E6A),
    Screen(0xFF9FB5E8 to 0xFF6D7FB8, 0xFF51679A to 0xFF3F4D7A),
    Tokyo(0xFFA8D0F0 to 0xFFF4B8C8, 0xFF5A87A8 to 0xFFA06A7A);

    fun colors(isDark: Boolean): List<Color> {
        val pair = if (isDark) dark else light
        return listOf(Color(pair.first), Color(pair.second))
    }
}

// endregion

// region Similar photos (05) / Best shot (06)

enum class GroupIcon { Beach, Dinner, Person, Family }

data class PhotoGroup(
    val id: String,
    val title: String,
    val icon: GroupIcon,
    val photoCount: Int,
    val bytes: Long,
    val style: ThumbnailStyle,
    /** Index of the AI-recommended keeper within the group. */
    val recommendedIndex: Int,
)

enum class PhotoGroupFilter(val label: String) { All("All"), Recent("Recent"), Reviewed("Reviewed") }

enum class ReasonKind { Sharp, EyesOpen, Exposure, NoBlur }

data class BestShotReason(val title: String, val detail: String, val kind: ReasonKind)

// endregion

// region Screenshots (11)

enum class ScreenshotKind { Shopping, Receipts, Chats, QrCodes, Tickets }

data class ScreenshotCategory(val title: String, val kind: ScreenshotKind, val bytes: Long)

data class ExpiredScreenshot(val title: String, val detail: String, val status: String)

// endregion

// region Videos (08)

enum class VideoKind { Large, Recording }

enum class VideoFilter(val label: String) {
    All("All"), Large("Large"), Recordings("Recordings");

    fun includes(kind: VideoKind) = when (this) {
        All -> true
        Large -> kind == VideoKind.Large
        Recordings -> kind == VideoKind.Recording
    }
}

data class VideoItem(
    val id: String,
    val title: String,
    val bytes: Long,
    val quality: String?,
    val duration: String,
    val kind: VideoKind,
    val style: ThumbnailStyle,
    /** Personal / meaningful footage is offered for review, never compression by default. */
    val isMeaningful: Boolean,
) {
    val metadata: String get() = listOfNotNull(quality, duration).joinToString(" · ")
}

// endregion

// region Insights (07)

data class ForecastPoint(val week: Float, val usedGB: Float)

data class StorageForecast(
    val capacityGB: Float,
    val remainingBytes: Long,
    val daysUntilFull: Int,
    /** Weeks relative to today; negative = history. */
    val history: List<ForecastPoint>,
    val projection: List<ForecastPoint>,
    val photosAddedThisWeek: Int,
    val videosAddedThisWeek: Int,
    val potentialCleanupBytes: Long,
)

// endregion

// region Memories (10)

data class MemoryEvent(val title: String, val photoCount: Int, val videoCount: Int, val style: ThumbnailStyle) {
    val summary: String
        get() {
            val photos = "%,d photos".format(photoCount)
            return if (videoCount > 0) "$photos · %,d videos".format(videoCount) else photos
        }
}

// endregion

object LibraryMockData {
    private const val MB = 1_000_000L

    val cleanupPlan = CleanupPlan(
        targetBytes = 10_000 * MB,
        estimatedBytes = 10_400 * MB,
        reviewTime = "2 min 40 sec",
        items = listOf(
            PlanItem("Old Screen Recordings", CleanupCategory.ScreenRecordings, 4_800 * MB, PlanTarget.Videos),
            PlanItem("Similar Photos", CleanupCategory.SimilarPhotos, 2_700 * MB, PlanTarget.SimilarPhotos),
            PlanItem("Screenshots", CleanupCategory.Screenshots, 1_400 * MB, PlanTarget.Screenshots),
            PlanItem("Blurry Photos", CleanupCategory.BlurryPhotos, 900 * MB, PlanTarget.SimilarPhotos),
            PlanItem("Duplicate Videos", CleanupCategory.LargeVideos, 600 * MB, PlanTarget.Videos),
        ),
    )

    const val SIMILAR_BYTES = 9_800 * MB
    const val SIMILAR_GROUPS = 328

    val photoGroups = listOf(
        PhotoGroup("beach", "Beach Sunset", GroupIcon.Beach, 12, 93 * MB, ThumbnailStyle.Sunset, 2),
        PhotoGroup("dinner", "Dinner", GroupIcon.Dinner, 8, 76 * MB, ThumbnailStyle.Dinner, 0),
        PhotoGroup("portrait", "Portrait Session", GroupIcon.Person, 14, 124 * MB, ThumbnailStyle.Portrait, 3),
        PhotoGroup("family", "Family Selfie", GroupIcon.Family, 4, 38 * MB, ThumbnailStyle.Family, 1),
    )

    val bestShotReasons = listOf(
        BestShotReason("Sharpest image", "Faces and details are the clearest.", ReasonKind.Sharp),
        BestShotReason("Everyone has eyes open", "All faces are clearly visible.", ReasonKind.EyesOpen),
        BestShotReason("Best exposure", "Well-balanced lighting and natural colors.", ReasonKind.Exposure),
        BestShotReason("No visible motion blur", "Everything looks sharp and steady.", ReasonKind.NoBlur),
    )

    const val SCREENSHOTS_RECOVERABLE = 5_100 * MB

    val screenshotCategories = listOf(
        ScreenshotCategory("Shopping", ScreenshotKind.Shopping, 1_700 * MB),
        ScreenshotCategory("Receipts", ScreenshotKind.Receipts, 1_100 * MB),
        ScreenshotCategory("Chats", ScreenshotKind.Chats, 890 * MB),
        ScreenshotCategory("QR Codes", ScreenshotKind.QrCodes, 630 * MB),
        ScreenshotCategory("Tickets", ScreenshotKind.Tickets, 260 * MB),
    )

    val expiredScreenshots = listOf(ExpiredScreenshot("Boarding pass", "Bangkok → Tokyo", "Trip completed"))

    const val LARGE_VIDEO_BYTES = 18_200 * MB
    const val RECORDING_BYTES = 4_300 * MB

    val videos = listOf(
        VideoItem("trip", "Trip Recap", 2_400 * MB, "4K", "08:42", VideoKind.Large, ThumbnailStyle.Mountain, false),
        VideoItem("rec", "Screen Recording", 1_300 * MB, null, "24:15", VideoKind.Recording, ThumbnailStyle.Screen, false),
        VideoItem("concert", "Concert Clip", 980 * MB, "4K", "03:18", VideoKind.Large, ThumbnailStyle.Concert, false),
        VideoItem("vlog", "Beach Vlog", 718 * MB, "4K", "05:21", VideoKind.Large, ThumbnailStyle.Beach, false),
        VideoItem("family", "Family Moments", 654 * MB, "1080p", "04:12", VideoKind.Large, ThumbnailStyle.Baby, true),
    )

    /** 238 GB used today, ~2.7 GB/week growth → full in ~47 days. */
    val forecast = StorageForecast(
        capacityGB = 256f,
        remainingBytes = 18_000 * MB,
        daysUntilFull = 47,
        history = listOf(ForecastPoint(-4f, 227.2f), ForecastPoint(-3f, 229.9f), ForecastPoint(-2f, 232.6f), ForecastPoint(-1f, 235.3f), ForecastPoint(0f, 238f)),
        projection = listOf(ForecastPoint(0f, 238f), ForecastPoint(2f, 243.4f), ForecastPoint(4f, 248.8f), ForecastPoint(6.7f, 256f)),
        photosAddedThisWeek = 286,
        videosAddedThisWeek = 19,
        potentialCleanupBytes = 1_400 * MB,
    )

    val memories = listOf(
        MemoryEvent("Tokyo Trip", 1_284, 94, ThumbnailStyle.Tokyo),
        MemoryEvent("Birthday Party", 342, 0, ThumbnailStyle.Cake),
        MemoryEvent("Concert Night", 184, 0, ThumbnailStyle.Concert),
    )

    const val TRIP_SIMILAR_PHOTOS = 382
    const val TRIP_BLURRY_SHOTS = 67
}
