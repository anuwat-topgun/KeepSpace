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
