import SwiftUI

/// 03 — Clean target selection.
struct CleanView: View {
    @Environment(AppRouter.self) private var router
    @Environment(LibraryStore.self) private var library
    @State private var target: CleanupTarget = .tenGB

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Clean", subtitle: "How much space do you need?")
                .padding(.bottom, 8)

            ForEach(CleanupTarget.allCases) { option in
                TargetOptionCard(option: option, isSelected: option == target)
                    .onTapGesture {
                        withAnimation(.spring(duration: 0.3)) { target = option }
                        library.cleanupTarget = option.bytes
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(option == target ? [.isButton, .isSelected] : .isButton)
            }

            Card(style: .info) {
                HStack(spacing: 16) {
                    IconTile(systemName: "clock", tint: .blue)
                    VStack(alignment: .leading, spacing: 4) {
                        SectionLabel("Estimated review time")
                        Text(library.cleanupPlan.reviewTime)
                            .font(Typography.metric)
                            .foregroundStyle(Palette.textPrimary)
                    }
                }
            }

            Button("Build Cleanup Plan") {
                library.cleanupTarget = target.bytes
                router.push(.cleanupPlan)
            }
            .buttonStyle(.primary)
            .padding(.top, 8)
        }
    }
}

enum CleanupTarget: CaseIterable, Identifiable {
    case fiveGB, tenGB, twentyGB, maximumSafe

    var id: Self { self }

    /// nil = everything that is safe to suggest.
    var bytes: Int64? {
        switch self {
        case .fiveGB: 5_000_000_000
        case .tenGB: 10_000_000_000
        case .twentyGB: 20_000_000_000
        case .maximumSafe: nil
        }
    }

    var title: String {
        switch self {
        case .fiveGB: "5 GB"
        case .tenGB: "10 GB"
        case .twentyGB: "20 GB"
        case .maximumSafe: "Maximum Safe Cleanup"
        }
    }

    var subtitle: String {
        switch self {
        case .fiveGB: "Quick cleanup of common files."
        case .tenGB: "Good balance for everyday use."
        case .twentyGB: "Deeper cleanup for more space."
        case .maximumSafe: "Frees up as much space as possible without deleting important data."
        }
    }

    var systemImage: String {
        switch self {
        case .fiveGB, .tenGB: "sparkles"
        case .twentyGB: "cylinder.split.1x2.fill"
        case .maximumSafe: "checkmark.shield.fill"
        }
    }

    var tint: Tint {
        switch self {
        case .fiveGB, .tenGB: .teal
        case .twentyGB: .purple
        case .maximumSafe: .mint
        }
    }
}

private struct TargetOptionCard: View {
    let option: CleanupTarget
    let isSelected: Bool

    var body: some View {
        Card(style: isSelected ? .selected : .plain) {
            HStack(spacing: 16) {
                IconTile(systemName: option.systemImage, tint: option.tint)
                VStack(alignment: .leading, spacing: 4) {
                    Text(option.title)
                        .font(.system(.title2, weight: .bold))
                        .foregroundStyle(Palette.textPrimary)
                    Text(option.subtitle)
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
                Spacer(minLength: 8)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 28))
                    .foregroundStyle(isSelected ? Palette.accent : Palette.textSecondary.opacity(0.6))
            }
        }
        .contentShape(Rectangle())
    }
}

#Preview {
    TabStack(tab: .clean)
        .previewEnvironment()
}
