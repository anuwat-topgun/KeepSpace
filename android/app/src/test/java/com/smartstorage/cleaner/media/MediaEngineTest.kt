package com.smartstorage.cleaner.media

import com.smartstorage.cleaner.model.CleanupCategory
import com.smartstorage.cleaner.model.formattedBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Mirrors ios/SmartStorageTests/MediaEngineTests.swift.

private const val T0 = 1_790_000_000_000L
private const val DAY = 24L * 3600 * 1000

private fun item(id: String, kind: MediaItem.Kind = MediaItem.Kind.Photo, atMs: Long = 0, bytes: Long = 1_000_000, favorite: Boolean = false) =
    MediaItem(id, kind, T0 + atMs, bytes, 4032, 3024, 0, favorite)

private fun photo(
    id: String,
    atMs: Long,
    hash: Long,
    sharpness: Double = 100.0,
    exposure: Double = 0.5,
    face: Double? = null,
    favorite: Boolean = false,
    bytes: Long = 1_000_000,
) = AnalyzedPhoto(
    item(id, atMs = atMs, bytes = bytes, favorite = favorite),
    ImageFeatures(hash, sharpness, exposure, face, if (face == null) 0 else 1),
)

class SimilarityGrouperTest {
    private val grouper = SimilarityGrouper(maxGapMs = 120_000, maxDistance = 12)

    @Test fun groupsLookAlikesTakenTogether() {
        val groups = grouper.groups(listOf(photo("a", 0, 0b1111), photo("b", 5_000, 0b1110), photo("c", 9_000, 0b0111)))
        assertEquals(listOf(listOf("a", "b", "c")), groups.map { g -> g.map { it.id } })
    }

    @Test fun splitsWhenTooFarApartInTime() {
        val groups = grouper.groups(listOf(photo("a", 0, 1), photo("b", 5_000, 1), photo("c", 600_000, 1), photo("d", 610_000, 1)))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), groups.map { g -> g.map { it.id } })
    }

    @Test fun splitsWhenContentDiffers() {
        assertTrue(grouper.groups(listOf(photo("a", 0, 0L), photo("b", 5_000, -1L))).isEmpty()) // 64 bits apart
    }

    @Test fun ignoresInputOrder() {
        val groups = grouper.groups(listOf(photo("b", 5_000, 3), photo("a", 0, 3)))
        assertEquals(listOf(listOf("a", "b")), groups.map { g -> g.map { it.id } })
    }
}

class BestShotScorerTest {
    private val scorer = BestShotScorer()

    @Test fun favoriteAlwaysWins() {
        val pick = scorer.pick(listOf(photo("a", 0, 0, sharpness = 900.0), photo("b", 1, 0, sharpness = 10.0, favorite = true)))!!
        assertEquals(1, pick.index)
        assertEquals(listOf(BestShotScorer.Reason.Favorite), pick.reasons)
    }

    @Test fun sharpestWinsWithoutFaces() {
        val pick = scorer.pick(listOf(photo("a", 0, 0, sharpness = 50.0), photo("b", 1, 0, sharpness = 400.0), photo("c", 2, 0, sharpness = 120.0)))!!
        assertEquals(1, pick.index)
        assertTrue(BestShotScorer.Reason.Sharpest in pick.reasons)
        assertTrue(BestShotScorer.Reason.BestFaces !in pick.reasons)
    }

    @Test fun faceQualityCanOutweighSmallSharpnessGap() {
        val pick = scorer.pick(listOf(photo("a", 0, 0, sharpness = 210.0, face = 0.2), photo("b", 1, 0, sharpness = 200.0, face = 0.9)))!!
        assertEquals(1, pick.index)
        assertTrue(BestShotScorer.Reason.BestFaces in pick.reasons)
    }

    @Test fun reasonsOnlyClaimWhatIsTrue() {
        val pick = scorer.pick(listOf(photo("a", 0, 0, sharpness = 100.0, exposure = 0.5), photo("b", 1, 0, sharpness = 900.0, exposure = 0.8)))!!
        assertEquals(1, pick.index)
        assertEquals(listOf(BestShotScorer.Reason.Sharpest), pick.reasons)
    }
}

class ImageMetricsTest {
    private val side = 256

    private fun image(pixel: (Int, Int) -> Int) = IntArray(side * side) { pixel(it % side, it / side) }

