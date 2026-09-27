import SwiftUI

// MARK: - Cleanup plan (04)

struct PlanItem: Identifiable, Sendable {
    let title: String
    let systemImage: String
    let tint: Tint
    let bytes: Int64
    let route: Route
    /// Number of files in this item; drives the review-time estimate.
    var itemCount: Int = 0

    var id: String { title }
}

struct CleanupPlan: Sendable {
    let targetBytes: Int64
    let estimatedBytes: Int64
    let reviewTime: String
    let items: [PlanItem]
}

// MARK: - Media placeholders

/// Stand-in artwork for media until the photo library is wired up. Each style is a soft
/// gradient + symbol that evokes the mockup photo it replaces.
enum ThumbnailStyle: String, CaseIterable, Hashable, Sendable {
    case sunset, dinner, portrait, family, mountain, concert, cake, beach, baby, screen, tokyo, boardingPass

    var colors: [Color] {
        switch self {
        case .sunset: [Color(light: 0xF6A96B, dark: 0xB86F3A), Color(light: 0xE0607E, dark: 0x9A3A55)]
        case .dinner: [Color(light: 0x6B4A3A, dark: 0x4A3226), Color(light: 0xD9A066, dark: 0x9C6D3F)]
        case .portrait: [Color(light: 0xF2C6A0, dark: 0xA27A58), Color(light: 0xB98B78, dark: 0x7A574A)]
        case .family: [Color(light: 0x8EC5E8, dark: 0x4B7FA3), Color(light: 0x6FA38A, dark: 0x3F6B55)]
        case .mountain: [Color(light: 0x7FB8D8, dark: 0x3F7898), Color(light: 0x3E7F6E, dark: 0x264F44)]
        case .concert: [Color(light: 0x3A2A7A, dark: 0x251A52), Color(light: 0xC04FC9, dark: 0x803488)]
        case .cake: [Color(light: 0xF5D9A6, dark: 0xA88E5E), Color(light: 0xC99A5B, dark: 0x86663A)]
        case .beach: [Color(light: 0xF3B179, dark: 0xA8733F), Color(light: 0x5A8FB0, dark: 0x345A73)]
        case .baby: [Color(light: 0xF6E3D4, dark: 0xA8958A), Color(light: 0xE8C2A8, dark: 0x9C7E6A)]
        case .screen: [Color(light: 0x9FB5E8, dark: 0x51679A), Color(light: 0x6D7FB8, dark: 0x3F4D7A)]
        case .tokyo: [Color(light: 0xA8D0F0, dark: 0x5A87A8), Color(light: 0xF4B8C8, dark: 0xA06A7A)]
        case .boardingPass: [Color(light: 0xE8F0FB, dark: 0x22324A), Color(light: 0xCFE0F7, dark: 0x1A2A40)]
        }
    }

    var symbol: String {
        switch self {
        case .sunset: "sun.horizon.fill"
        case .dinner: "fork.knife"
        case .portrait: "person.fill"
        case .family: "person.3.fill"
        case .mountain: "mountain.2.fill"
        case .concert: "music.mic"
        case .cake: "birthday.cake.fill"
        case .beach: "beach.umbrella.fill"
        case .baby: "figure.and.child.holdinghands"
        case .screen: "iphone"
        case .tokyo: "building.columns.fill"
        case .boardingPass: "airplane"
        }
    }
}

// MARK: - Similar photos (05) / Best shot (06)

struct PhotoGroup: Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let systemImage: String
    let tint: Tint
    let photoCount: Int
    let bytes: Int64
    let style: ThumbnailStyle
    /// Index of the AI-recommended keeper within the group.
    let recommendedIndex: Int
    /// Real library assets in capture order; empty for mock/demo groups.
    var assetIDs: [String] = []
    /// Why the recommended photo was picked; empty means "use the generic explanation".
    var reasons: [BestShotReason] = []
    /// Space recovered by keeping only the recommended photo.
    var reclaimableBytes: Int64 = 0
}

enum PhotoGroupFilter: String, CaseIterable, Sendable {
    case all = "All", recent = "Recent", reviewed = "Reviewed"
}

struct BestShotReason: Identifiable, Hashable, Sendable {
    let title: String
    let detail: String
    let systemImage: String
    let tint: Tint

    var id: String { title }
}

// MARK: - Screenshots (11)

struct ScreenshotCategory: Identifiable, Sendable {
    let title: String
    let systemImage: String
    let tint: Tint
    let bytes: Int64
    /// Real categories open their review set; demo ones don't.
    var kind: ScreenshotKind? = nil
    var count: Int = 0

    var id: String { title }
}

