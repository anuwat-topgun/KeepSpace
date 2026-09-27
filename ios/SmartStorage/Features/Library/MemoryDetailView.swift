import SwiftUI

/// One trip or event: what it holds, why it is protected, and the little that could be cleaned.
/// Read-only on purpose — nothing here deletes anything.
struct MemoryDetailView: View {
    let memoryID: String

    @Environment(LibraryStore.self) private var library
    @Environment(AppRouter.self) private var router
    @Environment(\.horizontalSizeClass) private var sizeClass

    /// Enough to get a feel for the memory without loading hundreds of thumbnails.
    private let previewLimit = 60

    var body: some View {
        let memory = library.content.memories.first { $0.id == memoryID }
            ?? (memoryID == "first" ? library.content.memories.first : nil)
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            if let memory {
                content(memory)
            } else {
                ScreenHeader(title: "Memory", subtitle: "This memory is no longer in your library.")
            }
        }
    }

    @ViewBuilder
    private func content(_ memory: MemoryEvent) -> some View {
        ScreenHeader(title: memory.title, subtitle: memory.detail ?? memory.summary)
            .padding(.bottom, 8)

        AssetImage(assetID: memory.coverAssetID, fallback: memory.style, cornerRadius: Metrics.cardRadius, symbolScale: 0.2)
            .frame(height: sizeClass == .regular ? 320 : 220)
            .overlay(alignment: .topLeading) {
                StatusBadge.protected().padding(14)
            }
            .accessibilityHidden(true)

        HStack(spacing: 12) {
            stat(memory.photoCount.formatted(), memory.photoCount == 1 ? "Photo" : "Photos")
            stat(memory.videoCount.formatted(), memory.videoCount == 1 ? "Video" : "Videos")
            stat(memory.bytes > 0 ? memory.bytes.formattedBytes : "—", "Size")
        }

        Card(style: .info) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(Tint.teal.foreground)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Protected \(memory.kind == .trip ? "trip" : "event")")
                        .font(Typography.cardHeadline)
                        .foregroundStyle(Palette.textPrimary)
                    Text("Photos from this \(memory.kind == .trip ? "trip" : "event") are never suggested on their own. Only extra shots of similar photos are offered, and blurry ones are never preselected.")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }

        if memory.similarCount + memory.blurryCount > 0 {
            Button { router.push(memory.similarCount > 0 ? .similarPhotos : .review(.blurry)) } label: {
                Card(padding: 16) {
                    ListTile(systemImage: "sparkles", tint: .teal, title: "Potential cleanup inside",
                             subtitle: "\(memory.similarCount) similar \(memory.similarCount == 1 ? "photo" : "photos") · \(memory.blurryCount) blurry \(memory.blurryCount == 1 ? "shot" : "shots")")
                }
            }
            .buttonStyle(.plain)
        }

        SectionLabel("Photos & videos")
        LazyVGrid(columns: [GridItem(.adaptive(minimum: sizeClass == .regular ? 120 : 96), spacing: 8)], spacing: 8) {
            if memory.assetIDs.isEmpty {
                // Demo memory: placeholder art only.
                ForEach(0..<9, id: \.self) { index in
                    MediaThumbnail(style: memory.style, variant: index, cornerRadius: 10, symbolScale: 0.3)
                        .aspectRatio(1, contentMode: .fit)
                }
            } else {
                ForEach(Array(memory.assetIDs.prefix(previewLimit)), id: \.self) { id in
                    AssetImage(assetID: id, fallback: memory.style, cornerRadius: 10)
                        .aspectRatio(1, contentMode: .fit)
                }
            }
        }
        if memory.assetIDs.count > previewLimit {
            Text("and \((memory.assetIDs.count - previewLimit).formatted()) more")
                .font(Typography.metadata)
                .foregroundStyle(Palette.textSecondary)
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        Card(padding: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(value)
                    .font(.system(.title3, weight: .semibold))
                    .foregroundStyle(Palette.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(label)
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

#Preview {
    NavigationStack { MemoryDetailView(memoryID: "Tokyo Trip") }
        .previewEnvironment()
}
