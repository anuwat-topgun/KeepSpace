import SwiftUI

/// 11 — Screenshots grouped by what they contain, plus time-sensitive content that has expired.
struct ScreenshotsView: View {
    @Environment(LibraryStore.self) private var library

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Screenshots", subtitle: "\(library.content.screenshotsBytes.formattedBytes) recoverable")
                .padding(.bottom, 8)

            AdaptiveGrid {
                ForEach(library.content.screenshotCategories) { category in
                    CardRow(systemImage: category.systemImage, tint: category.tint, title: category.title, subtitle: category.bytes.formattedBytes)
                }
            }

            if library.content.screenshotCategories.isEmpty {
                Card(style: .info) {
                    Label(library.isScanning ? "Reading your library…" : "No screenshots found.", systemImage: "viewfinder")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }

            if !library.content.expiredScreenshots.isEmpty {
                ExpiredContentCard(items: library.content.expiredScreenshots)
            }
        }
    }
}

private struct ExpiredContentCard: View {
    let items: [ExpiredScreenshot]

    var body: some View {
        Card(style: .info) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 16) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(Tint.blue.foreground)
                        .frame(width: 52, height: 52)
                        .background(Palette.surface.opacity(0.7), in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Expired Content")
                            .font(.system(.title3, weight: .bold))
                            .foregroundStyle(Palette.textPrimary)
                        Text("Screenshots from past events, trips, and time-sensitive content you may not need.")
                            .font(Typography.metadata)
                            .foregroundStyle(Palette.textSecondary)
                    }
                }

                ForEach(items) { item in
                    Card(padding: 14) {
                        HStack(spacing: 16) {
                            BoardingPassArt()
                                .frame(width: 120, height: 96)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title)
                                    .font(Typography.cardHeadline)
                                    .foregroundStyle(Palette.textPrimary)
                                Text(item.detail)
                                    .font(Typography.metadata)
                                    .foregroundStyle(Palette.textSecondary)
                                Text(item.status)
                                    .font(Typography.metadata)
                                    .foregroundStyle(Palette.textSecondary)
                                StatusBadge(text: "Review for deletion", systemImage: "trash", tint: .coral, compact: true)
                                    .padding(.top, 4)
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.right")
                                .font(.system(.body, weight: .semibold))
                                .foregroundStyle(Palette.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

/// Small illustrated boarding pass (BKK → TYO) used for the expired-content preview.
private struct BoardingPassArt: View {
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "airplane")
                Spacer()
                Capsule().fill(.white.opacity(0.6)).frame(width: 30, height: 4)
            }
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .frame(height: 26)
            .background(Tint.blue.foreground)

            VStack(spacing: 6) {
                HStack(spacing: 6) {
                    Text("BKK")
                    Image(systemName: "arrow.right").foregroundStyle(Tint.blue.foreground)
                    Text("TYO")
                }
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Palette.textPrimary)
                Image(systemName: "qrcode")
                    .font(.system(size: 24))
                    .foregroundStyle(Palette.textPrimary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Palette.surface)
        }
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
        .accessibilityHidden(true)
    }
}

#Preview {
    NavigationStack { ScreenshotsView() }
        .previewEnvironment()
}
