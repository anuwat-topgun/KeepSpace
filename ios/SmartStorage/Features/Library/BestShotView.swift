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
    @State private var selectedIndices: Set<Int>
    /// The last photo the person tapped; drives the large preview while selection remains multi-value.
    @State private var focusedIndex: Int
    @State private var kept = false
    @State private var isDeleting = false
    @State private var quotaPrompt: QuotaGatePrompt?

    private let demoPhotoCount = 4

    init(group: PhotoGroup, isEmbedded: Bool = false) {
        self.group = group
        self.isEmbedded = isEmbedded
        _selectedIndices = State(initialValue: [group.recommendedIndex])
        _focusedIndex = State(initialValue: group.recommendedIndex)
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

            if focusedIndex == group.recommendedIndex {
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
        .quotaGate($quotaPrompt) { ids in Task { await performKeep(keeperIndices: selectedIndices, limitingTo: Set(ids)) } }
    }

    private var strip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(0..<photoCount, id: \.self) { index in
                    let isRecommended = index == group.recommendedIndex
                    let isSelected = selectedIndices.contains(index)
                    Button {
                        toggleSelection(index)
                    } label: {
                        VStack(spacing: 7) {
                            AssetImage(assetID: assetID(index), fallback: group.style, variant: index, cornerRadius: 14)
                                // AssetImage uses GeometryReader, so both dimensions must be explicit
                                // inside a horizontal ScrollView or SwiftUI can collapse its height.
                                .frame(width: 112, height: 148)
                                .overlay(alignment: .topTrailing) {
                                    if isRecommended {
                                        recommendedTag(Image(systemName: "sparkles"))
                                            .padding(6)
                                    }
                                }
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .strokeBorder(isSelected ? Palette.accent : .clear, lineWidth: 3)
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

                            Label {
                                Text(isRecommended ? "Recommended".localizedUI : localizedFormat("Photo %d", index + 1))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                            } icon: {
                                if isRecommended { Image(systemName: "sparkles") }
                            }
                            .font(.system(.caption2, weight: isSelected ? .bold : .semibold))
                            .foregroundStyle(isSelected ? Palette.accent : Palette.textSecondary)
                            .frame(width: 112)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(localizedFormat(isRecommended ? "Photo %d, recommended" : "Photo %d", index + 1))
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(.horizontal, 4)
        }
        .frame(minHeight: 177)
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
        AssetImage(assetID: assetID(focusedIndex), fallback: group.style, variant: focusedIndex,
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
                    Text(keepButtonTitle.localizedUI)
                }
            }
            .buttonStyle(PrimaryButtonStyle(showsArrow: false))
            .disabled(kept || isDeleting)

            Text("Select one or more photos to keep. Tap a photo to add or remove it.".localizedUI)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Palette.textPrimary)
                .multilineTextAlignment(.center)

            if !group.assetIDs.isEmpty {
                Text(deletionExplanation)
                    .font(.caption)
                    .foregroundStyle(Palette.textSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.top, 4)
    }

    private var keepButtonTitle: String {
        if kept { return "Done" }
        if selectedIndices == Set([group.recommendedIndex]) { return "Keep Recommended" }
        return selectedIndices.count == 1 ? "Keep Selected Photo" : "Keep Selected Photos"
    }

    private var deletionExplanation: String {
        let deleteCount = max(0, group.photoCount - selectedIndices.count)
        if selectedIndices.count == 1 {
            return localizedFormat("Keeping this photo deletes the other %d after you confirm. They stay in Recently Deleted for 30 days.", deleteCount)
        }
        return localizedFormat("Keeping %d photos deletes the other %d after you confirm. They stay in Recently Deleted for 30 days.", selectedIndices.count, deleteCount)
    }

    private func toggleSelection(_ index: Int) {
        focusedIndex = index
        if selectedIndices.contains(index) {
            // At least one keeper is required; tapping the last one only keeps it focused.
            guard selectedIndices.count > 1 else { return }
            selectedIndices.remove(index)
            if focusedIndex == index { focusedIndex = selectedIndices.sorted().first ?? group.recommendedIndex }
        } else {
            selectedIndices.insert(index)
        }
    }

    /// Deletes every photo in the group except the person's one or more selections.
    private func keepSelected() async {
        guard !group.assetIDs.isEmpty else { kept = true; return } // demo content
        // The free monthly allowance first; over it, the quota gate is offered instead of deleting.
        let others = group.assetIDs.enumerated().filter { !selectedIndices.contains($0.offset) }.map(\.element)
        if others.isEmpty {
            kept = true
            if !isEmbedded { dismiss() }
            return
        }
        let candidates = library.cleanupItems(others, safety: ReviewKind.similar.safety)
        let allowances = monetization.allowances
        if let prompt = QuotaGatePrompt(allowances.gate(cleanup: candidates), selection: candidates, allowances: allowances) {
            quotaPrompt = prompt
            return
        }
        await performKeep(keeperIndices: selectedIndices, limitingTo: nil)
    }

    private func performKeep(keeperIndices: Set<Int>, limitingTo allowed: Set<String>?) async {
        isDeleting = true
        let outcome = await library.keep(group, keeperIndices: keeperIndices, limitingTo: allowed)
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
