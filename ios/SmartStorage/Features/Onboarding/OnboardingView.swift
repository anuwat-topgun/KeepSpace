import SwiftUI

/// 01 — Onboarding: value proposition and the on-device privacy promise.
/// On iPad the illustration moves beside the copy instead of above the cards.
struct OnboardingView: View {
    let onContinue: () -> Void
    @State private var showsLearnMore = false

    private let benefits: [(String, Tint, String, String)] = [
        ("sparkles", .teal, "Find duplicates", "Spot and remove similar photos, videos, and large files."),
        ("heart.fill", .coral, "Protect memories", "Keep your best photos and videos safe and organized."),
        ("cylinder.split.1x2.fill", .purple, "Recover storage safely", "Free up space with smart, on-device analysis."),
    ]

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .center, spacing: 32) {
                    VStack(alignment: .leading, spacing: 20) {
                        headline
                        cards
                    }
                    .frame(width: 480)
                    StorageIllustration()
                        .frame(minWidth: 300, maxWidth: .infinity)
                        .frame(height: 420)
                }
                VStack(alignment: .leading, spacing: 20) {
                    headline
                    StorageIllustration()
                        .frame(height: 280)
                    cards
                }
            }
            .padding(.top, 24)

            VStack(spacing: 12) {
                Button("Continue", action: onContinue)
                    .buttonStyle(.primary)
                    .frame(maxWidth: Metrics.readableWidth)
                Button("Learn More") { showsLearnMore = true }
                    .font(.system(.body, weight: .medium))
                    .foregroundStyle(Palette.textSecondary)
                    .padding(.vertical, 8)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 8)
        }
        .sheet(isPresented: $showsLearnMore) {
            PrivacyDetailSheet()
        }
    }

    private var headline: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Free up space,\nkeep what matters.")
                .font(.system(size: 40, weight: .heavy))
                .foregroundStyle(Palette.textPrimary)
                .minimumScaleFactor(0.7)
                .accessibilityAddTraits(.isHeader)
            Text("AI runs entirely on your device.")
                .font(Typography.screenSubtitle)
                .foregroundStyle(Palette.textSecondary)
        }
    }

    private var cards: some View {
        VStack(spacing: Metrics.stackSpacing) {
            ForEach(benefits, id: \.2) { icon, tint, title, detail in
                Card(padding: 16) {
                    ListTile(systemImage: icon, tint: tint, title: title, subtitle: detail, showsChevron: false)
                }
            }
            Card(style: .info, padding: 16) {
                HStack(spacing: 16) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(Palette.accent)
                        .frame(width: 52, height: 52)
                        .background(Tint.teal.background, in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Your photos never leave your phone.")
                            .font(Typography.cardHeadline)
                            .foregroundStyle(Palette.textPrimary)
                        Text("All analysis happens on your device, keeping your data private and secure.")
                            .font(Typography.metadata)
                            .foregroundStyle(Palette.textSecondary)
                    }
                }
            }
        }
    }
}

/// Floating glass tiles around a storage block — echoes the mockup hero art.
private struct StorageIllustration: View {
    var body: some View {
        GeometryReader { proxy in
            let w = proxy.size.width, h = proxy.size.height
            ZStack {
                Circle()
                    .fill(Palette.icyBlue)
                    .frame(width: min(w, h) * 0.9)
                    .blur(radius: 40)
                glassTile(symbol: "photo", size: h * 0.36).rotationEffect(.degrees(-8)).position(x: w * 0.24, y: h * 0.3)
                glassTile(symbol: "play.fill", size: h * 0.3).rotationEffect(.degrees(8)).position(x: w * 0.76, y: h * 0.24)
                glassTile(symbol: "doc.text", size: h * 0.3).rotationEffect(.degrees(6)).position(x: w * 0.82, y: h * 0.7)
                MediaThumbnail(style: .mountain, cornerRadius: 16, symbolScale: 0.3)
                    .frame(width: h * 0.4, height: h * 0.3)
                    .rotationEffect(.degrees(-10))
                    .shadow(color: .black.opacity(0.1), radius: 10, y: 6)
                    .position(x: w * 0.2, y: h * 0.74)
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(LinearGradient(colors: [Palette.surface, Color(light: 0xBFE3F2, dark: 0x2A5268)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: h * 0.46, height: h * 0.46)
                    .overlay(alignment: .bottom) {
                        Capsule().fill(Palette.accent.opacity(0.5)).frame(width: 30, height: 6).padding(.bottom, 16)
                    }
                    .rotation3DEffect(.degrees(30), axis: (x: 1, y: 0, z: 0.4))
                    .shadow(color: Palette.accent.opacity(0.25), radius: 20, y: 12)
                    .position(x: w * 0.5, y: h * 0.52)
            }
        }
        .accessibilityHidden(true)
    }

    private func glassTile(symbol: String, size: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: size * 0.24, style: .continuous)
            .fill(Palette.surface.opacity(0.7))
            .overlay(RoundedRectangle(cornerRadius: size * 0.24, style: .continuous).strokeBorder(.white.opacity(0.8)))
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: symbol)
                    .font(.system(size: size * 0.32, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: size * 0.6, height: size * 0.6)
                    .background(Color(light: 0x7FC3DD, dark: 0x3F8AA8), in: RoundedRectangle(cornerRadius: size * 0.14, style: .continuous))
            )
            .shadow(color: Palette.accent.opacity(0.15), radius: 12, y: 6)
    }
}

private struct PrivacyDetailSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScreenScaffold {
                ScreenHeader(title: "Your privacy", subtitle: "How KeepSpace handles your media.")
                ForEach(points, id: \.0) { icon, title, detail in
                    Card(padding: 16) {
                        ListTile(systemImage: icon, tint: .teal, title: title, subtitle: detail, showsChevron: false)
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
    }

    private var points: [(String, String, String)] {
        [
            ("cpu", "AI runs on this device", "Duplicate detection, best-shot picks and screenshot understanding use on-device models only."),
            ("xmark.icloud", "No uploads to our servers", "Your photos, videos and extracted text are never sent to KeepSpace."),
            ("icloud.and.arrow.up", "Backups go where you choose", "If you enable cloud backup, files upload directly to your Google Drive or OneDrive."),
            ("trash.slash", "Nothing is deleted silently", "You review every cleanup, and important memories are protected by default."),
        ]
    }
}

#Preview {
    OnboardingView(onContinue: {})
}
