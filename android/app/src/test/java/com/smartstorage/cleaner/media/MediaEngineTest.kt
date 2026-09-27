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

class CachePlannerTest {
    private fun entry(id: String, modified: Long, version: Int = ANALYZER_VERSION) =
        CachedAnalysis(id, modified, version, ImageFeatures(7, 50.0, 0.4, 0.8, 2))

    @Test fun reusesOnlyUnchangedCurrentVersionEntries() {
        val photos = listOf(
            item("same").copy(modifiedAt = 1_000),
            item("edited").copy(modifiedAt = 2_000),
            item("oldVersion").copy(modifiedAt = 1_000),
            item("new"),
        )
        val cached = mapOf(
            "same" to entry("same", 1_000),
            "edited" to entry("edited", 1_000),
            "oldVersion" to entry("oldVersion", 1_000, ANALYZER_VERSION - 1),
            "gone" to entry("gone", 1_000),
        )
        val plan = CachePlanner.plan(photos, cached)
        assertEquals(listOf("same"), plan.hits.map { it.id })
        assertEquals(setOf("edited", "oldVersion", "new"), plan.toAnalyze.map { it.id }.toSet())
        assertEquals(listOf("gone"), plan.staleIds)
    }

    @Test fun entityRoundTripKeepsEveryField() {
        val original = CachedAnalysis("content://media/1", 42, ANALYZER_VERSION, ImageFeatures(-123456789L, 12.5, 0.33, null, 0))
        assertEquals(original, AnalysisEntity.from(original).toCached())
    }
}

class ReviewSetTest {
    private val now = T0 + 90 * DAY

    @Test fun reviewSetsProtectKeepersFavoritesAndPersonalVideos() {
        val analyzed = listOf(
            photo("keep", 0, 1, sharpness = 500.0),
            photo("extra", 2_000, 1, sharpness = 100.0),
            photo("fav-blurry", 9_000_000, -1L, sharpness = 5.0, favorite = true),
            photo("blurry", 20_000_000, 0x0F0F0F0F0F0F0F0FL, sharpness = 4.0),
        )
        val items = analyzed.map { it.item } + listOf(
            item("big-video", MediaItem.Kind.Video, 0, 900_000_000),
            item("old-shot", MediaItem.Kind.Screenshot, 0, 1_000_000),
        )
        val sets = LibraryReportBuilder().build(items, analyzed, 1_000_000_000_000, 500_000_000_000, now).reviewSets

        assertEquals(listOf("extra"), sets[ReviewKind.Similar]?.map { it.id })       // keeper never offered
        assertEquals(listOf("blurry"), sets[ReviewKind.Blurry]?.map { it.id })       // favourite excluded
        assertEquals(listOf("old-shot"), sets[ReviewKind.OldScreenshots]?.map { it.id })
        assertTrue(sets[ReviewKind.LargeVideos].orEmpty().defaultSelection.isEmpty()) // personal footage: opt-in
        assertEquals(setOf("extra"), sets[ReviewKind.Similar].orEmpty().defaultSelection)
    }

    @Test fun selectionBytesAndKeeperRule() {
        val items = listOf(
            ReviewItem("a", 10, false, 0, 0, preselected = true),
            ReviewItem("b", 20, false, 0, 0, preselected = false),
            ReviewItem("k", 40, false, 0, 0, preselected = true, isKeeper = true),
        )
        assertEquals(setOf("a"), items.defaultSelection)
        assertEquals(30L, items.bytesOf(setOf("a", "b")))
    }
}

class CompressionTest {
    @Test fun offersCompressionForBig4KVideos() {
        val savings = CompressionEstimator.estimatedSavings(2_400_000_000, 300.0, 2160, CompressionPreset.Hd1080)
        assertTrue(savings != null && savings > 2_000_000_000)
    }

    @Test fun skipsVideosThatAreAlreadyEfficient() {
        assertNull(CompressionEstimator.estimatedSavings(75_000_000, 300.0, 720, CompressionPreset.Hd720))
        assertNull(CompressionEstimator.estimatedSavings(5_500_000, 2.0, 2160, CompressionPreset.Hd720))
    }

