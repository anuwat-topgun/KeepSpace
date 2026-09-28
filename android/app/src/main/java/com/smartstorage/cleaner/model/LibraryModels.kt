package com.smartstorage.cleaner.model

import androidx.compose.ui.graphics.Color
import com.smartstorage.cleaner.media.CompressionEstimator
import com.smartstorage.cleaner.media.FilingPlan
import com.smartstorage.cleaner.media.ReceiptCategory
import com.smartstorage.cleaner.media.ReceiptDetails
import com.smartstorage.cleaner.media.RuleMatcher
import com.smartstorage.cleaner.media.CompressionPreset
import com.smartstorage.cleaner.media.ReviewKind
import com.smartstorage.cleaner.media.SafetyLevel
import com.smartstorage.cleaner.media.ScreenshotKind

// Mirrors ios/SmartStorage/Model/LibraryModels.swift — keep the two in sync.

// region Cleanup plan (04)

enum class PlanTarget { Videos, SimilarPhotos, Screenshots }

data class PlanItem(
    val title: String,
    val kind: CleanupCategory,
    val bytes: Long,
    val target: PlanTarget,
    /** Number of files; drives the review-time estimate. */
    val itemCount: Int = 0,
    /** Real content opens the review grid; demo content falls back to [target]. */
    val review: ReviewKind? = null,
) {
    /** From the review set the item opens; demo items without one count as safe. */
    val safety: SafetyLevel get() = review?.safety ?: SafetyLevel.Safe
}

data class CleanupPlan(val targetBytes: Long, val estimatedBytes: Long, val reviewTime: String, val items: List<PlanItem>) {
    /** Size-weighted safety of everything in the plan, 0–100. */
    val safetyScore: Int get() = SafetyLevel.score(items.map { it.safety to it.bytes })

    /** The least safe level in the plan, for the one-line explanation. */
    val lowestSafety: SafetyLevel get() = items.filter { it.bytes > 0 }.minOfOrNull { it.safety } ?: SafetyLevel.VerySafe
}

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

enum class GroupIcon { Beach, Dinner, Person, Family, Photos }

data class PhotoGroup(
    val id: String,
    val title: String,
    val icon: GroupIcon,
    val photoCount: Int,
    val bytes: Long,
    val style: ThumbnailStyle,
    /** Index of the AI-recommended keeper within the group. */
    val recommendedIndex: Int,
    /** Real library assets (content URIs) in capture order; empty for demo groups. */
    val assetUris: List<String> = emptyList(),
    /** Why the recommended photo was picked; empty = use the generic explanation. */
    val reasons: List<BestShotReason> = emptyList(),
    /** Space recovered by keeping only the recommended photo. */
    val reclaimableBytes: Long = 0,
)

enum class PhotoGroupFilter(val label: String) { All("All"), Recent("Recent"), Reviewed("Reviewed") }

enum class ReasonKind { Sharp, EyesOpen, Exposure, NoBlur, Faces, Favorite }

data class BestShotReason(val title: String, val detail: String, val kind: ReasonKind)

// endregion

// region Screenshots (11)


data class ScreenshotCategory(
    val title: String,
    val kind: ScreenshotKind,
    val bytes: Long,
    val count: Int = 0,
    /** Real categories open their review set; demo ones don't. */
    val reviewable: Boolean = false,
)

data class ExpiredScreenshot(val title: String, val detail: String, val status: String, val assetUri: String? = null)

// endregion

// region Receipt filing (16)

data class ReceiptEntry(
    /** Content URI for real receipts; a stable demo ID otherwise. */
    val id: String,
    val details: ReceiptDetails,
    val capturedAt: Long,
    val bytes: Long,
    val fileName: String? = null,
    val source: Source = Source.Screenshot,
    /** Demo entries have no asset to show or upload. */
    val isDemo: Boolean = false,
) {
    /** [Photo] = a camera photo of a paper receipt. */
    enum class Source { Screenshot, Photo }

    val fileExtension: String get() = fileName?.substringAfterLast('.', "")?.ifEmpty { null } ?: "jpg"

    fun filingPlan(rules: List<com.smartstorage.cleaner.media.StorageRule>): FilingPlan? =
        RuleMatcher.plan(details, capturedAt, fileName, fileExtension, rules)

    val amountText: String?
        get() = details.amount?.let { amount ->
            val format = java.text.NumberFormat.getCurrencyInstance()
            runCatching { format.currency = java.util.Currency.getInstance(details.currency ?: "THB") }
            format.maximumFractionDigits = if (amount.stripTrailingZeros().scale() <= 0) 0 else 2
            format.format(amount)
        }
}

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
    val assetUri: String? = null,
    val durationSec: Double = 0.0,
    /** Shorter pixel side (1080 for 1080p), used to decide whether compression helps. */
    val shortSide: Int = 0,
) {
    val metadata: String get() = listOfNotNull(quality, duration).joinToString(" · ")

    fun estimatedSavings(preset: CompressionPreset): Long? =
        CompressionEstimator.estimatedSavings(bytes, durationSec, shortSide, preset)
}

// endregion

// region Insights (07)

data class ForecastPoint(val week: Float, val usedGB: Float)

data class StorageForecast(
    val capacityGB: Float,
    val remainingBytes: Long,
    /** null when storage isn't growing enough to project a date. */
    val daysUntilFull: Int?,
    /** Weeks relative to today; negative = history. */
    val history: List<ForecastPoint>,
    val projection: List<ForecastPoint>,
    val photosAddedThisWeek: Int,
    val videosAddedThisWeek: Int,
    val potentialCleanupBytes: Long,
)

