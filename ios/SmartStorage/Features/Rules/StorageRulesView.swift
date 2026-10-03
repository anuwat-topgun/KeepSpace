import SwiftUI

/// 14 — Storage Rules: automation that files content where it belongs.
struct StorageRulesView: View {
    @Environment(RuleStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(MonetizationStore.self) private var monetization

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Storage Rules", subtitle: "Choose where each kind of file would be filed.")
                .padding(.bottom, 8)

            AutomationHeroCard(active: store.activeCount)

            if store.rules.isEmpty {
                Card(style: .info) {
                    Label("No rules yet. Add one to start filing automatically.", systemImage: "arrow.triangle.branch")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }

            AdaptiveGrid(minColumnWidth: 400) {
                ForEach(store.rules) { rule in
                    RuleRow(rule: rule) { router.push(.editRule(id: rule.id)) }
                        .contextMenu {
                            Button(rule.isEnabled ? "Turn Off" : "Turn On") { store.setEnabled(!rule.isEnabled, for: rule.id) }
                            Button("Delete Rule", role: .destructive) { store.delete(rule.id) }
                        }
                }
            }

            // The first rule is free; a second opens the paywall. Existing rules are never removed.
            let canAdd = monetization.allowances.canCreateRule(existing: store.userRuleCount)
            Button {
                if canAdd { router.push(.newRule) } else { router.presentPaywall(focus: .unlimitedRules) }
            } label: {
                HStack(spacing: 8) {
                    Label("Add Rule", systemImage: "plus")
                    if !canAdd { ProChip() }
                }
            }
            .buttonStyle(PrimaryButtonStyle(showsArrow: false))
            .padding(.top, 4)
        }
    }
}

private struct AutomationHeroCard: View {
    let active: Int

    var body: some View {
        Card(style: .hero) {
            HStack(alignment: .center, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel("Automation")
                    Text(localizedFormat(active == 1 ? "%d rule on" : "%d rules on", active))
                        .font(Typography.metricLarge)
                        .foregroundStyle(Palette.textPrimary)
                    // Honest until v1.1 lands: rules are ready but nothing uploads yet.
                    Text("No cloud account connected yet")
                        .font(Typography.body)
                        .foregroundStyle(Palette.textSecondary)
                }
                Spacer(minLength: 0)
                ZStack {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(Palette.icyBlue)
                        .frame(width: 84, height: 84)
                        .overlay(Image(systemName: "sparkles").font(.title).foregroundStyle(Palette.accent))
                    ProviderGlyph(provider: .googleDrive).offset(x: 46, y: -36)
                    ProviderGlyph(provider: .oneDrive).offset(x: 46, y: 36)
                }
                .padding(.trailing, 30)
                .accessibilityHidden(true)
            }
        }
    }
}

/// Neutral provider mark (spec: "provider identity visible but not brand-heavy").
struct ProviderGlyph: View {
    let provider: CloudProvider

    var body: some View {
        Image(systemName: provider == .googleDrive ? "triangle.fill" : "cloud.fill")
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(provider == .googleDrive ? Tint.mint.foreground : Tint.blue.foreground)
            .frame(width: 36, height: 36)
            .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
    }
}

struct ProviderBadge: View {
    let provider: CloudProvider

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: provider == .googleDrive ? "triangle.fill" : "cloud.fill")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(provider == .googleDrive ? Tint.mint.foreground : Tint.blue.foreground)
            Text(provider.title)
        }
        .font(.system(.subheadline, weight: .medium))
        .foregroundStyle(Palette.accent)
        .padding(.horizontal, 12)
        .padding(.vertical, 5)
        .background(Palette.icyBlue, in: Capsule())
    }
}

private struct RuleRow: View {
    let rule: StorageRule
    let onOpen: () -> Void
    @Environment(RuleStore.self) private var store

    var body: some View {
        Card(padding: 16) {
            HStack(alignment: .top, spacing: 16) {
                IconTile(systemName: rule.trigger.systemImage, tint: rule.trigger.tint)
                    .opacity(rule.isEnabled ? 1 : 0.5)
                VStack(alignment: .leading, spacing: 8) {
                    Text(rule.name)
                        .font(Typography.cardHeadline)
                        .foregroundStyle(rule.isEnabled ? Palette.textPrimary : Palette.textSecondary)
                    ProviderBadge(provider: rule.provider)
                    // Templates wrap gracefully (spec: long/localized paths).
                    Text(rule.folderTemplate)
                        .font(.system(.subheadline, design: .monospaced))
                        .foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 12) {
                    Toggle("Enabled", isOn: Binding(get: { rule.isEnabled }, set: { store.setEnabled($0, for: rule.id) }))
                        .labelsHidden()
                        .tint(Palette.accent)
                    Image(systemName: "chevron.right")
                        .font(.system(.body, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
        .accessibilityElement(children: .contain)
        .accessibilityAction(named: "Edit rule", onOpen)
    }
}

#Preview {
    NavigationStack { StorageRulesView() }
        .previewEnvironment()
}
