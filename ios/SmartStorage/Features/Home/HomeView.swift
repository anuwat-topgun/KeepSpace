import SwiftUI

/// 02 — Home dashboard.
struct HomeView: View {
    @Environment(AppRouter.self) private var router
    private let storage = MockData.storage

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Smart Storage", subtitle: "Your storage, organized intelligently.")
                .padding(.bottom, 8)

            StorageHeroCard(storage: storage) {
                router.selectedTab = .clean
            }

            AdaptiveGrid {
                ForEach(CleanupCategory.allCases) { category in
                    Button {
                        router.push(category.route)
                    } label: {
                        CardRow(
                            systemImage: category.systemImage,
                            tint: category.tint,
                            title: category.title,
                            subtitle: storage.categoryBytes[category]?.formattedBytes
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct StorageHeroCard: View {
    let storage: StorageSummary
    let onFreeUp: () -> Void

    var body: some View {
        Card(style: .hero) {
            VStack(alignment: .leading, spacing: 16) {
                SectionLabel("Device Storage")

                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(storage.usedBytes.formattedBytes)
                        .font(Typography.metricLarge)
                        .foregroundStyle(Palette.textPrimary)
                    Text("/ \(storage.totalBytes.formattedBytes)")
                        .font(.system(.title2))
                        .foregroundStyle(Palette.textSecondary)
                }

                UsageBar(fraction: storage.usedFraction)

                Text("\(storage.freeBytes.formattedBytes) Free")
                    .font(Typography.body)
                    .foregroundStyle(Palette.textSecondary)

                Divider().overlay(Palette.separator)

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 16) {
                        potentialCleanup
                        Spacer()
                        freeUpButton.fixedSize()
                    }
                    VStack(alignment: .leading, spacing: 16) {
                        potentialCleanup
                        freeUpButton
                    }
                }
            }
        }
    }

    private var potentialCleanup: some View {
        HStack(spacing: 14) {
            IconTile(systemName: "sparkles", tint: .teal, size: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text("Potential Cleanup")
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
                Text(storage.potentialCleanupBytes.formattedBytes)
                    .font(Typography.metric)
                    .foregroundStyle(Palette.textPrimary)
            }
        }
    }

    private var freeUpButton: some View {
        Button("Free Up 10 GB", action: onFreeUp)
            .buttonStyle(.primary)
    }
}

#Preview {
    TabStack(tab: .home)
        .environment(AppRouter())
}
