import CoreGraphics
import Foundation
import Testing
@testable import SmartStorage

// MARK: - Fixtures

private let t0 = Date(timeIntervalSince1970: 1_790_000_000)

private func item(_ id: String, _ kind: MediaItem.Kind = .photo, at seconds: TimeInterval = 0, bytes: Int64 = 1_000_000, favorite: Bool = false) -> MediaItem {
    MediaItem(id: id, kind: kind, creationDate: t0.addingTimeInterval(seconds), bytes: bytes,
              pixelWidth: 4032, pixelHeight: 3024, duration: 0, isFavorite: favorite)
}

private func features(_ print: [Float], sharpness: Double = 100, exposure: Double = 0.5, face: Double? = nil) -> ImageFeatures {
    ImageFeatures(featurePrint: print, sharpness: sharpness, exposure: exposure, faceQuality: face, faceCount: face == nil ? 0 : 1)
}

private func photo(_ id: String, at seconds: TimeInterval, print: [Float], sharpness: Double = 100, exposure: Double = 0.5,
                   face: Double? = nil, favorite: Bool = false, bytes: Int64 = 1_000_000) -> AnalyzedPhoto {
    AnalyzedPhoto(item: item(id, at: seconds, bytes: bytes, favorite: favorite),
                  features: features(print, sharpness: sharpness, exposure: exposure, face: face))
}

// MARK: - Similarity grouping

@Suite struct SimilarityGrouperTests {
    let grouper = SimilarityGrouper(maxGap: 120, maxDistance: 0.5)

    @Test func groupsLookAlikesTakenTogether() {
        let groups = grouper.groups(from: [
            photo("a", at: 0, print: [1, 0]),
            photo("b", at: 5, print: [0.9, 0.1]),
            photo("c", at: 9, print: [0.95, 0.05]),
        ])
        #expect(groups.map { $0.map(\.id) } == [["a", "b", "c"]])
    }

    @Test func splitsWhenTooFarApartInTime() {
        let groups = grouper.groups(from: [
            photo("a", at: 0, print: [1, 0]),
            photo("b", at: 5, print: [1, 0.05]),
            photo("c", at: 600, print: [1, 0.1]),
            photo("d", at: 610, print: [1, 0.15]),
        ])
        #expect(groups.map { $0.map(\.id) } == [["a", "b"], ["c", "d"]])
    }

    @Test func splitsWhenContentDiffers() {
        let groups = grouper.groups(from: [
            photo("a", at: 0, print: [1, 0]),
            photo("b", at: 5, print: [0, 1]),
        ])
        #expect(groups.isEmpty)
    }

    @Test func ignoresInputOrderAndMissingPrints() {
        let groups = grouper.groups(from: [
            photo("b", at: 5, print: [1, 0]),
            photo("x", at: 7, print: []),
            photo("a", at: 0, print: [1, 0]),
        ])
        #expect(groups.map { $0.map(\.id) } == [["a", "b"]])
    }
}

// MARK: - Best shot

@Suite struct BestShotScorerTests {
    let scorer = BestShotScorer()

    @Test func favoriteAlwaysWins() {
        let pick = scorer.pick([
            photo("a", at: 0, print: [1], sharpness: 900),
            photo("b", at: 1, print: [1], sharpness: 10, favorite: true),
        ])
        #expect(pick?.index == 1)
        #expect(pick?.reasons == [.favorite])
    }

    @Test func sharpestWinsWithoutFaces() {
        let pick = scorer.pick([
            photo("a", at: 0, print: [1], sharpness: 50),
            photo("b", at: 1, print: [1], sharpness: 400),
            photo("c", at: 2, print: [1], sharpness: 120),
        ])
        #expect(pick?.index == 1)
        #expect(pick?.reasons.contains(.sharpest) == true)
        #expect(pick?.reasons.contains(.bestFaces) == false)
    }

    @Test func faceQualityCanOutweighSmallSharpnessGap() {
        let pick = scorer.pick([
            photo("a", at: 0, print: [1], sharpness: 210, face: 0.2),
            photo("b", at: 1, print: [1], sharpness: 200, face: 0.9),
        ])
        #expect(pick?.index == 1)
        #expect(pick?.reasons.contains(.bestFaces) == true)
    }

