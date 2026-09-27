import SwiftUI

/// Soft capsule badge: "Protected", "Connected", "Recommended keep selected", "Verified in Google Drive".
struct StatusBadge: View {
    let text: String
    var systemImage: String?
    var tint: Tint = .teal

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                Image(systemName: systemImage)
            }
            Text(text)
        }
        .font(.system(.subheadline, weight: .medium))
        .foregroundStyle(tint.foreground)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(tint.background, in: Capsule())
    }
}

extension StatusBadge {
    static func protected() -> StatusBadge {
        StatusBadge(text: "Protected", systemImage: "lock.fill", tint: .teal)
    }

    static func connected() -> StatusBadge {
        StatusBadge(text: "Connected", systemImage: "checkmark.circle.fill", tint: .mint)
    }

    static func verified(in provider: String) -> StatusBadge {
        StatusBadge(text: "Verified in \(provider)", systemImage: "checkmark.icloud.fill", tint: .blue)
    }
}

/// Horizontal filter chips ("All / Recent / Reviewed").
struct ChipPicker<Option: Hashable>: View {
    let options: [Option]
    @Binding var selection: Option
    let title: (Option) -> String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(options, id: \.self) { option in
                    let isSelected = option == selection
                    Button {
                        withAnimation(.spring(duration: 0.3)) { selection = option }
                    } label: {
                        Text(title(option))
                            .font(.system(.body, weight: isSelected ? .semibold : .regular))
                            .foregroundStyle(isSelected ? .white : Palette.textPrimary)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 10)
                            .background {
                                if isSelected {
                                    Capsule().fill(Palette.accentGradient)
                                } else {
                                    Capsule().fill(Palette.surfaceMuted)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
        }
    }
}
