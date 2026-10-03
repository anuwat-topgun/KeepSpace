import AVKit
import Photos
import SwiftUI

/// Photos-style library browser with per-asset cloud backup state.
struct LibraryView: View {
    @Environment(LibraryStore.self) private var library
    @Environment(CloudStore.self) private var cloud
    @Environment(AppRouter.self) private var router
    @State private var filter: LibraryFilter = .all
    @State private var selectedItem: MediaItem?

    private let columns = [GridItem(.adaptive(minimum: 104, maximum: 180), spacing: 3)]

    private var items: [MediaItem] {
        library.mediaItems.filter { item in
            switch filter {
            case .all: true
            case .photos: item.isStill
            case .videos: item.isVideo
            case .backedUp: cloud.state(for: item).isBackedUp
            }
        }
    }

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Library", subtitle: "Your full photo library, with cloud backup status.")
                .padding(.bottom, 4)

            HStack(spacing: 10) {
                StatusBadge(text: "Backed up", systemImage: "checkmark.icloud.fill", tint: .mint, compact: true)
                Text("Verified cloud copies")
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
                Spacer()
                Button {
                    router.push(.cloudOverview)
                } label: {
                    Image(systemName: "icloud.and.arrow.up")
                        .font(.system(.body, weight: .semibold))
                        .accessibilityLabel("Cloud backup")
                }
                .buttonStyle(.plain)
                .foregroundStyle(Palette.accent)
                Menu {
                    Button("Similar Photos") { router.push(.similarPhotos) }
                    Button("Screenshots") { router.push(.screenshots) }
                    Button("Videos") { router.push(.videos) }
                    Button("Memories") { router.push(.memories) }
                    Button("Receipt Filing") { router.push(.receipts) }
                    Button("Storage Rules") { router.push(.storageRules) }
                    Button("Back Up Now") { router.push(.manualBackup) }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.system(.body, weight: .semibold))
                        .accessibilityLabel("Organize")
                }
                .foregroundStyle(Palette.textPrimary)
            }

            ChipPicker(options: LibraryFilter.allCases, selection: $filter) { $0.title }
                .padding(.vertical, 2)

            if items.isEmpty {
                Card(style: .info) {
                    Label(library.isScanning ? "Reading your library…" : "No media found.", systemImage: "photo.on.rectangle")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            } else {
                LazyVGrid(columns: columns, spacing: 3) {
                    ForEach(items) { item in
                        LibraryTile(item: item, state: cloud.state(for: item)) {
                            selectedItem = item
                        }
                    }
                }
                .animation(.easeInOut(duration: 0.2), value: filter)
            }
        }
        .fullScreenCover(item: $selectedItem) { item in
            LibraryPreview(items: items, initialID: item.id)
                .environment(cloud)
        }
    }
}

private enum LibraryFilter: String, CaseIterable, Hashable {
    case all, photos, videos, backedUp

    var title: String {
        switch self {
        case .all: "All"
        case .photos: "Photos"
        case .videos: "Videos"
        case .backedUp: "Backed up"
        }
    }
}

private struct LibraryTile: View {
    let item: MediaItem
    let state: AssetCloudState
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            AssetImage(assetID: item.id, fallback: item.fallbackStyle, cornerRadius: 2)
                .aspectRatio(1, contentMode: .fit)
                .overlay(alignment: .bottomTrailing) {
                    CloudStateIcon(state: state)
                        .padding(6)
                }
                .overlay(alignment: .topTrailing) {
                    if item.isVideo {
                        Image(systemName: "video.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.white)
                            .shadow(radius: 2)
                            .padding(7)
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(item.fileName ?? "Photo".localizedUI), \(state.title.localizedUI)")
    }
}

private struct CloudStateIcon: View {
    let state: AssetCloudState

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: 12, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: 25, height: 25)
            .background(color.opacity(0.92), in: Circle())
            .overlay(Circle().stroke(.white.opacity(0.75), lineWidth: 1))
            .accessibilityLabel(state.title.localizedUI)
    }

    private var icon: String {
        switch state.status {
        case .backedUp: "checkmark.icloud.fill"
        case .waiting: "clock.fill"
        case .uploading, .verifying: "arrow.up.circle.fill"
        case .failed: "exclamationmark.icloud.fill"
        case .cancelled, nil: "icloud.slash.fill"
        }
    }

