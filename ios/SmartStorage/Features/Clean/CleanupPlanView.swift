import SwiftUI

/// 04 — Cleanup plan: how the chosen target will be reached, with protected memories excluded.
struct CleanupPlanView: View {
    @Environment(AppRouter.self) private var router
    @Environment(LibraryStore.self) private var library

    var body: some View {
        let plan = library.cleanupPlan
        ScreenScaffold {
            ScreenHeader(title: "Cleanup Plan", subtitle: "\(plan.estimatedBytes.formattedBytes) recommended")
                .padding(.bottom, 8)

            PlanSummaryCard(plan: plan)

            if !plan.items.isEmpty {
                Card(style: .info) {
                    Label(safetyExplanation(plan), systemImage: plan.lowestSafety.systemImage)
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }

            if plan.items.isEmpty {
                Card(style: .info) {
                    Text(library.isScanning
                         ? "Still analyzing your library. Suggestions appear here as soon as the scan finishes."
                         : "Nothing to clean up right now. Your library is in good shape.")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }

            ForEach(plan.items) { item in
                Button {
                    router.push(item.route)
                } label: {
                    Card(padding: 16) {
                        ListTile(systemImage: item.systemImage, tint: item.tint, title: item.title, subtitle: item.bytes.formattedBytes) {
                            StatusBadge(text: item.safety.title, systemImage: item.safety.systemImage, tint: item.safety.tint, compact: true)
                        }
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityHint("Safety: \(item.safety.title)")
                }
                .buttonStyle(.plain)
            }

            Label("Estimated review time \(plan.reviewTime)", systemImage: "clock")
                .font(Typography.metadata)
                .foregroundStyle(Palette.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.top, 4)

            if let first = plan.items.first {
                Button("Review Items") {
                    router.push(first.route)
                }
                .buttonStyle(.primary)
            }
        }
    }
}

/// Why the plan scores what it does (spec: recommendations must be explainable).
private func safetyExplanation(_ plan: CleanupPlan) -> String {
    switch plan.lowestSafety {
    case .verySafe: "Safety \(plan.safetyScore)/100 — only identical copies. Nothing is lost."
    case .safe: "Safety \(plan.safetyScore)/100 — the best shot of every group is kept. Safest items come first."
    case .reviewFirst: "Safety \(plan.safetyScore)/100 — safest items come first. Blurry photos and screenshots are worth a quick look before you delete."
    }
}

private struct PlanSummaryCard: View {
    let plan: CleanupPlan

    var body: some View {
        Card(style: .hero) {
            HStack(alignment: .top, spacing: 0) {
                metric(icon: "target", title: "Target", value: plan.targetBytes.formattedBytes)
                Divider().frame(height: 110)
                metric(icon: "sparkles", title: "Estimated cleanup", value: plan.estimatedBytes.formattedBytes)
                Divider().frame(height: 110)
                VStack(spacing: 10) {
                    circleIcon("checkmark.shield.fill", tint: .mint)
                    Text("Protected memories excluded")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    private func metric(icon: String, title: String, value: String) -> some View {
        VStack(spacing: 8) {
            circleIcon(icon, tint: .teal)
            Text(title)
                .font(Typography.metadata)
                .foregroundStyle(Palette.textSecondary)
                .multilineTextAlignment(.center)
            Text(value)
                .font(Typography.metric)
                .foregroundStyle(Palette.textPrimary)
                .minimumScaleFactor(0.7)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private func circleIcon(_ name: String, tint: Tint) -> some View {
        Image(systemName: name)
            .font(.system(size: 22, weight: .semibold))
            .foregroundStyle(tint.foreground)
            .frame(width: 52, height: 52)
            .background(tint.background, in: Circle())
    }
}

#Preview {
    NavigationStack { CleanupPlanView() }
        .previewEnvironment()
}
