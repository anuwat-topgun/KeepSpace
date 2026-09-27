import SwiftUI

/// 10 — Memories: events and trips recognized on device and protected by default.
struct MemoriesView: View {
    @Environment(AppRouter.self) private var router
    private let memories = MockData.memories
    private let cleanup = MockData.memoriesCleanup

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Memories", subtitle: "Important moments are protected by default.")
                .padding(.bottom, 8)

            AdaptiveGrid(minColumnWidth: 400) {
                ForEach(memories) { memory in
                    MemoryCard(memory: memory)
                }
            }

            Button {
                router.push(.similarPhotos)
            } label: {
                Card(style: .info) {
                    HStack(alignment: .top, spacing: 16) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 24, weight: .semibold))
                            .foregroundStyle(Palette.accent)
                            .frame(width: 56, height: 56)
                            .background(Tint.teal.background, in: Circle())
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Potential cleanup inside trips")
                                .font(Typography.cardHeadline)
                                .foregroundStyle(Palette.textPrimary)
                            Label("\(cleanup.similarPhotos) similar photos", systemImage: "photo.on.rectangle")
                            Label("\(cleanup.blurryShots) blurry shots", systemImage: "circle.dotted")
                        }
                        .font(Typography.body)
                        .foregroundStyle(Palette.textSecondary)
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right")
                            .font(.system(.body, weight: .semibold))
                            .foregroundStyle(Palette.textSecondary)
                            .padding(.top, 40)
                    }
                }
            }
            .buttonStyle(.plain)
        }
    }
}

private struct MemoryCard: View {
    let memory: MemoryEvent
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        Card(padding: 16) {
            HStack(spacing: 16) {
                MediaThumbnail(style: memory.style, cornerRadius: 16, symbolScale: 0.35)
                    .frame(width: sizeClass == .regular ? 150 : 112, height: sizeClass == .regular ? 116 : 96)
                VStack(alignment: .leading, spacing: 8) {
                    Text(memory.title)
                        .font(.system(.title3, weight: .semibold))
                        .foregroundStyle(Palette.textPrimary)
                    Text(memory.summary)
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                    StatusBadge.protected()
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(.body, weight: .semibold))
                    .foregroundStyle(Palette.textSecondary)
            }
        }
    }
}

#Preview {
    NavigationStack { MemoriesView() }
        .environment(AppRouter())
}