    private var color: Color {
        switch state.status {
        case .backedUp: Tint.mint.foreground
        case .waiting, .uploading, .verifying: Tint.blue.foreground
        case .failed: Tint.coral.foreground
        case .cancelled, nil: .black.opacity(0.55)
        }
    }
}

private struct LibraryPreview: View {
    let items: [MediaItem]
    @Environment(CloudStore.self) private var cloud
    @Environment(\.dismiss) private var dismiss
    @State private var selectedID: String
    @State private var isImmersive = false

    init(items: [MediaItem], initialID: String) {
        self.items = items
        _selectedID = State(initialValue: initialID)
    }

    private var item: MediaItem? {
        items.first { $0.id == selectedID } ?? items.first
    }

    private var index: Int {
        items.firstIndex { $0.id == selectedID } ?? 0
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            TabView(selection: $selectedID) {
                ForEach(items) { item in
                    LibraryPreviewPage(item: item, isImmersive: $isImmersive)
                        .tag(item.id)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea(edges: .horizontal)

            VStack {
                HStack {
                    if !isImmersive {
                        Button("Close") { dismiss() }
                            .font(.headline)
                            .foregroundStyle(.white)
                    }
                    Spacer()
                    if let item {
                        if item.isVideo {
                            Button {
                                withAnimation(.easeInOut(duration: 0.2)) { isImmersive.toggle() }
                            } label: {
                                Image(systemName: isImmersive
                                      ? "arrow.down.right.and.arrow.up.left"
                                      : "arrow.up.left.and.arrow.down.right")
                                    .font(.system(.body, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .frame(width: 36, height: 36)
                                    .background(.black.opacity(0.5), in: Circle())
                                    .accessibilityLabel(isImmersive ? "Exit full screen" : "Full screen")
                            }
                            .buttonStyle(.plain)
                        }
                        if !isImmersive {
                            let state = cloud.state(for: item)
                            StatusBadge(
                                text: state.title,
                                systemImage: "icloud.fill",
                                tint: state.isBackedUp ? .mint : .gray,
                                compact: true
                            )
                        }
                    }
                }
                .padding()
                .background(isImmersive ? .clear : .black.opacity(0.45))
                Spacer()
                if !items.isEmpty && !isImmersive {
                    Text("\(index + 1) / \(items.count)")
                        .font(.footnote.monospacedDigit().weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(.black.opacity(0.5), in: Capsule())
                        .padding(.bottom, 16)
                }
            }
        }
        .onChange(of: selectedID) { _, _ in isImmersive = false }
        .simultaneousGesture(
            DragGesture(minimumDistance: 24).onEnded { value in
                let vertical = value.translation.height
                let horizontal = abs(value.translation.width)
                if vertical > 100, vertical > horizontal * 1.25 { dismiss() }
            }
        )
    }
}

private struct LibraryPreviewPage: View {
    let item: MediaItem
    @Binding var isImmersive: Bool

    var body: some View {
        Group {
            if item.isVideo {
                PhotoLibraryVideoPlayer(assetID: item.id) {
                    withAnimation(.easeInOut(duration: 0.2)) { isImmersive.toggle() }
                }
            } else {
                AssetImage(assetID: item.id, fallback: item.fallbackStyle, cornerRadius: 0, contentMode: .fit)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

private struct PhotoLibraryVideoPlayer: View {
    let assetID: String
    let toggleFullscreen: () -> Void
    @State private var player: AVPlayer?

    var body: some View {
        ZStack {
            Color.black
            if let player {
                VideoPlayer(player: player)
            } else {
                ProgressView()
                    .tint(.white)
            }
        }
        .simultaneousGesture(TapGesture(count: 2).onEnded(toggleFullscreen))
        .task(id: assetID) { await load() }
        .onDisappear { player?.pause() }
    }

    private func load() async {
        player?.pause()
        player = nil
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [assetID], options: nil).firstObject else { return }
        let options = PHVideoRequestOptions()
        options.deliveryMode = .automatic
        options.isNetworkAccessAllowed = true
        let item = await withCheckedContinuation { continuation in
            PHImageManager.default().requestPlayerItem(forVideo: asset, options: options) { item, _ in
                continuation.resume(returning: item)
            }
        }
        guard !Task.isCancelled, let item else { return }
        player = AVPlayer(playerItem: item)
    }
}

private extension MediaItem {
    var fallbackStyle: ThumbnailStyle {
        switch kind {
        case .screenshot, .screenRecording: .screen
        case .video: .mountain
        case .photo: .sunset
        }
    }
}

#Preview {
    RootView()
}