    @Test func reasonsOnlyClaimWhatIsTrue() {
        // b is sharpest but worse exposed than a.
        let pick = scorer.pick([
            photo("a", at: 0, print: [1], sharpness: 100, exposure: 0.5),
            photo("b", at: 1, print: [1], sharpness: 900, exposure: 0.8),
        ])
        #expect(pick?.index == 1)
        #expect(pick?.reasons == [.sharpest])
    }
}

// MARK: - Blur metric

@Suite struct SharpnessTests {
    private func image(_ pixel: (Int, Int) -> UInt8, side: Int = 256) -> CGImage {
        var bytes = [UInt8](repeating: 0, count: side * side)
        for y in 0..<side { for x in 0..<side { bytes[y * side + x] = pixel(x, y) } }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        return CGImage(width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: side,
                       space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
                       provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
    }

    @Test func flatImageHasNoDetail() {
        let result = ImageAnalyzer.sharpnessAndExposure(of: image { _, _ in 128 })
        #expect(result.sharpness < 1)
        #expect(abs(result.exposure - 128.0 / 255) < 0.01)
    }

    @Test func sharpEdgesScoreHigherThanSoftGradient() {
        let checker = ImageAnalyzer.sharpnessAndExposure(of: image { x, y in (x / 8 + y / 8) % 2 == 0 ? 0 : 255 })
        let gradient = ImageAnalyzer.sharpnessAndExposure(of: image { x, _ in UInt8(x) })
        #expect(checker.sharpness > 1_000)
        #expect(gradient.sharpness < 60) // below the default blur threshold
        #expect(checker.sharpness > gradient.sharpness * 10)
    }
}

// MARK: - Report + plan

@Suite struct LibraryReportTests {
    let now = t0.addingTimeInterval(90 * 24 * 3600)

    @Test func buildsCategoriesAndSafePlan() {
        let old: TimeInterval = 0 // 90 days before `now`
        let items: [MediaItem] = [
            item("s-old", .screenshot, at: old, bytes: 2_000_000),
            item("s-new", .screenshot, at: 89 * 24 * 3600, bytes: 3_000_000),
            item("r-old", .screenRecording, at: old, bytes: 40_000_000),
            item("v-big", .video, at: old, bytes: 80_000_000),
            item("v-small", .video, at: old, bytes: 10_000_000),
        ]
        let analyzed = [
            photo("p1", at: 10, print: [1, 0], sharpness: 300, bytes: 5_000_000),
            photo("p2", at: 12, print: [1, 0], sharpness: 100, bytes: 4_000_000),
            photo("blur", at: 5_000, print: [0, 1], sharpness: 5, bytes: 3_000_000),
        ]
        let content = LibraryReportBuilder().build(
            items: items + analyzed.map(\.item), analyzed: analyzed,
            deviceTotalBytes: 128_000_000_000, deviceFreeBytes: 28_000_000_000, now: now
        )

        #expect(content.storage.usedBytes == 100_000_000_000)
        #expect(content.photoGroups.count == 1)
        #expect(content.photoGroups.first?.assetIDs[content.photoGroups.first!.recommendedIndex] == "p1")
        #expect(content.similarBytes == 4_000_000)
        #expect(content.storage.categoryBytes[.blurryPhotos] == 3_000_000)
        #expect(content.storage.categoryBytes[.largeVideos] == 80_000_000)
        #expect(content.storage.categoryBytes[.screenRecordings] == 40_000_000)
        #expect(content.screenshotsBytes == 2_000_000) // only the stale one is suggested
        #expect(content.videos.first?.assetID == "v-big")

        // Safest-first: old recordings (40 MB) alone satisfy a 30 MB target.
        let small = content.plan(for: 30_000_000)
        #expect(small.items.map(\.title) == ["Old Screen Recordings"])
        // Maximum safe cleanup takes everything eligible.
        let all = content.plan(for: nil)
        #expect(all.estimatedBytes == 40_000_000 + 4_000_000 + 2_000_000 + 3_000_000)
    }

