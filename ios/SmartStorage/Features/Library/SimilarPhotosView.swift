import SwiftUI

/// 05 — Similar photo groups.
/// Phone: list → push Best Shot. Wide tablet: list on the left, Best Shot for the selected group on the right.
struct SimilarPhotosView: View {
    @Environment(AppRouter.self) private var router
    @Environment(LibraryStore.self) private var library
    @State private var filter: PhotoGroupFilter = .all
    @State private var selectedGroupID: String?

    private var groups: [PhotoGroup] { library.content.photoGroups }
    /// Below this width the two-pane layout would cramp both panes.
    private let twoPaneMinWidth: CGFloat = 820

    var body: some View {
        GeometryReader { proxy in
            if proxy.size.width >= twoPaneMinWidth {
                twoPane
            } else {
                list(selectable: false)
            }
        }
        .background(Palette.background.ignoresSafeArea())
    }

    private var twoPane: some View {
        HStack(spacing: 0) {
            list(selectable: true)
                .frame(width: 420)
            Divider()
            if let group = groups.first(where: { $0.id == selectedGroupID }) ?? groups.first {
                BestShotView(group: group)
                    .id(group.id)
                    .transition(.opacity)
            } else {
                ContentUnavailableView("Select a group", systemImage: "photo.on.rectangle.angled")
            }
        }
        .animation(.easeInOut(duration: 0.2), value: selectedGroupID)
    }

    private func list(selectable: Bool) -> some View {
        ScreenScaffold {
            ScreenHeader(
                title: "Similar Photos",
                subtitle: "\(library.content.similarBytes.formattedBytes) recoverable · \(groups.count) groups"
            )

            if groups.isEmpty {
                Card(style: .info) {
                    Label(
                        library.isScanning ? "Looking for similar photos on this device…" : "No similar photos found.",
                        systemImage: library.isScanning ? "sparkles" : "checkmark.circle"
                    )
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
                }
            }

            ChipPicker(options: PhotoGroupFilter.allCases, selection: $filter) { $0.rawValue }
                .padding(.vertical, 4)

            ForEach(groups) { group in
                Button {
                    if selectable {
                        selectedGroupID = group.id
                    } else {
                        router.push(.bestShot(groupID: group.id))
                    }
                } label: {
                    PhotoGroupCard(group: group, isSelected: selectable && group.id == (selectedGroupID ?? groups.first?.id))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct PhotoGroupCard: View {
    let group: PhotoGroup
    var isSelected = false

    var body: some View {
        Card(style: isSelected ? .selected : .plain, padding: 16) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 16) {
                    IconTile(systemName: group.systemImage, tint: group.tint)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(group.title)
                            .font(Typography.cardHeadline)
                            .foregroundStyle(Palette.textPrimary)
                        Text("\(group.photoCount) photos · \((group.reclaimableBytes > 0 ? group.reclaimableBytes : group.bytes).formattedBytes)\(group.reclaimableBytes > 0 ? " recoverable" : "")")
                            .font(Typography.metadata)
                            .foregroundStyle(Palette.textSecondary)
                        StatusBadge(text: "Recommended keep selected", systemImage: "sparkles", tint: .teal, compact: true)
                            .minimumScaleFactor(0.85)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.system(.body, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                        .padding(.top, 14)
                }
                ThumbnailStrip(style: group.style, totalCount: group.photoCount, assetIDs: group.assetIDs)
            }
        }
    }
}

#Preview {
    NavigationStack { SimilarPhotosView() }
        .previewEnvironment()
}