    @Test fun replacesOnlyWhenRealSavingsAreMeaningful() {
        assertTrue(CompressionEstimator.shouldReplace(100_000_000, 60_000_000))
        assertTrue(!CompressionEstimator.shouldReplace(100_000_000, 90_000_000))
        assertTrue(!CompressionEstimator.shouldReplace(10_000_000, 7_000_000))
    }

    @Test fun targetSizeKeepsAspectNeverUpscalesAndStaysEven() {
        assertEquals(1280 to 720, MediaActions.targetSize(1920, 1080, 720))   // landscape
        assertEquals(720 to 1280, MediaActions.targetSize(1080, 1920, 720))   // portrait: short side is width
        assertEquals(640 to 360, MediaActions.targetSize(640, 360, 720))      // never upscale
        val (w, h) = MediaActions.targetSize(1918, 1078, 720)
        assertTrue(w % 2 == 0 && h % 2 == 0)
    }
}

class ScreenshotClassifierTest {
    private val now = 1_790_490_000_000L // 2026-09-27

    @Test fun boardingPassIsATicketWithRouteAndDate() {
        val text = "BOARDING PASS\nPASSENGER  SOMCHAI/J MR\nFLIGHT TG 676   SEAT 32A   GATE C4\nBKK → HND\nDEPARTURE 14 SEP 2026 23:55"
        val info = ScreenshotClassifier.classify(text, hasQrCode = true, now = now)
        assertEquals(ScreenshotKind.Tickets, info.kind)
        assertEquals("BKK → HND", info.route)
        assertTrue(info.isExpired(now))
    }

    @Test fun upcomingTicketIsNotExpired() {
        val info = ScreenshotClassifier.classify("E-TICKET  Concert  Admit One  Seat B12  2026-10-30", true, now)
        assertEquals(ScreenshotKind.Tickets, info.kind)
        assertTrue(!info.isExpired(now))
    }

    @Test fun receiptsInEnglishAndThai() {
        assertEquals(ScreenshotKind.Receipts, ScreenshotClassifier.classify("BLUE BOTTLE COFFEE\nLatte \$5.50\nTax \$0.50\nTOTAL \$6.00\nPAID VISA", false, now).kind)
        assertEquals(ScreenshotKind.Receipts, ScreenshotClassifier.classify("ใบเสร็จรับเงิน\nกาแฟ 120.00 บาท\nภาษี 8.40\nรวมทั้งสิ้น 128.40 บาท", false, now).kind)
        assertEquals(ScreenshotKind.Receipts, ScreenshotClassifier.classify("โอนเงินสำเร็จ\n27 ก.ย. 69\nจำนวนเงิน 500.00 บาท", true, now).kind)
        // What the Latin-only OCR model sees on a Thai receipt: just the amounts.
        assertEquals(ScreenshotKind.Receipts, ScreenshotClassifier.classify("Central Department Store\n1 2,990.00\n195.61\n3,450.00\nVISA", false, now).kind)
    }

    @Test fun shoppingChatsQrAndOther() {
        assertEquals(ScreenshotKind.Shopping, ScreenshotClassifier.classify("Wireless Earbuds  ฿1,290  4.8 ★ 2k sold  Free shipping  Add to cart  Buy now", false, now).kind)
        assertEquals(ScreenshotKind.Chats, ScreenshotClassifier.classify("Mom  online\nSee you at 7?\n10:41\nOk!\n10:42\nDelivered\n10:43\n10:45", false, now).kind)
        assertEquals(ScreenshotKind.QrCodes, ScreenshotClassifier.classify("Scan to pay", true, now).kind)
        assertEquals(ScreenshotKind.Other, ScreenshotClassifier.classify("Settings  Wi-Fi  Bluetooth", false, now).kind)
        assertEquals(ScreenshotKind.Other, ScreenshotClassifier.classify("deadline online", false, now).kind)
    }