    @Test func forecastProjectsFromRecentGrowth() {
        // 1 GB of photos per week for the last four weeks.
        let week: TimeInterval = 7 * 24 * 3600
        let items = (1...4).map { item("w\($0)", at: 90 * 24 * 3600 - Double($0) * week + 60, bytes: 1_000_000_000) }
        let content = LibraryReportBuilder().build(
            items: items, analyzed: [], deviceTotalBytes: 100_000_000_000, deviceFreeBytes: 7_000_000_000, now: now
        )
        #expect(content.forecast.daysUntilFull == 49)
        #expect(content.forecast.points.filter { !$0.isProjection }.count == 5)
    }

    @Test func noProjectionWhenNotGrowing() {
        let content = LibraryReportBuilder().build(
            items: [], analyzed: [], deviceTotalBytes: 100_000_000_000, deviceFreeBytes: 50_000_000_000, now: now
        )
        #expect(content.forecast.daysUntilFull == nil)
    }
}

@Suite struct DegenerateModelTests {
    @Test func identicalPrintsDisableGrouping() {
        // Matches what the simulator produced: all the same except one tiny outlier.
        var photos = (0..<10).map { photo("p\($0)", at: Double($0), print: [0.3, 0.4]) }
        photos.append(photo("odd", at: 11, print: [0.3001, 0.4]))
        #expect(SimilarityGrouper.printsLookDegenerate(photos))
        #expect(SimilarityGrouper().groups(from: photos).isEmpty)
    }

    @Test func realisticPrintsStillGroup() {
        let photos = (0..<5).map { photo("p\($0)", at: Double($0), print: [1, Float($0) * 0.05]) }
        #expect(!SimilarityGrouper.printsLookDegenerate(photos))
        #expect(SimilarityGrouper().groups(from: photos).count == 1)
    }
}

@Suite struct ByteFormattingTests {
    @Test(arguments: [
        (Int64(238_000_000_000), "238 GB"),
        (Int64(18_000_000_000), "18 GB"),
        (Int64(9_800_000_000), "9.8 GB"),
        (Int64(890_000_000), "890 MB"),
        (Int64(68_309), "68 KB"),
        (Int64(0), "0 MB"),
    ])
    func formats(bytes: Int64, expected: String) {
        #expect(bytes.formattedBytes == expected)
    }
}

// MARK: - Analysis cache

@Suite struct AnalysisCacheTests {
    private func entry(_ id: String, modified: Date, version: Int = analyzerVersion, sharpness: Double = 50) -> CachedAnalysis {
        CachedAnalysis(assetID: id, modifiedAt: modified, version: version,
                       features: ImageFeatures(featurePrint: [0.25, -1.5, 3], sharpness: sharpness, exposure: 0.4,
                                               faceQuality: 0.8, faceCount: 2, sceneLabel: "beach"))
    }

    @Test func plannerReusesOnlyUnchangedCurrentVersionEntries() {
        var edited = item("edited"); edited.modifiedAt = t0.addingTimeInterval(60)
        var same = item("same"); same.modifiedAt = t0
        var old = item("oldVersion"); old.modifiedAt = t0
        let fresh = item("new")
        let cached = [
            "same": entry("same", modified: t0),
            "edited": entry("edited", modified: t0),
            "oldVersion": entry("oldVersion", modified: t0, version: analyzerVersion - 1),
            "gone": entry("gone", modified: t0),
        ]
        let plan = CachePlanner.plan(photos: [same, edited, old, fresh], cached: cached)
        #expect(plan.hits.map(\.id) == ["same"])
        #expect(Set(plan.toAnalyze.map(\.id)) == ["edited", "oldVersion", "new"])
        #expect(plan.staleIDs == ["gone"])
    }

    @Test func storeRoundTripsUpsertsAndDeletes() async throws {
        let store = try AnalysisStore.make(inMemory: true)
        await store.save([entry("a", modified: t0), entry("b", modified: t0)])
        #expect(await store.count() == 2)

        // Upsert: same id overwrites instead of duplicating.
        await store.save([entry("a", modified: t0.addingTimeInterval(5), sharpness: 999)])
        let all = await store.loadAll()
        #expect(all.count == 2)
        #expect(all["a"]?.features.sharpness == 999)
        #expect(all["a"]?.modifiedAt == t0.addingTimeInterval(5))
        // Every field survives the trip, including the packed feature print.
        #expect(all["b"] == entry("b", modified: t0))

        await store.delete(ids: ["a"])
        #expect(await store.loadAll().keys.sorted() == ["b"])
    }
}

// MARK: - Review & compression

@Suite struct ReviewSetTests {
    let now = t0.addingTimeInterval(90 * 24 * 3600)

