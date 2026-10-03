import SwiftUI

/// 09 — Settings with the privacy stance front and centre.
/// Only features that are functional in the Store build are exposed here.
struct SettingsView: View {
    @Environment(AppRouter.self) private var router
    @Environment(LibraryStore.self) private var library
    @Environment(WeeklyCleanReminder.self) private var weekly
    @Environment(TasteStore.self) private var taste
    @Environment(CloudStore.self) private var cloud
    @Environment(MonetizationStore.self) private var monetization

    private var accessText: String {
        switch library.access {
        case .authorized: "Full Access"
        case .limited: "Limited Access"
        case .denied: "Off"
        case .notDetermined: "Not set"
        }
    }

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Settings")
                .padding(.bottom, 8)

            PrivacyHeroCard()

            row(.subscription, icon: "sparkle", tint: .teal, subtitle: SubscriptionView.settingsSubtitle(monetization.status), title: "Subscription · KeepSpace Pro")
            row(.memories, icon: "heart.fill", tint: .coral, subtitle: "Protected by default")
            row(.aiTaste, icon: "wand.and.stars", tint: .purple, locked: !monetization.isPro && taste.decisions == 0, subtitle: taste.isEnabled ? (taste.decisions == 0 ? "Learns as you choose" : "Learned from \(taste.decisions) \(taste.decisions == 1 ? "choice" : "choices")") : "Off")
            row(.cloudOverview, icon: "icloud.fill", tint: .blue,
                subtitle: cloud.connections.filter(\.isConnected).isEmpty ? "Connect Google Drive or OneDrive" : "\(cloud.connections.filter(\.isConnected).count) connected")

            row(.photoAccess, icon: "photo.fill", tint: .coral, subtitle: accessText)
            row(.notifications, icon: "bell.fill", tint: .purple, subtitle: weekly.isEnabled ? "Weekly Smart Clean · On" : "Weekly Smart Clean · Off")
            row(.privacy, icon: "checkmark.shield.fill", tint: .blue, subtitle: nil)
            row(.about, icon: "info.circle.fill", tint: .gray, subtitle: nil)
        }
    }

    private func row(_ route: Route, icon: String, tint: Tint, locked: Bool = false, subtitle: String?, title: String? = nil) -> some View {
        Button {
            router.push(route)
        } label: {
            CardRow(systemImage: icon, tint: tint, title: title ?? route.title, subtitle: subtitle, isProLocked: locked)
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
                    Text("AI stays on device. Backups go only where you choose.")
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
        .previewEnvironment()
}
