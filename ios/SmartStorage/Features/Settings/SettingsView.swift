import SwiftUI

/// 09 — Settings with the privacy stance front and centre.
/// Cloud (v1.1) and Storage Rules (v1.2) are pushed from here.
struct SettingsView: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Settings")
                .padding(.bottom, 8)

            PrivacyHeroCard()

            row(.memories, icon: "heart.fill", tint: .coral, subtitle: "Protected by default")
            row(.cloudOverview, icon: "cloud.fill", tint: .blue, subtitle: "Google Drive · OneDrive")
            row(.storageRules, icon: "arrow.triangle.branch", tint: .teal, subtitle: "Automatic filing")

            CardRow(systemImage: "photo.fill", tint: .coral, title: "Photo Access", subtitle: "Full Access")
            CardRow(systemImage: "bell.fill", tint: .purple, title: "Notifications", subtitle: "Weekly Smart Clean")
            CardRow(systemImage: "crown.fill", tint: .mint, title: "Subscription", subtitle: "KeepSpace Pro")
            CardRow(systemImage: "checkmark.shield.fill", tint: .blue, title: "Privacy & Security")
            CardRow(systemImage: "info.circle.fill", tint: .gray, title: "About")
        }
    }

    private func row(_ route: Route, icon: String, tint: Tint, subtitle: String) -> some View {
        Button {
            router.push(route)
        } label: {
            CardRow(systemImage: icon, tint: tint, title: route.title, subtitle: subtitle)
        }
        .buttonStyle(.plain)
    }
}

private struct PrivacyHeroCard: View {
    var body: some View {
        Card(style: .hero) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 12) {
                    IconTile(systemName: "lock.shield.fill", tint: .blue)
                    Text("On-device AI only")
                        .font(.system(.title2, weight: .bold))
                        .foregroundStyle(Palette.textPrimary)
                    Text("Your photos never leave your device.")
                        .font(Typography.body)
                        .foregroundStyle(Palette.textSecondary)
                }
                Spacer(minLength: 0)
                ShieldArt()
                    .frame(width: 120, height: 120)
            }
        }
    }
}

/// Soft glassy shield standing on rings — the Settings hero illustration.
private struct ShieldArt: View {
    var body: some View {
        ZStack {
            ForEach(0..<2) { ring in
                Ellipse()
                    .stroke(Tint.blue.foreground.opacity(0.18), lineWidth: 1.5)
                    .frame(width: 110 - CGFloat(ring) * 30, height: 34 - CGFloat(ring) * 10)
                    .offset(y: 38)
            }
            Image(systemName: "shield.fill")
                .font(.system(size: 78))
                .foregroundStyle(
                    LinearGradient(colors: [Color(light: 0x9ED4F5, dark: 0x4C8DB8), Color(light: 0x4AA3E0, dark: 0x2D6F9E)], startPoint: .top, endPoint: .bottom)
                )
                .shadow(color: Tint.blue.foreground.opacity(0.3), radius: 12, y: 6)
            Image(systemName: "lock.fill")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(.white.opacity(0.95))
                .offset(y: 2)
        }
        .accessibilityHidden(true)
    }
}

#Preview {
    NavigationStack { SettingsView() }
        .environment(AppRouter())
}