    @Test fun dateFormats() {
        fun ymd(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
        assertEquals(listOf("2026-09-12"), DateExtractor.dates("2026-09-12").map(::ymd))
        assertEquals(listOf("2026-09-12"), DateExtractor.dates("12/09/2026").map(::ymd))
        assertEquals(listOf("2026-09-25"), DateExtractor.dates("09/25/2026").map(::ymd))
        assertEquals(listOf("2026-09-12"), DateExtractor.dates("12 SEP 2026").map(::ymd))
        assertEquals(listOf("2026-09-12"), DateExtractor.dates("Sep 12, 2026").map(::ymd))
        assertEquals(listOf("2026-09-12"), DateExtractor.dates("12/09/2569").map(::ymd))
        assertTrue(DateExtractor.dates("Total 12.50 Qty 3").isEmpty())
    }
}

class ScreenshotReportTest {
    private val now = T0 + 90 * DAY

    @Test fun importantScreenshotsStayOutOfOldAndExpiredGetTheirOwnSet() {
        val items = listOf(
            item("old-chat", MediaItem.Kind.Screenshot, 0, 1_000),
            item("old-receipt", MediaItem.Kind.Screenshot, 0, 2_000),
            item("old-ticket-upcoming", MediaItem.Kind.Screenshot, 0, 3_000),
            item("old-ticket-past", MediaItem.Kind.Screenshot, 0, 4_000),
            item("new-shopping", MediaItem.Kind.Screenshot, 89 * DAY, 5_000),
            item("unread", MediaItem.Kind.Screenshot, 0, 6_000),
        )
        val info = mapOf(
            "old-chat" to ScreenshotInfo(ScreenshotKind.Chats),
            "old-receipt" to ScreenshotInfo(ScreenshotKind.Receipts),
            "old-ticket-upcoming" to ScreenshotInfo(ScreenshotKind.Tickets, now + 7 * DAY),
            "old-ticket-past" to ScreenshotInfo(ScreenshotKind.Tickets, now - 7 * DAY, "BKK → HND"),
            "new-shopping" to ScreenshotInfo(ScreenshotKind.Shopping),
        )
        val content = LibraryReportBuilder().build(items, emptyList(), 100_000_000_000, 50_000_000_000, now, screenshotInfo = info)

        assertEquals(setOf("old-chat", "unread"), content.reviewSets[ReviewKind.OldScreenshots].orEmpty().map { it.id }.toSet())
        assertEquals(listOf("old-ticket-past"), content.reviewSets[ReviewKind.Expired]?.map { it.id })
        assertEquals(setOf("old-ticket-past"), content.reviewSets[ReviewKind.Expired].orEmpty().defaultSelection)
        assertTrue(content.reviewSets[ReviewKind.Screenshots(ScreenshotKind.Receipts)].orEmpty().defaultSelection.isEmpty())
        assertEquals("BKK → HND", content.expiredScreenshots.first().detail)
        assertEquals(
            mapOf(ScreenshotKind.Chats to 1, ScreenshotKind.Receipts to 1, ScreenshotKind.Tickets to 2, ScreenshotKind.Shopping to 1, ScreenshotKind.Other to 1),
            content.screenshotCategories.associate { it.kind to it.count },
        )
        assertTrue(content.cleanupCandidates.any { it.review == ReviewKind.Expired && it.bytes == 4_000L })
    }

    @Test fun plannerReusesCachedClassifications() {
        val a = item("a", MediaItem.Kind.Screenshot).copy(modifiedAt = 1_000)
        val b = item("b", MediaItem.Kind.Screenshot).copy(modifiedAt = 2_000)
        val cached = mapOf(
            "a" to CachedScreenshot("a", 1_000, SCREENSHOT_READER_VERSION, ScreenshotInfo(ScreenshotKind.Receipts)),
            "b" to CachedScreenshot("b", 1_000, SCREENSHOT_READER_VERSION, ScreenshotInfo(ScreenshotKind.Chats)),
            "gone" to CachedScreenshot("gone", 1_000, SCREENSHOT_READER_VERSION, ScreenshotInfo(ScreenshotKind.Other)),
        )
        val plan = CachePlanner.planScreenshots(listOf(a, b), cached)
        assertEquals(mapOf("a" to ScreenshotInfo(ScreenshotKind.Receipts)), plan.hits)
        assertEquals(listOf("b"), plan.toAnalyze.map { it.id })
        assertEquals(listOf("gone"), plan.staleIds)
    }