struct ExpiredScreenshot: Identifiable, Sendable {
    let title: String
    let detail: String
    let status: String
    let style: ThumbnailStyle
    var assetID: String? = nil

    var id: String { assetID ?? title }
}

// MARK: - Receipt filing (16)

struct ReceiptEntry: Identifiable, Hashable, Sendable {
    enum Source: Sendable {
        case screenshot
        /// A camera photo of a paper receipt.
        case photo
    }

    /// Asset ID for real receipts; a stable demo ID otherwise.
    let id: String
    let details: ReceiptDetails
    let capturedAt: Date
    let bytes: Int64
    var fileName: String? = nil
    var source: Source = .screenshot
    /// Demo entries have no asset to show or upload.
    var isDemo = false

    var fileExtension: String { fileName.map { ($0 as NSString).pathExtension }.flatMap { $0.isEmpty ? nil : $0 } ?? "jpg" }

    func filingPlan(rules: [StorageRule]) -> FilingPlan? {
        RuleMatcher.plan(for: details, capturedAt: capturedAt, originalName: fileName, fileExtension: fileExtension, rules: rules)
    }

    var amountText: String? {
        details.amount.map { amount in
            let formatter = NumberFormatter()
            formatter.numberStyle = .currency
            formatter.currencyCode = details.currency ?? Locale.current.currency?.identifier ?? "THB"
            formatter.maximumFractionDigits = amount == amount.rounded() ? 0 : 2
            return formatter.string(from: NSDecimalNumber(decimal: amount)) ?? "\(amount)"
        }
    }
}

private extension Decimal {
    func rounded() -> Decimal {
        var result = Decimal(), value = self
        NSDecimalRound(&result, &value, 0, .plain)
        return result
    }
}

// MARK: - Videos (08)

enum VideoKind: Sendable { case large, recording }

enum VideoFilter: String, CaseIterable, Sendable {
    case all = "All", large = "Large", recordings = "Recordings"

    func includes(_ kind: VideoKind) -> Bool {
        switch self {
        case .all: true
        case .large: kind == .large
        case .recordings: kind == .recording
        }
    }
}

struct VideoItem: Identifiable, Sendable {
    let id: String
    let title: String
    let bytes: Int64
    let quality: String?
    let duration: String
    let kind: VideoKind
    let style: ThumbnailStyle
    /// Personal / meaningful footage is offered for review, never compression by default.
    let isMeaningful: Bool
    var assetID: String? = nil
    var durationSeconds: TimeInterval = 0
    /// Shorter pixel side (1080 for 1080p), used to decide whether compression helps.
    var shortSide: Int = 0

    func estimatedSavings(_ preset: CompressionPreset) -> Int64? {
        CompressionEstimator.estimatedSavings(bytes: bytes, duration: durationSeconds, shortSide: shortSide, preset: preset)
    }

    var metadata: String {
        [quality, duration].compactMap { $0 }.joined(separator: " · ")
    }
}

// MARK: - Insights (07)

struct ForecastPoint: Identifiable, Equatable, Sendable {
    /// Weeks relative to today (negative = history).
    let week: Double
    let usedGB: Double
    let isProjection: Bool

    var id: String { "\(isProjection)-\(week)" }
}

struct StorageForecast: Sendable {
    let capacityGB: Double
    let remainingBytes: Int64
    /// nil when storage isn't growing enough to project a date.
    let daysUntilFull: Int?
    let points: [ForecastPoint]
    let photosAddedThisWeek: Int
    let videosAddedThisWeek: Int
    let potentialCleanupBytes: Int64
}

// MARK: - Memories (10)

struct MemoryEvent: Identifiable, Hashable, Sendable {
    enum Kind: Sendable { case trip, event }

    let id: String
    let title: String
    let photoCount: Int
    let videoCount: Int
    /// Placeholder art for demo memories (and while a real cover loads).
    let style: ThumbnailStyle
    var kind: Kind = .event
    var start: Date? = nil
    var end: Date? = nil
    var distanceKm: Int? = nil
    /// The best photo of the memory; nil for demo memories.
    var coverAssetID: String? = nil
    /// Every photo and video, in capture order.
    var assetIDs: [String] = []
    var bytes: Int64 = 0
    /// Cleanup candidates inside the memory: extra shots from similar groups, and blurry photos.
    var similarCount = 0
    var blurryCount = 0

    init(id: String? = nil, title: String, photoCount: Int, videoCount: Int, style: ThumbnailStyle) {
        self.id = id ?? title
        self.title = title
        self.photoCount = photoCount
        self.videoCount = videoCount
        self.style = style
    }

