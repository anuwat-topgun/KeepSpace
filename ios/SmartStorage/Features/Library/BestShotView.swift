import SwiftUI

/// 06 — Best Shot: the AI's pick for a group, with a plain-language explanation.
/// On wide layouts the hero image and the "Why this one" card sit side by side.
struct BestShotView: View {
    let group: PhotoGroup
    @State private var selectedIndex: Int
    @State private var kept = false

    private let reasons = MockData.bestShotReasons
    private let stripCount = 4

    init(group: PhotoGroup) {
        self.group = group
        _selectedIndex = State(initialValue: min(group.recommendedIndex, 3))
    }

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Best Shot", subtitle: "AI selected the best photo in this group.")
                .padding(.bottom, 4)

            strip

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

            actions
        }
        .sensoryFeedback(.success, trigger: kept)
    }

    private var strip: some View {
        HStack(spacing: 10) {
            ForEach(0..<stripCount, id: \.self) { index in
                let isRecommended = index == min(group.recommendedIndex, stripCount - 1)
                let isSelected = index == selectedIndex
                Button {
                    withAnimation(.spring(duration: 0.3)) { selectedIndex = index }
                } label: {
                    MediaThumbnail(style: group.style, variant: index, cornerRadius: 14)
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
                        .scaleEffect(isSelected ? 1.03 : 1)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Photo \(index + 1)\(isRecommended ? ", recommended" : "")")
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
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
        MediaThumbnail(style: group.style, variant: selectedIndex, cornerRadius: Metrics.cardRadius, symbolScale: 0.22)
            .aspectRatio(4 / 3, contentMode: .fit)
            .id(selectedIndex)
            .transition(.opacity)
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
                            Text(reason.title)
                                .font(.system(.body, weight: .medium))
                                .foregroundStyle(Palette.textPrimary)
                            Text(reason.detail)
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
            Button(kept ? "Recommended Kept" : "Keep Recommended") {
                kept = true
            }
            .buttonStyle(PrimaryButtonStyle(showsArrow: false))
            .disabled(kept)

            Button("Review All") {}
                .buttonStyle(.secondary)
        }
        .padding(.top, 4)
    }
}

#Preview {
    NavigationStack { BestShotView(group: MockData.photoGroups[0]) }
}
