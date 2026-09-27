import SwiftUI

/// Stand-in for screens that are routed but not built yet.
struct PlaceholderScreen: View {
    let title: String

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: title, subtitle: "This screen is coming soon.")
            Card(style: .info) {
                HStack(spacing: 16) {
                    IconTile(systemName: "hammer.fill", tint: .gray)
                    Text("Routed from the app shell; implementation lands in a later step.")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }
    }
}