    var summary: String {
        let photos = "\(photoCount.formatted()) \(photoCount == 1 ? "photo" : "photos")"
        return videoCount > 0 ? "\(photos) · \(videoCount.formatted()) \(videoCount == 1 ? "video" : "videos")" : photos
    }

    /// "12–15 Sep 2026 · 540 km from home".
    var detail: String? {
        guard let start, let end else { return nil }
        let dates = Calendar.current.isDate(start, inSameDayAs: end)
            ? start.formatted(date: .abbreviated, time: .omitted)
            : (start..<max(end, start.addingTimeInterval(1))).formatted(.interval.day().month(.abbreviated).year())
        return [dates, distanceKm.map { "\($0.formatted()) km from home" }].compactMap { $0 }.joined(separator: " · ")
    }
}

// MARK: - Mock data (matches the mockups)

extension MockData {
    private static let mb: Int64 = 1_000_000

    static let cleanupPlan = CleanupPlan(
        targetBytes: 10_000_000_000,
        estimatedBytes: 10_400_000_000,
        reviewTime: "2 min 40 sec",
        items: [
            PlanItem(title: "Old Screen Recordings", systemImage: "record.circle", tint: .coral, bytes: 4_800 * mb, route: .videos, itemCount: 12),
            PlanItem(title: "Similar Photos", systemImage: "photo.on.rectangle.angled", tint: .coral, bytes: 2_700 * mb, route: .similarPhotos, itemCount: 60),
            PlanItem(title: "Screenshots", systemImage: "viewfinder", tint: .blue, bytes: 1_400 * mb, route: .screenshots, itemCount: 25),
            PlanItem(title: "Blurry Photos", systemImage: "camera.filters", tint: .mint, bytes: 900 * mb, route: .similarPhotos, itemCount: 7),
            PlanItem(title: "Duplicate Videos", systemImage: "video.fill", tint: .purple, bytes: 600 * mb, route: .videos, itemCount: 3),
        ]
    )

    static let similarPhotosSummary = (bytes: Int64(9_800) * mb, groups: 328)

    static let photoGroups: [PhotoGroup] = [
        PhotoGroup(id: "beach", title: "Beach Sunset", systemImage: "beach.umbrella.fill", tint: .coral, photoCount: 12, bytes: 93 * mb, style: .sunset, recommendedIndex: 2),
        PhotoGroup(id: "dinner", title: "Dinner", systemImage: "fork.knife", tint: .purple, photoCount: 8, bytes: 76 * mb, style: .dinner, recommendedIndex: 0),
        PhotoGroup(id: "portrait", title: "Portrait Session", systemImage: "person.fill", tint: .blue, photoCount: 14, bytes: 124 * mb, style: .portrait, recommendedIndex: 3),
        PhotoGroup(id: "family", title: "Family Selfie", systemImage: "person.3.fill", tint: .mint, photoCount: 4, bytes: 38 * mb, style: .family, recommendedIndex: 1),
    ]

    static let bestShotReasons: [BestShotReason] = [
        BestShotReason(title: "Sharpest image", detail: "Faces and details are the clearest.", systemImage: "viewfinder", tint: .blue),
        BestShotReason(title: "Everyone has eyes open", detail: "All faces are clearly visible.", systemImage: "person.2.fill", tint: .mint),
        BestShotReason(title: "Best exposure", detail: "Well-balanced lighting and natural colors.", systemImage: "sun.max.fill", tint: .purple),
        BestShotReason(title: "No visible motion blur", detail: "Everything looks sharp and steady.", systemImage: "figure.run", tint: .coral),
    ]

    static let screenshotsRecoverableBytes: Int64 = 5_100 * mb

    static let screenshotCategories: [ScreenshotCategory] = [
        ScreenshotCategory(title: "Shopping", systemImage: "bag.fill", tint: .coral, bytes: 1_700 * mb),
        ScreenshotCategory(title: "Receipts", systemImage: "doc.text.fill", tint: .amber, bytes: 1_100 * mb),
        ScreenshotCategory(title: "Chats", systemImage: "bubble.left.and.bubble.right.fill", tint: .mint, bytes: 890 * mb),
        ScreenshotCategory(title: "QR Codes", systemImage: "qrcode.viewfinder", tint: .blue, bytes: 630 * mb),
        ScreenshotCategory(title: "Tickets", systemImage: "ticket.fill", tint: .purple, bytes: 260 * mb),
    ]

    static let expiredScreenshots: [ExpiredScreenshot] = [
        ExpiredScreenshot(title: "Boarding pass", detail: "Bangkok → Tokyo", status: "Trip completed", style: .boardingPass),
    ]