    @Test func reviewSetsProtectKeepersFavoritesAndPersonalVideos() {
        let analyzed = [
            photo("keep", at: 0, print: [1, 0], sharpness: 500),
            photo("extra", at: 2, print: [1, 0.05], sharpness: 100),
            photo("fav-blurry", at: 9_000, print: [0, 1], sharpness: 5, favorite: true),
            photo("blurry", at: 20_000, print: [0.5, 0.5], sharpness: 4),
        ]
        let items = analyzed.map(\.item) + [
            item("big-video", .video, at: 0, bytes: 900_000_000),
            item("old-shot", .screenshot, at: 0, bytes: 1_000_000),
        ]
        let content = LibraryReportBuilder().build(items: items, analyzed: analyzed,
                                                   deviceTotalBytes: 1_000_000_000_000, deviceFreeBytes: 500_000_000_000, now: now)
        let sets = content.reviewSets

        #expect(sets[.similar]?.map(\.id) == ["extra"])            // keeper never offered
        #expect(sets[.blurry]?.map(\.id) == ["blurry"])            // favourite excluded entirely
        #expect(sets[.oldScreenshots]?.map(\.id) == ["old-shot"])
        #expect(sets[.largeVideos]?.map(\.id) == ["big-video"])
        #expect(sets[.largeVideos]?.defaultSelection.isEmpty == true) // personal footage: opt-in only
        #expect(sets[.similar]?.defaultSelection == ["extra"])
        #expect(content.cleanupCandidates.first { $0.title == "Similar Photos" }?.route == .review(.similar))
    }

    @Test func selectionBytesAndKeeperRule() {
        let items = [
            ReviewItem(id: "a", bytes: 10, isVideo: false, duration: 0, createdAt: t0, preselected: true),
            ReviewItem(id: "b", bytes: 20, isVideo: false, duration: 0, createdAt: t0, preselected: false),
            ReviewItem(id: "k", bytes: 40, isVideo: false, duration: 0, createdAt: t0, preselected: true, isKeeper: true),
        ]
        #expect(items.defaultSelection == ["a"])
        #expect(items.bytes(of: ["a", "b"]) == 30)
    }
}

@Suite struct CompressionEstimatorTests {
    @Test func offersCompressionForBig4KVideos() {
        // 4K, 5 min, 2.4 GB → 1080p at ~6.5 Mbps ≈ 244 MB.
        let savings = CompressionEstimator.estimatedSavings(bytes: 2_400_000_000, duration: 300, shortSide: 2160, preset: .hd1080)
        #expect(savings != nil)
        #expect((savings ?? 0) > 2_000_000_000)
    }

    @Test func skipsVideosThatAreAlreadyEfficient() {
        // 720p at ~2 Mbps: nothing meaningful to gain at 720p.
        #expect(CompressionEstimator.estimatedSavings(bytes: 75_000_000, duration: 300, shortSide: 720, preset: .hd720) == nil)
        // Tiny clip: savings under the 5 MB floor.
        #expect(CompressionEstimator.estimatedSavings(bytes: 5_500_000, duration: 2, shortSide: 2160, preset: .hd720) == nil)
    }

    @Test func replacesOnlyWhenRealSavingsAreMeaningful() {
        #expect(CompressionEstimator.shouldReplace(originalBytes: 100_000_000, compressedBytes: 60_000_000))
        #expect(!CompressionEstimator.shouldReplace(originalBytes: 100_000_000, compressedBytes: 90_000_000)) // only 10%
        #expect(!CompressionEstimator.shouldReplace(originalBytes: 10_000_000, compressedBytes: 7_000_000))   // under 5 MB
    }
}

// MARK: - Screenshot OCR classification

@Suite struct ScreenshotClassifierTests {
    let now = Date(timeIntervalSince1970: 1_790_490_000) // 2026-09-27

