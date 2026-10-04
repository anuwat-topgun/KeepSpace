import SwiftUI

/// 06 — Best Shot: the AI's pick for a group, with a plain-language explanation.
/// On wide layouts the hero image and the "Why this one" card sit side by side.
struct BestShotView: View {
    let group: PhotoGroup
    /// True when shown as the detail pane next to the group list (no navigation to pop).
    var isEmbedded = false
    @Environment(LibraryStore.self) private var library
    @Environment(MonetizationStore.self) private var monetization
    @Environment(\.dismiss) private var dismiss
    @State private var selectedIndex: Int
    @State private var kept = false
    @State private var isDeleting = false
    @State private var quotaPrompt: QuotaGatePrompt?

    private let demoPhotoCount = 4

    init(group: PhotoGroup, isEmbedded: Bool = false) {
        self.group = group
        self.isEmbedded = isEmbedded
        _selectedIndex = State(initialValue: group.recommendedIndex)
    }

    private var reasons: [BestShotReason] {
        group.reasons.isEmpty ? MockData.bestShotReasons : group.reasons
    }

    private var photoCount: Int {
        group.assetIDs.isEmpty ? demoPhotoCount : group.assetIDs.count
    }

    private func assetID(_ index: Int) -> String? {
        group.assetIDs.indices.contains(index) ? group.assetIDs[index] : nil
    }

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Best Shot", subtitle: "AI selected the best photo in this group.")
                .padding(.bottom, 4)

            actions
            strip

            if selectedIndex == group.recommendedIndex {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .top, spacing: Metrics.stackSpacing) {
                        hero.frame(minWidth: 380)
                        reasonsCard.frame(width: 340)
                    }
                    VStack(spacing: Metrics.stackSpacing) {
                        hero
                        reasonsCard
                    }
                }
            } else {
                hero
            }

        }
        .sensoryFeedback(.success, trigger: kept)
        .quotaGate($quotaPrompt) { ids in Task { await performKeep(keeperIndex: selectedIndex, limitingTo: Set(ids)) } }
    }

    private var strip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(0..<photoCount, id: \.self) { index in
                    let isRecommended = index == group.recommendedIndex
                    let isSelected = index == selectedIndex
                    Button {
                        selectedIndex = index
                    } label: {
                        AssetImage(assetID: assetID(index), fallback: group.style, variant: index, cornerRadius: 14)
                            .frame(width: 104)
                            .aspectRatio(0.72, contentMode: .fit)
                            .overlay(alignment: .top) {
                                if isRecommended {
                                    ViewThatFits(in: .horizontal) {
                                        recommendedTag(Label("Recommended", systemImage: "sparkles"))
                                        recommendedTag(Image(systemName: "sparkles"))
                                    }
                                    .padding(6)
                                }
                            }
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .strokeBorder(isSelected ? Palette.accent : .clear, lineWidth: 3)
                                    .padding(-4)
                            )
                            .overlay(alignment: .bottomTrailing) {
                                if isSelected {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.title3)
                                        .foregroundStyle(Palette.accent)
                                        .background(Circle().fill(.white).padding(2))
                                        .padding(6)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(localizedFormat(isRecommended ? "Photo %d, recommended" : "Photo %d", index + 1))
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(.horizontal, 4)
        }
        .padding(.vertical, 6)
    }

    private func recommendedTag(_ content: some View) -> some View {
        content
            .font(.system(.caption2, weight: .semibold))
            .lineLimit(1)
            .fixedSize()
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Palette.accent, in: Capsule())
    }

    private var hero: some View {
        AssetImage(assetID: assetID(selectedIndex), fallback: group.style, variant: selectedIndex,
                   cornerRadius: Metrics.cardRadius, symbolScale: 0.22, contentMode: .fit)
            .aspectRatio(4 / 3, contentMode: .fit)
    }

    private var reasonsCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 12) {
                Text("Why this one")
                    .font(.system(.title3, weight: .bold))
                    .foregroundStyle(Palette.textPrimary)
                ForEach(Array(reasons.enumerated()), id: \.element.id) { index, reason in
                    if index > 0 { Divider().padding(.leading, 60) }
                    HStack(spacing: 14) {
                        IconTile(systemName: reason.systemImage, tint: reason.tint, size: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(reason.title.localizedUI)
                                .font(.system(.body, weight: .medium))
                                .foregroundStyle(Palette.textPrimary)
                            Text(reason.detail.localizedUI)
                                .font(Typography.metadata)
                                .foregroundStyle(Palette.textSecondary)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }

    private var actions: some View {
        VStack(spacing: 12) {
            Button {
                Task { await keepSelected() }
            } label: {
                if isDeleting {
                    ProgressView().tint(.white)
                } else {
                    Text((kept ? "Done" : selectedIndex == group.recommendedIndex ? "Keep Recommended" : "Keep Selected Photo").localizedUI)
                }
            }
            .buttonStyle(PrimaryButtonStyle(showsArrow: false))
            .disabled(kept || isDeleting)

            if !group.assetIDs.isEmpty {
                Text(localizedFormat("Keeping this photo deletes the other %d after you confirm. They stay in Recently Deleted for 30 days.", group.photoCount - 1))
                    .font(.caption)
                    .foregroundStyle(Palette.textSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.top, 4)
    }

    /// Deletes every photo in the group except the recommendation or the user's own selection.
    private func keepSelected() async {
        guard !group.assetIDs.isEmpty else { kept = true; return } // demo content
        // The free monthly allowance first; over it, the quota gate is offered instead of deleting.
        let others = group.assetIDs.enumerated().filter { $0.offset != selectedIndex }.map(\.element)
        let candidates = library.cleanupItems(others, safety: ReviewKind.similar.safety)
        let allowances = monetization.allowances
        if let prompt = QuotaGatePrompt(allowances.gate(cleanup: candidates), selection: candidates, allowances: allowances) {
            quotaPrompt = prompt
            return
        }
        await performKeep(keeperIndex: selectedIndex, limitingTo: nil)
    }

    private func performKeep(keeperIndex: Int, limitingTo allowed: Set<String>?) async {
        isDeleting = true
        let outcome = await library.keep(group, keeperIndex: keeperIndex, limitingTo: allowed)
        isDeleting = false
        if case .deleted = outcome {
            kept = true
            if !isEmbedded { dismiss() }
        }
    }
}

#Preview {
    NavigationStack { BestShotView(group: MockData.photoGroups[0]) }
        .previewEnvironment()
}