    static let videoSummary = (largeBytes: Int64(18_200) * mb, recordingBytes: Int64(4_300) * mb)

    static let videos: [VideoItem] = [
        VideoItem(id: "trip", title: "Trip Recap", bytes: 2_400 * mb, quality: "4K", duration: "08:42", kind: .large, style: .mountain, isMeaningful: false, durationSeconds: 522, shortSide: 2160),
        VideoItem(id: "rec", title: "Screen Recording", bytes: 1_300 * mb, quality: nil, duration: "24:15", kind: .recording, style: .screen, isMeaningful: false, durationSeconds: 1455, shortSide: 1179),
        VideoItem(id: "concert", title: "Concert Clip", bytes: 980 * mb, quality: "4K", duration: "03:18", kind: .large, style: .concert, isMeaningful: false, durationSeconds: 198, shortSide: 2160),
        VideoItem(id: "vlog", title: "Beach Vlog", bytes: 718 * mb, quality: "4K", duration: "05:21", kind: .large, style: .beach, isMeaningful: false, durationSeconds: 321, shortSide: 2160),
        VideoItem(id: "family", title: "Family Moments", bytes: 654 * mb, quality: "1080p", duration: "04:12", kind: .large, style: .baby, isMeaningful: true, durationSeconds: 252, shortSide: 1080),
    ]

    /// Four weeks of history plus a projection to capacity. Kept internally consistent:
    /// 238 GB used today, ~2.7 GB/week growth → full in ~47 days.
    static let forecast: StorageForecast = {
        let history: [ForecastPoint] = [(-4, 227.2), (-3, 229.9), (-2, 232.6), (-1, 235.3), (0, 238.0)]
            .map { ForecastPoint(week: $0.0, usedGB: $0.1, isProjection: false) }
        let projection: [ForecastPoint] = [(0, 238.0), (2, 243.4), (4, 248.8), (6.7, 256.0)]
            .map { ForecastPoint(week: $0.0, usedGB: $0.1, isProjection: true) }
        return StorageForecast(
            capacityGB: 256,
            remainingBytes: 18_000_000_000,
            daysUntilFull: 47,
            points: history + projection,
            photosAddedThisWeek: 286,
            videosAddedThisWeek: 19,
            potentialCleanupBytes: 1_400 * mb
        )
    }()

    static let memories: [MemoryEvent] = {
        func day(_ month: Int, _ day: Int) -> Date { Calendar.utcGregorian.date(from: DateComponents(year: 2026, month: month, day: day, hour: 12))! }
        var tokyo = MemoryEvent(title: "Tokyo Trip", photoCount: 1_284, videoCount: 94, style: .tokyo)
        tokyo.kind = .trip
        (tokyo.start, tokyo.end, tokyo.distanceKm) = (day(4, 3), day(4, 9), 4_600)
        (tokyo.bytes, tokyo.similarCount, tokyo.blurryCount) = (9_800_000_000, 280, 45)
        var birthday = MemoryEvent(title: "Birthday Party", photoCount: 342, videoCount: 0, style: .cake)
        (birthday.start, birthday.end) = (day(6, 14), day(6, 14))
        (birthday.bytes, birthday.similarCount, birthday.blurryCount) = (1_900_000_000, 70, 14)
        var concert = MemoryEvent(title: "Concert Night", photoCount: 184, videoCount: 0, style: .concert)
        (concert.start, concert.end) = (day(8, 22), day(8, 22))
        (concert.bytes, concert.similarCount, concert.blurryCount) = (820_000_000, 32, 8)
        return [tokyo, birthday, concert]
    }()
    static let memoriesCleanup = (similarPhotos: 382, blurryShots: 67)

    static let receipts: [ReceiptEntry] = {
        let day = Calendar.utcGregorian.date(from: DateComponents(year: 2026, month: 9, day: 27, hour: 12))!
        return [
            ReceiptEntry(id: "demo-central",
                         details: ReceiptDetails(merchant: "Central Department Store", date: day, amount: 3450, currency: "THB", category: .shopping),
                         capturedAt: day, bytes: 2_400_000, fileName: "IMG_0412.JPG", source: .photo, isDemo: true),
            ReceiptEntry(id: "demo-coffee",
                         details: ReceiptDetails(merchant: "Blue Bottle Coffee", date: day.addingTimeInterval(-15 * 86_400),
                                                 amount: Decimal(string: "6.00"), currency: "USD", category: .foodAndDrink),
                         capturedAt: day.addingTimeInterval(-15 * 86_400), bytes: 1_100_000, fileName: "IMG_0398.PNG", isDemo: true),
        ]
    }()
}
