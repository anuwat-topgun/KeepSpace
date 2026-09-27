import SwiftUI

/// 10 — Memories: events and trips recognized on device and protected by default.
struct MemoriesView: View {
    @Environment(AppRouter.self) private var router
    @Environment(LibraryStore.self) private var library

    private var memories: [MemoryEvent] { library.content.memories }
    private var cleanup: (similarPhotos: Int, blurryShots: Int) { library.content.memoriesCleanup }

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Memories", subtitle: "Important moments are protected by default.")
                .padding(.bottom, 8)

            if memories.isEmpty {
                Card(style: .info) {
                    Label(
                        "Trip and event detection is coming soon. Photos are never removed without your review.",
                        systemImage: "lock.shield"
                    )
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
                }
            }

            AdaptiveGrid(minColumnWidth: 400) {
                ForEach(memories) { memory in
                    MemoryCard(memory: memory)
                }
            }

            if cleanup.similarPhotos + cleanup.blurryShots > 0 {
                Button {
                    router.push(.similarPhotos)
                } label: {
                    cleanupCard
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var cleanupCard: some View {
        Card(style: .info) {
            HStack(alignment: .top, spacing: 16) {
                Image(systemName: "sparkles")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(Palette.accent)
                    .frame(width: 56, height: 56)
                    .background(Tint.teal.background, in: Circle())
                VStack(alignment: .leading, spacing: 12) {
                    Text(memories.isEmpty ? "Potential cleanup in your photos" : "Potential cleanup inside trips")
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
        .previewEnvironment()
}
