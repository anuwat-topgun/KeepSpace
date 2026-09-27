import SwiftUI

/// 09 — Settings. Cloud (v1.1) and Storage Rules (v1.2) are pushed from here.
struct SettingsView: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Settings")
                .padding(.bottom, 8)

            Card(style: .hero) {
                VStack(alignment: .leading, spacing: 12) {
                    IconTile(systemName: "lock.shield.fill", tint: .blue)
                    Text("On-device AI only")
                        .font(.system(.title2, weight: .bold))
                        .foregroundStyle(Palette.textPrimary)
                    Text("AI processing never leaves your device. Files are uploaded only to the cloud destinations you choose.")
                        .font(Typography.body)
                        .foregroundStyle(Palette.textSecondary)
                }
            }

            row(.cloudOverview, icon: "cloud.fill", tint: .blue, subtitle: "Google Drive · OneDrive")
            row(.storageRules, icon: "arrow.triangle.branch", tint: .teal, subtitle: "Automatic filing")

            CardRow(systemImage: "photo.fill", tint: .coral, title: "Photo Access", subtitle: "Full Access")
            CardRow(systemImage: "bell.fill", tint: .purple, title: "Notifications", subtitle: "Weekly Smart Clean")
            CardRow(systemImage: "crown.fill", tint: .mint, title: "Subscription", subtitle: "Smart Storage Pro")
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
