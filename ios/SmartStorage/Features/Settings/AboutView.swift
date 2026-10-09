import SwiftUI

/// About: version and the open-source pieces KeepSpace is built on.
struct AboutView: View {
    private let privacyURL = URL(string: "https://keepspace.itston.com/privacy/")!
    private let termsURL = URL(string: "https://keepspace.itston.com/terms/")!

    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "1.0") (\(info?["CFBundleVersion"] as? String ?? "1"))"
    }

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "About", subtitle: "Keep space. Keep what matters.")
                .padding(.bottom, 8)

            Card(padding: 16) {
                ListTile(systemImage: "app.badge.fill", tint: .teal, title: "KeepSpace", subtitle: localizedFormat("Version %@", version), showsChevron: false)
            }
            Card(style: .info) {
                Label("AI processing never leaves your device. Files go only to the cloud destinations you choose.", systemImage: "lock.shield")
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
            }
            Card(padding: 16) {
                VStack(spacing: 10) {
                    Link("Privacy Policy", destination: privacyURL)
                        .buttonStyle(.secondaryOutlined)
                        .accessibilityHint("Opens the KeepSpace privacy policy in your browser")
                    Link("Terms of Use", destination: termsURL)
                        .buttonStyle(.secondaryOutlined)
                        .accessibilityHint("Opens the KeepSpace terms of use in your browser")
                }
            }
            Card(padding: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Built with").font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                    Text("Apple Vision, Photos, SwiftUI and SwiftData — all part of iOS. No third-party libraries are included in this version.")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }
    }
}

#Preview {
    NavigationStack { AboutView() }.previewEnvironment()
}
