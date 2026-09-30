import SwiftUI

/// Review-before-delete grid. Nothing is removed until the user taps Delete *and* confirms the
/// system prompt; deleted items stay in Recently Deleted for 30 days.
struct ReviewView: View {
    enum Source: Hashable {
        case kind(ReviewKind)
        case group(String)
    }

    let source: Source

    @Environment(LibraryStore.self) private var library
    /// nil until the user changes it, so the default follows the current items.
    @State private var selection: Set<String>?
    @State private var isDeleting = false

    private var items: [ReviewItem] {
        switch source {
        case .kind(let kind):
            return library.content.reviewSets[kind] ?? []
        case .group(let id):
            guard let group = library.content.photoGroups.first(where: { $0.id == id }) else { return [] }
            let extras = (library.content.reviewSets[.similar] ?? []).filter { group.assetIDs.contains($0.id) }
            let keeperID = group.assetIDs[group.recommendedIndex]
            let keeper = ReviewItem(id: keeperID, bytes: group.bytes - group.reclaimableBytes, isVideo: false, duration: 0,
                                    createdAt: .distantPast, preselected: false, isKeeper: true)
            // Keep capture order so the burst reads naturally.
            let byID = Dictionary(uniqueKeysWithValues: (extras + [keeper]).map { ($0.id, $0) })
            return group.assetIDs.compactMap { byID[$0] }
        }
    }

    private var title: String {
        switch source {
        case .kind(let kind): kind.title
        case .group: "Review Group"
        }
    }

    private var explanation: String {
        switch source {
        case .kind(let kind): kind.explanation
        case .group: "The best photo is kept. Select the others you don't need."
        }
    }

    var body: some View {
        let items = items
        // Results may refresh while this screen is open. Ignore stale IDs and keepers so
        // Select All always means every currently visible, deletable item.
        let selected = items.validSelection(selection)
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: title, subtitle: explanation)
                .padding(.bottom, 4)

            if case .kind(let kind) = source {
                StatusBadge(text: kind.safety.title, systemImage: kind.safety.systemImage, tint: kind.safety.tint)
            }

            if items.isEmpty {
                Card(style: .info) {
                    Label("Nothing left to review here.", systemImage: "checkmark.circle")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            } else {
                selectionBar(items: items, selected: selected)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 8)], spacing: 8) {
                    ForEach(items) { item in
                        ReviewTile(item: item, isSelected: selected.contains(item.id)) {
                            toggle(item, in: selected)
                        }
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if !items.isEmpty { deleteBar(items: items, selected: selected) }
        }
    }

    private func selectionBar(items: [ReviewItem], selected: Set<String>) -> some View {
        let selectableIDs = items.selectableIDs
        let allSelected = !selectableIDs.isEmpty && selected == selectableIDs
        return HStack {
            Text("\(selected.count) of \(selectableIDs.count) selected · \(items.bytes(of: selected).formattedBytes)")
                .font(Typography.metadata)
                .foregroundStyle(Palette.textSecondary)
            Spacer()
            Button(allSelected ? "Deselect All" : "Select All") {
                selection = allSelected ? [] : selectableIDs
            }
            .font(.system(.subheadline, weight: .semibold))
        }
    }

    private func deleteBar(items: [ReviewItem], selected: Set<String>) -> some View {
        VStack(spacing: 6) {
            Button {
                Task {
                    isDeleting = true
                    let outcome = await library.delete(selected)
                    isDeleting = false
                    // Everything selected is gone; what's left was deliberately unselected, so keep it that way.
                    if case .deleted = outcome { selection = [] }
                }
            } label: {
                if isDeleting {
                    ProgressView().tint(.white)
                } else {
                    Label("Delete \(selected.count) · \(items.bytes(of: selected).formattedBytes)", systemImage: "trash")
                }
            }
            .buttonStyle(PrimaryButtonStyle(showsArrow: false))
            .disabled(selected.isEmpty || isDeleting)

            Text("You'll confirm in the next step. Items stay in Recently Deleted for 30 days.")
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: Metrics.readableWidth)
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .frame(maxWidth: .infinity)
        .background(.bar)
    }

    private func toggle(_ item: ReviewItem, in current: Set<String>) {
        guard !item.isKeeper else { return }
        var next = current
        if next.contains(item.id) { next.remove(item.id) } else { next.insert(item.id) }
        selection = next
    }
}

private struct ReviewTile: View {
    let item: ReviewItem
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            AssetImage(assetID: item.id, fallback: item.isVideo ? .mountain : .sunset, cornerRadius: 12, contentMode: .fit)
                .aspectRatio(1, contentMode: .fit)
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(.black.opacity(isSelected ? 0.12 : 0))
                }
                .overlay(alignment: .topTrailing) {
                    if !item.isKeeper {
                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 24))
                            .foregroundStyle(isSelected ? Palette.accent : .white)
                            .background(Circle().fill(isSelected ? .white : .black.opacity(0.25)).padding(2))
                            .padding(6)
                    }
                }
                .overlay(alignment: .bottomLeading) {
                    HStack(spacing: 4) {
                        if item.isKeeper {
                            Label("Best", systemImage: "sparkles")
                                .font(.system(.caption2, weight: .semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(Palette.accent, in: Capsule())
                        } else if item.isVideo {
                            DurationBadge(text: Duration.seconds(item.duration).formatted(.time(pattern: .minuteSecond)))
                        }
                    }
                    .padding(6)
                }
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(isSelected ? Palette.accent : .clear, lineWidth: 2.5)
                }
        }
        .buttonStyle(.plain)
        .disabled(item.isKeeper)
        .accessibilityLabel(item.isKeeper ? "Best photo, kept" : "\(item.isVideo ? "Video" : "Photo"), \(item.bytes.formattedBytes)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

#Preview {
    NavigationStack { ReviewView(source: .kind(.similar)) }
        .previewEnvironment()
}