    @Test func boardingPassIsATicketWithRouteAndDate() {
        let text = """
        BOARDING PASS
        PASSENGER  SOMCHAI/J MR
        FLIGHT TG 676   SEAT 32A   GATE C4
        BKK → HND
        DEPARTURE 14 SEP 2026 23:55
        """
        let info = ScreenshotClassifier.classify(text: text, hasQRCode: true, now: now)
        #expect(info.kind == .tickets)             // beats the QR code
        #expect(info.route == "BKK → HND")
        #expect(info.isExpired(now: now))           // trip was two weeks ago
    }

    @Test func upcomingTicketIsNotExpired() {
        let info = ScreenshotClassifier.classify(text: "E-TICKET  Concert  Admit One  Seat B12  2026-10-30", hasQRCode: true, now: now)
        #expect(info.kind == .tickets)
        #expect(!info.isExpired(now: now))
    }

    @Test func receiptsInEnglishAndThai() {
        let english = "BLUE BOTTLE COFFEE\nLatte $5.50\nTax $0.50\nTOTAL $6.00\nPAID VISA"
        #expect(ScreenshotClassifier.classify(text: english, hasQRCode: false, now: now).kind == .receipts)
        let thai = "ใบเสร็จรับเงิน\nกาแฟ 120.00 บาท\nภาษี 8.40\nรวมทั้งสิ้น 128.40 บาท"
        #expect(ScreenshotClassifier.classify(text: thai, hasQRCode: false, now: now).kind == .receipts)
        // What a Latin-only OCR model sees on a Thai receipt: just the amounts.
        #expect(ScreenshotClassifier.classify(text: "Central Department Store\n1 2,990.00\n195.61\n3,450.00\nVISA", hasQRCode: false, now: now).kind == .receipts)
        let transfer = "โอนเงินสำเร็จ\n27 ก.ย. 69\nจำนวนเงิน 500.00 บาท"
        #expect(ScreenshotClassifier.classify(text: transfer, hasQRCode: true, now: now).kind == .receipts)
    }

    @Test func shoppingChatsQrAndOther() {
        #expect(ScreenshotClassifier.classify(text: "Wireless Earbuds  ฿1,290  4.8 ★ 2k sold  Free shipping  Add to cart  Buy now",
                                              hasQRCode: false, now: now).kind == .shopping)
        #expect(ScreenshotClassifier.classify(text: "Mom  online\nSee you at 7?\n10:41\nOk!\n10:42\nDelivered\n10:43\n10:45",
                                              hasQRCode: false, now: now).kind == .chats)
        #expect(ScreenshotClassifier.classify(text: "Scan to pay", hasQRCode: true, now: now).kind == .qrCodes)
        #expect(ScreenshotClassifier.classify(text: "Settings  Wi-Fi  Bluetooth", hasQRCode: false, now: now).kind == .other)
        // "line" must not match inside "online" / "deadline".
        #expect(ScreenshotClassifier.classify(text: "deadline online", hasQRCode: false, now: now).kind == .other)
    }

    @Test func dateFormats() {
        let calendar = Calendar(identifier: .gregorian)
        func ymd(_ d: Date) -> [Int] {
            let c = calendar.dateComponents(in: TimeZone(identifier: "UTC")!, from: d)
            return [c.year!, c.month!, c.day!]
        }
        #expect(DateExtractor.dates(in: "2026-09-12").map(ymd) == [[2026, 9, 12]])
        #expect(DateExtractor.dates(in: "12/09/2026").map(ymd) == [[2026, 9, 12]])  // day first
        #expect(DateExtractor.dates(in: "09/25/2026").map(ymd) == [[2026, 9, 25]])  // US order when unambiguous
        #expect(DateExtractor.dates(in: "12 SEP 2026").map(ymd) == [[2026, 9, 12]])
        #expect(DateExtractor.dates(in: "Sep 12, 2026").map(ymd) == [[2026, 9, 12]])
        #expect(DateExtractor.dates(in: "12/09/2569").map(ymd) == [[2026, 9, 12]])  // Buddhist era
        #expect(DateExtractor.dates(in: "Total 12.50 Qty 3").isEmpty)
    }
}

@Suite struct ScreenshotReportTests {
    let now = t0.addingTimeInterval(90 * 24 * 3600)

