import Foundation

/// Turns raw scan output into what the screens show. Pure and synchronous so it is easy to test.
struct LibraryReportBuilder: Sendable {
    /// Below this Laplacian variance (256px grayscale) a photo reads as blurry.
    var blurThreshold = 60.0
    /// Videos at least this big count as "large".
    var largeVideoBytes: Int64 = 50_000_000
    /// Screenshots and recordings older than this are suggested for cleanup.
    var staleAge: TimeInterval = 30 * 24 * 3600
    var maxGroupsShown = 60
    var maxVideosShown = 50
    var grouper = SimilarityGrouper()
    var scorer = BestShotScorer()

    func build(
        items: [MediaItem],
        analyzed: [AnalyzedPhoto],
        deviceTotalBytes: Int64,
        deviceFreeBytes: Int64,
        now: Date = .now
    ) -> LibraryContent {
        let byID = Dictionary(items.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let screenshots = items.filter { $0.kind == .screenshot }
        let recordings = items.filter { $0.kind == .screenRecording }
        let videos = items.filter { $0.kind == .video }
        let largeVideos = videos.filter { $0.bytes >= largeVideoBytes }

        // Similar groups and the keeper for each.
        let rawGroups = grouper.groups(from: analyzed)
        var groups: [PhotoGroup] = []
        var inGroup = Set<String>()
        for members in rawGroups {
            guard let pick = scorer.pick(members) else { continue }
            members.forEach { inGroup.insert($0.id) }
            groups.append(photoGroup(members, pick: pick))
        }
        groups.sort { $0.reclaimableBytes > $1.reclaimableBytes }
        let similarBytes = groups.reduce(0) { $0 + $1.reclaimableBytes }
        let similarCount = groups.reduce(0) { $0 + $1.photoCount - 1 }

        // Blurry photos that aren't already covered by a similar group; favourites are never suggested.
        let blurry = analyzed.filter {
            $0.features.sharpness < blurThreshold && !inGroup.contains($0.id) && !$0.item.isFavorite
        }
        let blurryBytes = blurry.reduce(0) { $0 + $1.item.bytes }

        let staleScreenshots = screenshots.filter { now.timeIntervalSince($0.creationDate) > staleAge }
        let staleRecordings = recordings.filter { now.timeIntervalSince($0.creationDate) > staleAge }

        let candidates = [
            PlanItem(title: "Old Screen Recordings", systemImage: "record.circle", tint: .coral,
                     bytes: staleRecordings.totalBytes, route: .review(.oldRecordings), itemCount: staleRecordings.count),
            PlanItem(title: "Similar Photos", systemImage: "photo.on.rectangle.angled", tint: .coral,
                     bytes: similarBytes, route: .review(.similar), itemCount: similarCount),
            PlanItem(title: "Old Screenshots", systemImage: "viewfinder", tint: .blue,
                     bytes: staleScreenshots.totalBytes, route: .review(.oldScreenshots), itemCount: staleScreenshots.count),
            PlanItem(title: "Blurry Photos", systemImage: "camera.filters", tint: .mint,
                     bytes: blurryBytes, route: .review(.blurry), itemCount: blurry.count),
        ]
        let potential = candidates.reduce(0) { $0 + $1.bytes }

        let storage = StorageSummary(
            usedBytes: deviceTotalBytes - deviceFreeBytes,
            totalBytes: deviceTotalBytes,
            potentialCleanupBytes: potential,
            categoryBytes: [
                .similarPhotos: similarBytes,
                .screenshots: screenshots.totalBytes,
                .largeVideos: largeVideos.totalBytes,
                .screenRecordings: recordings.totalBytes,
                .blurryPhotos: blurryBytes,
            ]
        )

        let recentScreenshots = screenshots.filter { now.timeIntervalSince($0.creationDate) <= staleAge }
        return LibraryContent(
            storage: storage,
            similarBytes: similarBytes,
            photoGroups: Array(groups.prefix(maxGroupsShown)),
            screenshotsBytes: staleScreenshots.totalBytes,
            screenshotCategories: [
                ScreenshotCategory(title: "Older than 30 days", systemImage: "clock.arrow.circlepath", tint: .amber, bytes: staleScreenshots.totalBytes),
                ScreenshotCategory(title: "Last 30 days", systemImage: "viewfinder", tint: .blue, bytes: recentScreenshots.totalBytes),
            ].filter { $0.bytes > 0 },
            expiredScreenshots: [],
            largeVideoBytes: largeVideos.totalBytes,
            recordingBytes: recordings.totalBytes,
            videos: (videos + recordings)
                .sorted { $0.bytes > $1.bytes }
                .prefix(maxVideosShown)
                .map(videoItem),
            forecast: forecast(items: items, total: deviceTotalBytes, free: deviceFreeBytes, potential: potential, now: now),
            memories: [],
            memoriesCleanup: (similarCount, blurry.count),
            cleanupCandidates: candidates,
            reviewSets: [
                // Non-keepers from every similar group, biggest groups first.
                .similar: groups.flatMap { group in
                    group.assetIDs.enumerated().compactMap { index, id in
                        index == group.recommendedIndex ? nil : byID[id].map { Self.review($0, preselected: true) }
                    }
                },
                .blurry: blurry.map { Self.review($0.item, preselected: true) },
                .oldScreenshots: staleScreenshots.map { Self.review($0, preselected: true) },
                .oldRecordings: staleRecordings.map { Self.review($0, preselected: true) },
                // Personal footage: listed biggest first, never preselected.
                .largeVideos: largeVideos.sorted { $0.bytes > $1.bytes }.map { Self.review($0, preselected: false) },
            ]
        )
    }

    // MARK: - Pieces

    private func photoGroup(_ members: [AnalyzedPhoto], pick: BestShotScorer.Pick) -> PhotoGroup {
        let total = members.reduce(0) { $0 + $1.item.bytes }
        let keeper = members[pick.index]
        let label = members.compactMap(\.features.sceneLabel).mostCommon
        let date = keeper.item.creationDate.formatted(.dateTime.month(.abbreviated).day().hour().minute())
        let (icon, tint) = Self.icon(for: label, hasFaces: members.contains { $0.features.faceCount > 0 })
        return PhotoGroup(
            id: keeper.id,
            title: label.map { $0.replacingOccurrences(of: "_", with: " ").capitalized } ?? date,
            systemImage: icon,
            tint: tint,
            photoCount: members.count,
            bytes: total,
            style: .sunset,
            recommendedIndex: pick.index,
            assetIDs: members.map(\.id),
            reasons: pick.reasons.map(Self.reason),
            reclaimableBytes: total - keeper.item.bytes
        )
    }

    private static func icon(for label: String?, hasFaces: Bool) -> (String, Tint) {
        switch label ?? "" {
        case let l where ["beach", "ocean", "sea", "shore", "sunset_sunrise", "sky"].contains(l): ("beach.umbrella.fill", .coral)
        case let l where ["food", "meal", "dish", "dessert", "drink", "restaurant"].contains(l): ("fork.knife", .purple)
        case let l where ["mountain", "hill", "landscape", "forest", "tree"].contains(l): ("mountain.2.fill", .mint)
        case let l where ["dog", "cat", "animal", "pet"].contains(l): ("pawprint.fill", .amber)
        default: hasFaces ? ("person.2.fill", .blue) : ("photo.on.rectangle.angled", .teal)
        }
    }

    private static func review(_ item: MediaItem, preselected: Bool) -> ReviewItem {
        // Favourites are protected: never preselected, whatever the set.
        ReviewItem(id: item.id, bytes: item.bytes, isVideo: item.isVideo, duration: item.duration,
                   createdAt: item.creationDate, preselected: preselected && !item.isFavorite)
    }

    private static func reason(_ reason: BestShotScorer.Reason) -> BestShotReason {
        switch reason {
        case .sharpest: BestShotReason(title: "Sharpest image", detail: "Details are the clearest in this group.", systemImage: "viewfinder", tint: .blue)
        case .bestFaces: BestShotReason(title: "Best faces", detail: "Faces are the clearest and best framed.", systemImage: "person.2.fill", tint: .mint)
        case .bestExposure: BestShotReason(title: "Best exposure", detail: "Well-balanced lighting.", systemImage: "sun.max.fill", tint: .purple)
        case .favorite: BestShotReason(title: "Your favorite", detail: "You marked this photo as a favorite.", systemImage: "heart.fill", tint: .coral)
        }
    }

    private func videoItem(_ item: MediaItem) -> VideoItem {
        let shortSide = min(item.pixelWidth, item.pixelHeight)
        let quality: String? = item.kind == .screenRecording ? nil
            : shortSide >= 2160 ? "4K" : shortSide >= 1080 ? "1080p" : shortSide >= 720 ? "720p" : nil
        let seconds = Int(item.duration.rounded())
        return VideoItem(
            id: item.id,
            title: item.kind == .screenRecording ? "Screen Recording" : item.creationDate.formatted(date: .abbreviated, time: .omitted),
            bytes: item.bytes,
            quality: quality,
            duration: String(format: "%02d:%02d", seconds / 60, seconds % 60),
            kind: item.kind == .screenRecording ? .recording : .large,
            style: item.kind == .screenRecording ? .screen : .mountain,
            isMeaningful: item.isFavorite,
            assetID: item.id,
            durationSeconds: item.duration,
            shortSide: shortSide
        )
    }

    /// History = device usage minus media added since each point; projection = recent weekly
    /// media growth extended until the device is full (capped at 12 weeks for the chart).
    private func forecast(items: [MediaItem], total: Int64, free: Int64, potential: Int64, now: Date) -> StorageForecast {
        let week: TimeInterval = 7 * 24 * 3600
        let used = Double(total - free) / 1e9
        let history = (0...4).reversed().map { back -> ForecastPoint in
            let since = now.addingTimeInterval(-Double(back) * week)
            let addedSince = items.filter { $0.creationDate > since }.totalBytes
            return ForecastPoint(week: -Double(back), usedGB: used - Double(addedSince) / 1e9, isProjection: false)
        }
        let weeklyGrowthGB = (used - (history.first?.usedGB ?? used)) / 4
        let freeGB = Double(free) / 1e9
        var projection: [ForecastPoint] = [ForecastPoint(week: 0, usedGB: used, isProjection: true)]
        var daysUntilFull: Int?
        if weeklyGrowthGB > 0.05 {
            let weeksToFull = freeGB / weeklyGrowthGB
            daysUntilFull = Int((weeksToFull * 7).rounded())
            let end = min(weeksToFull, 12)
            projection.append(ForecastPoint(week: end, usedGB: used + weeklyGrowthGB * end, isProjection: true))
        }
        let lastWeek = now.addingTimeInterval(-week)
        return StorageForecast(
            capacityGB: Double(total) / 1e9,
            remainingBytes: free,
            daysUntilFull: daysUntilFull,
            points: history + projection,
            photosAddedThisWeek: items.filter { !$0.isVideo && $0.creationDate > lastWeek }.count,
            videosAddedThisWeek: items.filter { $0.isVideo && $0.creationDate > lastWeek }.count,
            potentialCleanupBytes: potential
        )
    }
}

private extension Sequence where Element == MediaItem {
    var totalBytes: Int64 { reduce(0) { $0 + $1.bytes } }
}

private extension Array where Element == String {
    var mostCommon: String? {
        Dictionary(grouping: self, by: { $0 }).max { $0.value.count < $1.value.count }?.key
    }
}