    @Test fun flatImageHasNoDetail() {
        val flat = image { _, _ -> 128 }
        assertTrue(ImageMetrics.laplacianVariance(flat, side) < 1)
        assertEquals(128 / 255.0, ImageMetrics.exposure(flat), 0.01)
    }

    @Test fun sharpEdgesScoreHigherThanSoftGradient() {
        val checker = ImageMetrics.laplacianVariance(image { x, y -> if ((x / 8 + y / 8) % 2 == 0) 0 else 255 }, side)
        val gradient = ImageMetrics.laplacianVariance(image { x, _ -> x }, side)
        assertTrue(checker > 1_000)
        assertTrue(gradient < 60) // below the default blur threshold
    }

    @Test fun dHashIsStableUnderBrightnessAndSensitiveToContent() {
        val base = IntArray(72) { (it % 9) * 20 + (it / 9) * 3 }
        val brighter = IntArray(72) { base[it] + 25 }
        val mirrored = IntArray(72) { base[(it / 9) * 9 + (8 - it % 9)] }
        assertEquals(0, java.lang.Long.bitCount(ImageMetrics.dHash(base) xor ImageMetrics.dHash(brighter)))
        assertTrue(java.lang.Long.bitCount(ImageMetrics.dHash(base) xor ImageMetrics.dHash(mirrored)) > 32)
    }
}

class LibraryReportTest {
    private val now = T0 + 90 * DAY

    @Test fun buildsCategoriesAndSafePlan() {
        val items = listOf(
            item("s-old", MediaItem.Kind.Screenshot, 0, 2_000_000),
            item("s-new", MediaItem.Kind.Screenshot, 89 * DAY, 3_000_000),
            item("r-old", MediaItem.Kind.ScreenRecording, 0, 40_000_000),
            item("v-big", MediaItem.Kind.Video, 0, 80_000_000),
            item("v-small", MediaItem.Kind.Video, 0, 10_000_000),
        )
        val analyzed = listOf(
            photo("p1", 10_000, 7, sharpness = 300.0, bytes = 5_000_000),
            photo("p2", 12_000, 7, sharpness = 100.0, bytes = 4_000_000),
            photo("blur", 5_000_000, -1L, sharpness = 5.0, bytes = 3_000_000),
        )
        val content = LibraryReportBuilder().build(items + analyzed.map { it.item }, analyzed, 128_000_000_000, 28_000_000_000, now)

        assertEquals(100_000_000_000, content.storage.usedBytes)
        assertEquals(1, content.photoGroups.size)
        val group = content.photoGroups.first()
        assertEquals("p1", group.assetUris[group.recommendedIndex])
        assertEquals(4_000_000L, content.similarBytes)
        assertEquals(3_000_000L, content.storage.categoryBytes[CleanupCategory.BlurryPhotos])
        assertEquals(80_000_000L, content.storage.categoryBytes[CleanupCategory.LargeVideos])
        assertEquals(40_000_000L, content.storage.categoryBytes[CleanupCategory.ScreenRecordings])
        assertEquals(2_000_000L, content.screenshotsBytes)
        assertEquals("v-big", content.videos.first().assetUri)

        assertEquals(listOf("Old Screen Recordings"), content.plan(30_000_000).items.map { it.title })
        assertEquals(40_000_000L + 4_000_000 + 2_000_000 + 3_000_000, content.plan(null).estimatedBytes)
    }

    @Test fun forecastProjectsFromRecentGrowth() {
        val week = 7 * DAY
        val items = (1..4).map { item("w$it", atMs = 90 * DAY - it * week + 60_000, bytes = 1_000_000_000) }
        val content = LibraryReportBuilder().build(items, emptyList(), 100_000_000_000, 7_000_000_000, now)
        assertEquals(49, content.forecast.daysUntilFull)
        assertEquals(5, content.forecast.history.size)
    }

    @Test fun noProjectionWhenNotGrowing() {
        val content = LibraryReportBuilder().build(emptyList(), emptyList(), 100_000_000_000, 50_000_000_000, now)
        assertNull(content.forecast.daysUntilFull)
    }
}

class ByteFormattingTest {
    @Test fun formatsLikeIos() {
        mapOf(
            238_000_000_000L to "238 GB",
            18_000_000_000L to "18 GB",
            9_800_000_000L to "9.8 GB",
            890_000_000L to "890 MB",
            68_309L to "68 KB",
            0L to "0 MB",
        ).forEach { (bytes, expected) -> assertEquals(expected, bytes.formattedBytes(java.util.Locale.US)) }
    }
}