    @Test func importantScreenshotsStayOutOfOldAndExpiredGetTheirOwnSet() {
        let items = [
            item("old-chat", .screenshot, at: 0, bytes: 1_000),
            item("old-receipt", .screenshot, at: 0, bytes: 2_000),
            item("old-ticket-upcoming", .screenshot, at: 0, bytes: 3_000),
            item("old-ticket-past", .screenshot, at: 0, bytes: 4_000),
            item("new-shopping", .screenshot, at: 89 * 24 * 3600, bytes: 5_000),
            item("unread", .screenshot, at: 0, bytes: 6_000),
        ]
        let info: [String: ScreenshotInfo] = [
            "old-chat": ScreenshotInfo(kind: .chats),
            "old-receipt": ScreenshotInfo(kind: .receipts),
            "old-ticket-upcoming": ScreenshotInfo(kind: .tickets, eventDate: now.addingTimeInterval(7 * 24 * 3600)),
            "old-ticket-past": ScreenshotInfo(kind: .tickets, eventDate: now.addingTimeInterval(-7 * 24 * 3600), route: "BKK → HND"),
            "new-shopping": ScreenshotInfo(kind: .shopping),
        ]
        let content = LibraryReportBuilder().build(items: items, analyzed: [], screenshotInfo: info,
                                                   deviceTotalBytes: 100_000_000_000, deviceFreeBytes: 50_000_000_000, now: now)

        // Old = stale and not a receipt/ticket; unread ones count as "other".
        #expect(Set(content.reviewSets[.oldScreenshots]?.map(\.id) ?? []) == ["old-chat", "unread"])
        #expect(content.reviewSets[.expired]?.map(\.id) == ["old-ticket-past"])
        #expect(content.reviewSets[.expired]?.defaultSelection == ["old-ticket-past"])
        #expect(content.reviewSets[.screenshots(.receipts)]?.defaultSelection.isEmpty == true) // browse only
        #expect(content.expiredScreenshots.first?.detail == "BKK → HND")
        #expect(content.expiredScreenshots.first?.title == "Boarding pass")

        let byKind = Dictionary(uniqueKeysWithValues: content.screenshotCategories.compactMap { c in c.kind.map { ($0, c.count) } })
        #expect(byKind == [.chats: 1, .receipts: 1, .tickets: 2, .shopping: 1, .other: 1])
        #expect(content.cleanupCandidates.contains { $0.route == .review(.expired) && $0.bytes == 4_000 })
    }

    @Test func screenshotPlannerReusesCachedClassifications() {
        var a = item("a", .screenshot); a.modifiedAt = t0
        var b = item("b", .screenshot); b.modifiedAt = t0.addingTimeInterval(5)
        let cached = [
            "a": CachedScreenshot(assetID: "a", modifiedAt: t0, version: screenshotReaderVersion, info: ScreenshotInfo(kind: .receipts)),
            "b": CachedScreenshot(assetID: "b", modifiedAt: t0, version: screenshotReaderVersion, info: ScreenshotInfo(kind: .chats)),
            "gone": CachedScreenshot(assetID: "gone", modifiedAt: t0, version: screenshotReaderVersion, info: ScreenshotInfo(kind: .other)),
        ]
        let plan = CachePlanner.plan(screenshots: [a, b], cached: cached)
        #expect(plan.hits == ["a": ScreenshotInfo(kind: .receipts)])
        #expect(plan.toAnalyze.map(\.id) == ["b"])   // edited since it was read
        #expect(plan.staleIDs == ["gone"])
    }

    @Test func screenshotStoreRoundTrip() async throws {
        let store = try AnalysisStore.make(inMemory: true)
        let entry = CachedScreenshot(assetID: "s", modifiedAt: t0, version: screenshotReaderVersion,
                                     info: ScreenshotInfo(kind: .tickets, eventDate: t0, route: "BKK → HND"))
        await store.saveScreenshots([entry])
        #expect(await store.loadScreenshots()["s"] == entry)
        await store.deleteScreenshots(ids: ["s"])
        #expect(await store.loadScreenshots().isEmpty)
    }
}

// MARK: - Receipt filing

@Suite struct ReceiptExtractorTests {
    let now = Date(timeIntervalSince1970: 1_790_490_000) // 2026-09-27

