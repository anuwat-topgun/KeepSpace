import SwiftUI

/// Rounded list tile: icon tile + title + subtitle + trailing accessory (chevron by default).
/// Used for cleanup categories, settings rows, screenshot categories, rules.
struct ListTile<Trailing: View>: View {
    let systemImage: String
    var tint: Tint = .teal
    let title: String
    var subtitle: String?
    var showsChevron = true
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: 16) {
            IconTile(systemName: systemImage, tint: tint)
            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey(title))
                    .font(Typography.cardHeadline)
                    .foregroundStyle(Palette.textPrimary)
                if let subtitle {
                    Text(LocalizedStringKey(subtitle))
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }
            Spacer(minLength: 8)
            trailing
            if showsChevron {
                Image(systemName: "chevron.right")
                    .font(.system(.body, weight: .semibold))
                    .foregroundStyle(Palette.textSecondary)
                    .accessibilityHidden(true)
            }
        }
        .contentShape(Rectangle())
    }
}

extension ListTile where Trailing == EmptyView {
    init(systemImage: String, tint: Tint = .teal, title: String, subtitle: String? = nil, showsChevron: Bool = true) {
        self.init(systemImage: systemImage, tint: tint, title: title, subtitle: subtitle, showsChevron: showsChevron) {
            EmptyView()
        }
    }
}

/// A `ListTile` wrapped in its own plain card — the dominant row pattern in the mockups.
struct CardRow: View {
    let systemImage: String
    var tint: Tint = .teal
    let title: String
    var subtitle: String?

    var body: some View {
        Card(padding: 16) {
            ListTile(systemImage: systemImage, tint: tint, title: title, subtitle: subtitle)
        }
    }
}

/// Form row with a toggle ("Upload over Wi-Fi only").
struct ToggleRow: View {
    let systemImage: String
    var tint: Tint = .teal
    let title: String
    var subtitle: String?
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            ListTile(systemImage: systemImage, tint: tint, title: title, subtitle: subtitle, showsChevron: false)
        }
        .tint(Palette.accent)
    }
}