    @Test fun reviewKindKeysRoundTrip() {
        val kinds = listOf(ReviewKind.Similar, ReviewKind.Expired, ReviewKind.Screenshots(ScreenshotKind.Receipts))
        kinds.forEach { assertEquals(it, ReviewKind.fromKey(it.key)) }
        val entity = ScreenshotEntity.from(CachedScreenshot("s", 1, SCREENSHOT_READER_VERSION, ScreenshotInfo(ScreenshotKind.Tickets, 42, "BKK → HND")))
        assertEquals(ScreenshotInfo(ScreenshotKind.Tickets, 42, "BKK → HND"), entity.toCached().info)
    }
}

class ReceiptExtractorTest {
    private val now = 1_790_490_000_000L // 2026-09-27
    private fun ymd(ms: Long?) = ms?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }

    @Test fun thaiDepartmentStoreReceiptLikeMockup16() {
        val text = "CENTRAL\nCentral Department Store\nSiam Paragon, Bangkok\nTAX INVOICE (ABB)\n1 Fashion Item      2,990.00\n" +
            "1 Home Collection     350.00\n1 Gift Package        110.00\nTotal               3,450.00\nVAT Included\n20260927 1234567890\n27/09/2026"
        val r = ReceiptExtractor.extract(text, now)
        assertEquals("CENTRAL", r.merchant)
        assertEquals(0, java.math.BigDecimal(3450).compareTo(r.amount))
        assertEquals("2026-09-27", ymd(r.date))
        assertEquals(ReceiptCategory.Shopping, r.category)
    }

    @Test fun englishCafeReceipt() {
        val r = ReceiptExtractor.extract("BLUE BOTTLE COFFEE\nLatte  \$5.50\nSubtotal \$5.50\nTax  \$0.50\nTOTAL  \$6.00\nSep 12, 2026  9:14 AM", now)
        assertEquals("BLUE BOTTLE COFFEE", r.merchant)
        assertEquals(java.math.BigDecimal("6.00"), r.amount)
        assertEquals("USD", r.currency)
        assertEquals(ReceiptCategory.FoodAndDrink, r.category)
        assertEquals("2026-09-12", ymd(r.date))
    }

    @Test fun thaiTransferSlip() {
        val r = ReceiptExtractor.extract("โอนเงินสำเร็จ\n27 ก.ย. 69  09:32\nจำนวนเงิน 500.00 บาท\nไปยัง นาย สมชาย ใจดี", now)
        assertEquals("นาย สมชาย ใจดี", r.merchant)
        assertEquals(0, java.math.BigDecimal(500).compareTo(r.amount))
        assertEquals("THB", r.currency)
        assertEquals(ReceiptCategory.Transfer, r.category)
        assertEquals("2026-09-27", ymd(r.date))
    }

    @Test fun amountsDatesAndNames() {
        assertTrue(ReceiptExtractor.amounts("12.09.2026").isEmpty())
        assertEquals(listOf(java.math.BigDecimal(1290), java.math.BigDecimal("3450.00")), ReceiptExtractor.amounts("฿1,290  and 3,450.00 บาท"))
        assertEquals(listOf("2026-03-01"), DateExtractor.dates("1 มี.ค. 2569").map { ymd(it) })
        assertEquals(listOf("2027-01-05"), DateExtractor.dates("5 ม.ค. 70").map { ymd(it) })
        assertEquals("Central", ReceiptExtractor.shortName("Central Department Store"))
        assertEquals("Storehouse Cafe", ReceiptExtractor.shortName("Storehouse Cafe"))
        assertEquals("สมชาย ใจดี", ReceiptExtractor.shortName("นาย สมชาย ใจดี"))
        assertEquals("John Smith", ReceiptExtractor.shortName("Mr. John Smith"))
        assertEquals("Nail Studio", ReceiptExtractor.shortName("Nail Studio"))
    }
}

class FilingTemplateTest {
    private val date = java.time.LocalDate.of(2026, 9, 27).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