    private func ymd(_ d: Date?) -> String? {
        d.map { Calendar.utcGregorian.dateComponents([.year, .month, .day], from: $0) }.map { String(format: "%04d-%02d-%02d", $0.year!, $0.month!, $0.day!) }
    }

    @Test func thaiDepartmentStoreReceiptLikeMockup16() {
        let text = """
        CENTRAL
        Central Department Store
        Siam Paragon, Bangkok
        TAX INVOICE (ABB)
        1 Fashion Item      2,990.00
        1 Home Collection     350.00
        1 Gift Package        110.00
        Total               3,450.00
        VAT Included
        20260927 1234567890
        27/09/2026
        """
        let r = ReceiptExtractor.extract(from: text, now: now)
        #expect(r.merchant == "CENTRAL")
        #expect(r.amount == Decimal(3450))
        #expect(ymd(r.date) == "2026-09-27")
        #expect(r.category == .shopping)
    }

    @Test func englishCafeReceipt() {
        let text = "BLUE BOTTLE COFFEE\nLatte  $5.50\nSubtotal $5.50\nTax  $0.50\nTOTAL  $6.00\nSep 12, 2026  9:14 AM"
        let r = ReceiptExtractor.extract(from: text, now: now)
        #expect(r.merchant == "BLUE BOTTLE COFFEE")
        #expect(r.amount == Decimal(string: "6.00"))   // not the subtotal
        #expect(r.currency == "USD")
        #expect(r.category == .foodAndDrink)
        #expect(ymd(r.date) == "2026-09-12")
    }

    @Test func thaiTransferSlip() {
        let r = ReceiptExtractor.extract(from: "โอนเงินสำเร็จ\n27 ก.ย. 69  09:32\nจำนวนเงิน 500.00 บาท\nไปยัง นาย สมชาย ใจดี", now: now)
        #expect(r.merchant == "นาย สมชาย ใจดี")
        #expect(r.amount == Decimal(500))
        #expect(r.currency == "THB")
        #expect(r.category == .transfer)
        #expect(ymd(r.date) == "2026-09-27")          // Buddhist-era "69"
    }

    @Test func amountsAndDatesDontMix() {
        #expect(ReceiptExtractor.amounts(in: "12.09.2026").isEmpty)
        #expect(ReceiptExtractor.amounts(in: "฿1,290  and 3,450.00 บาท") == [Decimal(1290), Decimal(3450)])
        #expect(DateExtractor.dates(in: "1 มี.ค. 2569").compactMap(ymd) == ["2026-03-01"])
        #expect(DateExtractor.dates(in: "5 ม.ค. 70").compactMap(ymd) == ["2027-01-05"])
    }

    @Test func shortNames() {
        #expect(ReceiptExtractor.shortName("Central Department Store") == "Central")
        #expect(ReceiptExtractor.shortName("CP ALL Public Company Limited") == "Cp All")
        #expect(ReceiptExtractor.shortName("Storehouse Cafe") == "Storehouse Cafe") // whole words only
        #expect(ReceiptExtractor.shortName("นาย สมชาย ใจดี") == "สมชาย ใจดี")
        #expect(ReceiptExtractor.shortName("Mr. John Smith") == "John Smith")
        #expect(ReceiptExtractor.shortName("Mrs Cafe") == "Cafe")
        #expect(ReceiptExtractor.shortName("Nail Studio") == "Nail Studio") // "นาย"-like prefixes only as whole words
    }
}

@Suite struct FilingTemplateTests {
    let date = Calendar.utcGregorian.date(from: DateComponents(year: 2026, month: 9, day: 27, hour: 12))!

    @Test func matchesMockup16() {
        let receipt = ReceiptDetails(merchant: "Central Department Store", date: date, amount: 3450, currency: "THB", category: .shopping)
        let plan = RuleMatcher.plan(for: receipt, capturedAt: date, originalName: "IMG_0412.JPG", fileExtension: "JPG")
        #expect(plan?.rule.provider == .googleDrive)
        #expect(plan?.folder == "/Receipts/2026/09/Central/")
        #expect(plan?.fileName == "2026-09-27_Central_3450.jpg")
    }