// endregion

// region Memories (10)

data class MemoryEvent(
    val title: String,
    val photoCount: Int,
    val videoCount: Int,
    /** Placeholder art for demo memories (and while a real cover loads). */
    val style: ThumbnailStyle,
    val id: String = title,
    val kind: Kind = Kind.Event,
    /** Epoch millis. */
    val start: Long? = null,
    val end: Long? = null,
    val distanceKm: Int? = null,
    /** The best photo of the memory; null for demo memories. */
    val coverUri: String? = null,
    /** Every photo and video, in capture order. */
    val assetUris: List<String> = emptyList(),
    val bytes: Long = 0,
    /** Cleanup candidates inside the memory: extra shots from similar groups, and blurry photos. */
    val similarCount: Int = 0,
    val blurryCount: Int = 0,
) {
    enum class Kind { Trip, Event }

    val summary: String
        get() {
            val photos = "%,d %s".format(photoCount, if (photoCount == 1) "photo" else "photos")
            return if (videoCount > 0) "$photos · %,d %s".format(videoCount, if (videoCount == 1) "video" else "videos") else photos
        }

    /** "12 – 15 Sep 2026 · 540 km from home". */
    fun detail(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
        val s = start ?: return null
        val e = end ?: return null
        val format = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)
        val first = java.time.Instant.ofEpochMilli(s).atZone(zone).toLocalDate()
        val last = java.time.Instant.ofEpochMilli(e).atZone(zone).toLocalDate()
        val dates = when {
            first == last -> first.format(format)
            first.year == last.year && first.month == last.month -> "${first.dayOfMonth} – ${last.format(format)}"
            first.year == last.year -> "${first.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))} – ${last.format(format)}"
            else -> "${first.format(format)} – ${last.format(format)}"
        }
        return listOfNotNull(dates, distanceKm?.let { "%,d km from home".format(it) }).joinToString(" · ")
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
            PlanItem("Old Screen Recordings", CleanupCategory.ScreenRecordings, 4_800 * MB, PlanTarget.Videos, 12),
            PlanItem("Similar Photos", CleanupCategory.SimilarPhotos, 2_700 * MB, PlanTarget.SimilarPhotos, 60),
            PlanItem("Screenshots", CleanupCategory.Screenshots, 1_400 * MB, PlanTarget.Screenshots, 25),
            PlanItem("Blurry Photos", CleanupCategory.BlurryPhotos, 900 * MB, PlanTarget.SimilarPhotos, 7),
            PlanItem("Duplicate Videos", CleanupCategory.LargeVideos, 600 * MB, PlanTarget.Videos, 3),
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
        VideoItem("trip", "Trip Recap", 2_400 * MB, "4K", "08:42", VideoKind.Large, ThumbnailStyle.Mountain, false, durationSec = 522.0, shortSide = 2160),
        VideoItem("rec", "Screen Recording", 1_300 * MB, null, "24:15", VideoKind.Recording, ThumbnailStyle.Screen, false, durationSec = 1455.0, shortSide = 1179),
        VideoItem("concert", "Concert Clip", 980 * MB, "4K", "03:18", VideoKind.Large, ThumbnailStyle.Concert, false, durationSec = 198.0, shortSide = 2160),
        VideoItem("vlog", "Beach Vlog", 718 * MB, "4K", "05:21", VideoKind.Large, ThumbnailStyle.Beach, false, durationSec = 321.0, shortSide = 2160),
        VideoItem("family", "Family Moments", 654 * MB, "1080p", "04:12", VideoKind.Large, ThumbnailStyle.Baby, true, durationSec = 252.0, shortSide = 1080),
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

    val memories: List<MemoryEvent>
        get() {
            fun day(month: Int, day: Int) = java.time.LocalDate.of(2026, month, day).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            return listOf(
                MemoryEvent("Tokyo Trip", 1_284, 94, ThumbnailStyle.Tokyo, kind = MemoryEvent.Kind.Trip, start = day(4, 3), end = day(4, 9),
                    distanceKm = 4_600, bytes = 9_800 * MB, similarCount = 280, blurryCount = 45),
                MemoryEvent("Birthday Party", 342, 0, ThumbnailStyle.Cake, start = day(6, 14), end = day(6, 14),
                    bytes = 1_900 * MB, similarCount = 70, blurryCount = 14),
                MemoryEvent("Concert Night", 184, 0, ThumbnailStyle.Concert, start = day(8, 22), end = day(8, 22),
                    bytes = 820 * MB, similarCount = 32, blurryCount = 8),
            )
        }

    val receipts: List<ReceiptEntry>
        get() {
            val day = java.time.LocalDate.of(2026, 9, 27).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            return listOf(
                ReceiptEntry("demo-central", ReceiptDetails("Central Department Store", day, java.math.BigDecimal(3450), "THB", ReceiptCategory.Shopping),
                    day, 2_400_000, "IMG_0412.JPG", ReceiptEntry.Source.Photo, isDemo = true),
                ReceiptEntry("demo-coffee", ReceiptDetails("Blue Bottle Coffee", day - 15 * 86_400_000L, java.math.BigDecimal("6.00"), "USD", ReceiptCategory.FoodAndDrink),
                    day - 15 * 86_400_000L, 1_100_000, "IMG_0398.PNG", isDemo = true),
            )
        }

    const val TRIP_SIMILAR_PHOTOS = 382
    const val TRIP_BLURRY_SHOTS = 67
}
