import SwiftUI

/// 08 — Large videos and screen recordings, with compression offered as an alternative to deletion.
struct VideosView: View {
    @Environment(LibraryStore.self) private var library
    @State private var filter: VideoFilter = .all

    private var videos: [VideoItem] {
        library.content.videos.filter { filter.includes($0.kind) }
    }

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Videos", subtitle: "Large files and recordings.")
                .padding(.bottom, 8)

            Card(style: .hero) {
                VStack(alignment: .leading, spacing: 16) {
                    SectionLabel("Video storage")
                    HStack(spacing: 0) {
                        metric(icon: "video.fill", tint: .purple, title: "Large Videos", bytes: library.content.largeVideoBytes)
                        Divider().frame(height: 120)
                        metric(icon: "record.circle", tint: .coral, title: "Screen Recordings", bytes: library.content.recordingBytes)
                    }
                }
            }

            ChipPicker(options: VideoFilter.allCases, selection: $filter) { $0.rawValue }
                .padding(.vertical, 4)

            if videos.isEmpty {
                Card(style: .info) {
                    Label(library.isScanning ? "Reading your library…" : "No videos here.", systemImage: "video")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }

            AdaptiveGrid(minColumnWidth: 400) {
                ForEach(videos) { video in
                    VideoRow(video: video)
                }
            }
            .animation(.easeInOut(duration: 0.2), value: filter)
        }
    }

    private func metric(icon: String, tint: Tint, title: String, bytes: Int64) -> some View {
        VStack(spacing: 8) {
            IconTile(systemName: icon, tint: tint, size: 60)
            Text(title)
                .font(Typography.metadata)
                .foregroundStyle(Palette.textSecondary)
            Text(bytes.formattedBytes)
                .font(Typography.metricLarge)
                .foregroundStyle(Palette.textPrimary)
                .minimumScaleFactor(0.7)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}

private struct VideoRow: View {
    let video: VideoItem
    @Environment(AppRouter.self) private var router
    @State private var compressing: VideoItem?

    var body: some View {
        Card(padding: 12) {
            HStack(spacing: 14) {
                AssetImage(assetID: video.assetID, fallback: video.style, cornerRadius: 12)
                    .frame(width: 96, height: 64)
                    .overlay(alignment: .bottomTrailing) {
                        DurationBadge(text: video.duration).padding(6)
                    }
                VStack(alignment: .leading, spacing: 3) {
                    Text(video.title)
                        .font(Typography.cardHeadline)
                        .foregroundStyle(Palette.textPrimary)
                        .lineLimit(2)
                    Text(video.bytes.formattedBytes)
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                    Text(video.metadata)
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
                Spacer(minLength: 4)
                if video.isMeaningful || CompressionPreset.allCases.allSatisfy({ video.estimatedSavings($0) == nil }) {
                    // Favourites and videos that wouldn't shrink are offered for review, not compression.
                    Button("Review") { router.push(.review(.largeVideos)) }
                        .buttonStyle(InlinePillButtonStyle(tint: .mint))
                } else {
                    Button("Compress") { compressing = video }
                        .buttonStyle(InlinePillButtonStyle(tint: .teal))
                }
            }
        }
        .sheet(item: $compressing) { video in
            CompressSheet(video: video)
                .presentationDetents([.medium, .large])
        }
    }
}

#Preview {
    NavigationStack { VideosView() }
        .previewEnvironment()
}
