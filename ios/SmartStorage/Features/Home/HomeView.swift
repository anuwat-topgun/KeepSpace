import SwiftUI

/// 02 — Home dashboard.
struct HomeView: View {
    @Environment(AppRouter.self) private var router
    @Environment(LibraryStore.self) private var library

    var body: some View {
        let storage = library.content.storage
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "KeepSpace", subtitle: "Your storage, organized intelligently.")
                .padding(.bottom, 8)

            StorageHeroCard(storage: storage, showsCleanup: library.access.canRead) {
                router.selectedTab = .clean
            }

            LibraryStatusCard()

            if library.access.canRead {
                AdaptiveGrid {
                    ForEach(CleanupCategory.allCases) { category in
                        Button {
                            router.push(category.route)
                        } label: {
                            CardRow(
                                systemImage: category.systemImage,
                                tint: category.tint,
                                title: category.title,
                                subtitle: subtitle(for: category, storage: storage)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func subtitle(for category: CleanupCategory, storage: StorageSummary) -> String {
        let bytes = storage.categoryBytes[category] ?? 0
        // Similar/blurry need the AI pass; say so rather than showing a misleading 0.
        if bytes == 0, library.isScanning, category == .similarPhotos || category == .blurryPhotos {
            return "Analyzing…"
        }
        return bytes.formattedBytes
    }
}

/// Permission prompt, limited-access note, or scan progress — whichever applies.
private struct LibraryStatusCard: View {
    @Environment(LibraryStore.self) private var library
    @Environment(\.openURL) private var openURL

    var body: some View {
        switch library.access {
        case .notDetermined:
            statusCard(
                icon: "photo.on.rectangle.angled", tint: .teal,
                title: "Allow photo access",
                detail: "KeepSpace analyzes your library on this device to find space you can safely recover.",
                action: ("Allow Access", { Task { await library.requestAccessAndScan() } })
            )
        case .denied:
            statusCard(
                icon: "lock.fill", tint: .coral,
                title: "Photo access is off",
                detail: "Turn on photo access in Settings so KeepSpace can find similar photos, large videos and screenshots.",
                action: ("Open Settings", { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } })
            )
        case .authorized, .limited:
            if case .analyzing(let done, let total) = library.phase, total > 0 {
                Card(style: .info) {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Analyzing on device · \(done) of \(total) items", systemImage: "sparkles")
                            .font(Typography.metadata)
                            .foregroundStyle(Palette.textSecondary)
                        ProgressView(value: Double(done), total: Double(total))
                            .tint(Palette.accent)
                    }
                }
            } else if library.phase == .loadingLibrary {
                Card(style: .info) {
                    Label("Reading your library…", systemImage: "photo.stack")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            } else if library.access == .limited {
                statusCard(
                    icon: "photo.badge.exclamationmark", tint: .amber,
                    title: "Limited access",
                    detail: "KeepSpace only sees the photos you selected. Allow full access for a complete cleanup.",
                    action: ("Open Settings", { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } })
                )
            }
        }
    }

    private func statusCard(icon: String, tint: Tint, title: String, detail: String, action: (String, () -> Void)) -> some View {
        Card(style: .info) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 14) {
                    IconTile(systemName: icon, tint: tint, size: 48)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title)
                            .font(Typography.cardHeadline)
                            .foregroundStyle(Palette.textPrimary)
                        Text(detail)
                            .font(Typography.metadata)
                            .foregroundStyle(Palette.textSecondary)
                    }
                }
                Button(action.0, action: action.1)
                    .buttonStyle(PrimaryButtonStyle(showsArrow: false))
            }
        }
    }
}

private struct StorageHeroCard: View {
    let storage: StorageSummary
    let showsCleanup: Bool
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

                if showsCleanup {
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
        Button("Free Up Space", action: onFreeUp)
            .buttonStyle(.primary)
    }
}

#Preview {
    TabStack(tab: .home)
        .previewEnvironment()
}