    @Test fun matchesMockup16() {
        val receipt = ReceiptDetails("Central Department Store", date, java.math.BigDecimal(3450), "THB", ReceiptCategory.Shopping)
        val plan = RuleMatcher.plan(receipt, date, "IMG_0412.JPG", "JPG")!!
        assertEquals(CloudProvider.GoogleDrive, plan.rule.provider)
        assertEquals("/Receipts/2026/09/Central/", plan.folder)
        assertEquals("2026-09-27_Central_3450.jpg", plan.fileName)
    }

    @Test fun sanitizesAndFallsBack() {
        val values = TemplateValues(date, merchant = "A/B: Café*", amount = java.math.BigDecimal("12.5"))
        assertEquals("/Receipts/A B Café/Other/", TemplateResolver.folder("/Receipts/{MERCHANT}/{CATEGORY}", values))
        assertEquals("2026-09-27_A-B-Café_12.50.png", TemplateResolver.fileName("{DATE}_{MERCHANT}_{AMOUNT}", values, "png"))
        assertEquals("Unknown_0.heic", TemplateResolver.fileName("{MERCHANT}_{AMOUNT}", TemplateValues(date), "heic"))
    }

    @Test fun disabledRulesAreSkippedAndCacheKeepsReceipts() {
        val rules = StorageRule.defaults.mapIndexed { i, r -> if (i == 0) r.copy(isEnabled = false) else r }
        assertNull(RuleMatcher.plan(ReceiptDetails(), date, null, "jpg", rules))

        val info = ScreenshotClassifier.classify("BLUE BOTTLE COFFEE\nLatte \$5.50\nTax \$0.50\nTOTAL \$6.00\nPAID", false)
        assertEquals(ScreenshotKind.Receipts, info.kind)
        val entity = ScreenshotEntity.from(CachedScreenshot("r", 1, SCREENSHOT_READER_VERSION, info))
        assertEquals(info.receipt, entity.toCached().info.receipt)
    }

    // Storage rules

    @Test fun unknownVariablesAreCaseSensitive() {
        assertEquals(listOf("{year}", "{YAER}"), TemplateResolver.unknownVariables("/R/{year}/{YAER}/{MONTH}/{year}"))
        assertTrue(TemplateResolver.unknownVariables(RuleTrigger.Receipt.suggestedFolder).isEmpty())
    }

    @Test fun ruleProblemsFlagEmptyAndUnknown() {
        val base = StorageRule.defaults.first()
        assertTrue(base.problems.isEmpty())
        val bad = base.copy(folderTemplate = "///", fileNameTemplate = " ", afterUpload = AfterUploadAction.KeepOnDevice)
        assertEquals(listOf(RuleProblem.EmptyFolder, RuleProblem.EmptyFileName), bad.problems)
        assertEquals(listOf(RuleProblem.UnknownVariables(listOf("{FOO}"))), base.copy(fileNameTemplate = "{FOO}").problems)
    }

    @Test fun receiptRulePreviewUsesSampleReceipt() {
        val millis = java.time.LocalDate.of(2026, 9, 27).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val plan = StorageRule.defaults.first().preview(millis)
        assertEquals("/Receipts/2026/09/Central/2026-09-27_Central_3450.jpg", plan.fullPath)
    }

    @Test fun ruleCodecRoundTrips() {
        val rules = StorageRule.defaults + StorageRule(name = "Old recordings", trigger = RuleTrigger.ScreenRecording,
            provider = CloudProvider.OneDrive, folderTemplate = "/Rec/{YEAR}/", fileNameTemplate = "{ORIGINAL_NAME}",
            afterUpload = AfterUploadAction.DeleteAfter30Days, isEnabled = false)
        assertEquals(rules, RuleCodec.decode(RuleCodec.encode(rules)))
        assertNull(RuleCodec.decode("not json"))
    }

    @Test fun matcherSkipsDisabledReceiptRule() {
        val receipt = ReceiptExtractor.extract("Central\nTotal 100.00")
        val disabled = StorageRule.defaults.map { if (it.trigger == RuleTrigger.Receipt) it.copy(isEnabled = false) else it }
        assertNull(RuleMatcher.plan(receipt, 0, "a.jpg", "jpg", disabled))
    }
}