    @Test func sanitizesAndFallsBack() {
        let values = TemplateValues(date: date, merchant: "A/B: Café*", amount: Decimal(string: "12.5"))
        #expect(TemplateResolver.folder("/Receipts/{MERCHANT}/{CATEGORY}", values) == "/Receipts/A B Café/Other/")
        #expect(TemplateResolver.fileName("{DATE}_{MERCHANT}_{AMOUNT}", values, extension: "png") == "2026-09-27_A-B-Café_12.50.png")
        let empty = TemplateValues(date: date)
        #expect(TemplateResolver.fileName("{MERCHANT}_{AMOUNT}", empty, extension: "heic") == "Unknown_0.heic")
    }

    @Test func disabledRulesAreSkipped() {
        var rules = StorageRule.defaults
        rules[0].isEnabled = false
        #expect(RuleMatcher.plan(for: ReceiptDetails(), capturedAt: date, originalName: nil, fileExtension: "jpg", rules: rules) == nil)
    }
}

@Suite struct ReceiptCacheTests {
    @Test func classifierAttachesReceiptDetailsAndCacheKeepsThem() async throws {
        let info = ScreenshotClassifier.classify(text: "BLUE BOTTLE COFFEE\nLatte $5.50\nTax $0.50\nTOTAL $6.00\nPAID", hasQRCode: false)
        #expect(info.kind == .receipts)
        #expect(info.receipt?.amount == Decimal(string: "6.00"))

        let store = try AnalysisStore.make(inMemory: true)
        let entry = CachedScreenshot(assetID: "r", modifiedAt: t0, version: screenshotReaderVersion, info: info)
        await store.saveScreenshots([entry])
        let loaded = await store.loadScreenshots()["r"]
        #expect(loaded?.info.receipt == info.receipt)   // incl. the exact Decimal amount
        #expect(loaded?.info.kind == .receipts)
    }
}

// MARK: - Storage rules

@Suite struct StorageRuleTests {
    @Test func unknownVariablesAreCaseSensitiveAndDeduplicated() {
        #expect(TemplateResolver.unknownVariables(in: "/Receipts/{YEAR}/{MONTH}/") == [])
        #expect(TemplateResolver.unknownVariables(in: "/R/{YAER}/{year}/{YAER}/") == ["{YAER}", "{year}"])
    }

    @Test func problemsAndPreview() {
        var rule = StorageRule.defaults[0]
        #expect(rule.problems.isEmpty)
        #expect(rule.preview().folder.hasPrefix("/Receipts/"))
        #expect(rule.preview().fileName.hasSuffix("_Central_3450.jpg"))
        rule.folderTemplate = "///"
        rule.fileNameTemplate = "  "
        #expect(rule.problems == [.emptyFolder, .emptyFileName])
        rule.folderTemplate = "/X/{NOPE}/"
        rule.fileNameTemplate = "{DATE}"
        #expect(rule.problems == [.unknownVariables(["{NOPE}"])])
    }

    @MainActor @Test func storePersistsEditsAndMatchesFirstEnabledRule() {
        let defaults = UserDefaults(suiteName: "rules-test-\(UUID())")!
        let store = RuleStore(defaults: defaults)
        #expect(store.rules == StorageRule.defaults)

        var custom = StorageRule(name: "Receipts to OneDrive", trigger: .receipt, provider: .oneDrive,
                                 folderTemplate: "/Finance/{YEAR}/", fileNameTemplate: "{DATE}_{AMOUNT}", afterUpload: .keepOnDevice)
        store.save(custom)
        #expect(store.rule(for: .receipt)?.provider == .googleDrive) // default still first
        store.setEnabled(false, for: StorageRule.defaults[0].id)
        #expect(store.rule(for: .receipt)?.id == custom.id)

        custom.folderTemplate = "/Finance/{YEAR}/{MONTH}/"
        store.save(custom) // update in place, no duplicate
        #expect(store.rules.filter { $0.id == custom.id }.count == 1)

        // A fresh store reads the same rules back.
        let reloaded = RuleStore(defaults: defaults)
        #expect(reloaded.rules == store.rules)
        reloaded.delete(custom.id)
        #expect(RuleStore(defaults: defaults).rules.count == StorageRule.defaults.count)
    }
}
