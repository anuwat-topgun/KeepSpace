import SwiftUI

/// Receipts found in screenshots and photos of paper receipts, with what was read from each and where it would be filed.
struct ReceiptsView: View {
    @Environment(LibraryStore.self) private var library
    @Environment(RuleStore.self) private var rules
    @Environment(AppRouter.self) private var router

    var body: some View {
        let receipts = library.content.receipts
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Receipt Filing", subtitle: "Details read on this device, ready to file by your rules.")
                .padding(.bottom, 8)

            if receipts.isEmpty {
                Card(style: .info) {
                    Label(library.isScanning ? "Reading your screenshots and receipt photos…" : "No receipts found in your screenshots or photos yet.",
                          systemImage: "doc.text.magnifyingglass")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }

            AdaptiveGrid(minColumnWidth: 400) {
                ForEach(receipts) { receipt in
                    Button { router.push(.receiptFiling(id: receipt.id)) } label: { ReceiptRow(receipt: receipt) }
                        .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct ReceiptRow: View {
    let receipt: ReceiptEntry
    @Environment(RuleStore.self) private var rules

    var body: some View {
        Card(padding: 12) {
            HStack(spacing: 14) {
                ReceiptThumbnail(receipt: receipt)
                    .frame(width: 64, height: 84)
                VStack(alignment: .leading, spacing: 3) {
                    Text(receipt.details.merchant ?? "Unknown merchant")
                        .font(Typography.cardHeadline)
                        .foregroundStyle(Palette.textPrimary)
                        .lineLimit(1)
                    Text([receipt.amountText, (receipt.details.date ?? receipt.capturedAt).formatted(date: .abbreviated, time: .omitted)]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                    Label(receipt.source == .photo ? "Paper receipt photo" : "Screenshot",
                          systemImage: receipt.source == .photo ? "camera" : "viewfinder")
                        .font(.caption)
                        .foregroundStyle(Palette.textSecondary)
                    if let plan = receipt.filingPlan(rules: rules.rules) {
                        Label(plan.folder, systemImage: "folder")
                            .font(.caption)
                            .foregroundStyle(Palette.textSecondary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(.body, weight: .semibold))
                    .foregroundStyle(Palette.textSecondary)
            }
        }
    }
}

private struct ReceiptThumbnail: View {
    let receipt: ReceiptEntry

    var body: some View {
        if receipt.isDemo {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Palette.surfaceMuted)
                .overlay(Image(systemName: "doc.text").font(.title2).foregroundStyle(Palette.textSecondary))
        } else {
            AssetImage(assetID: receipt.id, fallback: .screen, cornerRadius: 10, cropAlignment: .top)
        }
    }
}

/// 16 — Receipt Filing: what was extracted, which rule matched, and where the file will go.
/// Uploading needs a connected cloud account (v1.1); until then the screen says so plainly.
struct ReceiptFilingView: View {
    let receiptID: String

    @Environment(LibraryStore.self) private var library
    @Environment(RuleStore.self) private var rules
    @Environment(AppRouter.self) private var router

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Receipt Filing", subtitle: "AI extracted details and matched a storage rule.")
                .padding(.bottom, 8)

            if let receipt = library.content.receipts.first(where: { $0.id == receiptID }) {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .top, spacing: Metrics.stackSpacing) {
                        extractedCard(receipt).frame(minWidth: 420)
                        ruleCard(receipt).frame(width: 380)
                    }
                    VStack(spacing: Metrics.stackSpacing) {
                        extractedCard(receipt)
                        ruleCard(receipt)
                    }
                }
                statusCard(receipt)
                actions
            } else {
                Card(style: .info) {
                    Label("This receipt is no longer in your library.", systemImage: "doc.questionmark")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }
    }

    private func extractedCard(_ receipt: ReceiptEntry) -> some View {
        Card(padding: 14) {
            HStack(alignment: .top, spacing: 16) {
                ReceiptThumbnail(receipt: receipt)
                    .frame(width: 124, height: 170)
                VStack(alignment: .leading, spacing: 12) {
                    field("Merchant", receipt.details.merchant, icon: "storefront.fill", tint: .blue)
                    // No printed date: filing falls back to when the screenshot or photo was taken, so say that.
                    field("Date", receipt.details.date?.formatted(date: .abbreviated, time: .omitted)
                          ?? receipt.capturedAt.formatted(date: .abbreviated, time: .omitted) + " (photo date)",
                          icon: "calendar", tint: .blue)
                    field("Amount", receipt.amountText, icon: "banknote.fill", tint: .purple)
                    field("Category", receipt.details.category.title, icon: "bag.fill", tint: .coral)
                }
            }
        }
    }

    private func field(_ label: String, _ value: String?, icon: String, tint: Tint) -> some View {
        HStack(spacing: 12) {
            IconTile(systemName: icon, tint: tint, size: 40)
            VStack(alignment: .leading, spacing: 1) {
                Text(label).font(.caption).foregroundStyle(Palette.textSecondary)
                Text(value ?? "Not found")
                    .font(.system(.body, weight: .semibold))
                    .foregroundStyle(value == nil ? Palette.textSecondary : Palette.textPrimary)
                    .lineLimit(2)
            }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func ruleCard(_ receipt: ReceiptEntry) -> some View {
        Card(style: .info) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    IconTile(systemName: "sparkles", tint: .teal)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Matched Rule").font(.system(.title3, weight: .bold)).foregroundStyle(Palette.textPrimary)
                        Text("This receipt will be saved to:").font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                    }
                }
                if let plan = receipt.filingPlan(rules: rules.rules) {
                    ruleRow("Destination", plan.rule.provider.title, icon: "externaldrive.fill.badge.icloud")
                    Divider()
                    ruleRow("Target Folder", plan.folder, icon: "folder.fill")
                    Divider()
                    ruleRow("File Name", plan.fileName, icon: "doc.fill")
                } else {
                    Text("No enabled rule files receipts yet.").font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                }
            }
        }
    }

    private func ruleRow(_ label: String, _ value: String, icon: String) -> some View {
        HStack(spacing: 14) {
            IconTile(systemName: icon, tint: .gray, size: 40)
            VStack(alignment: .leading, spacing: 1) {
                Text(label).font(.caption).foregroundStyle(Palette.textSecondary)
                // Paths can be long (spec §7.2): wrap rather than truncate.
                Text(value).font(.system(.body, weight: .medium)).foregroundStyle(Palette.textPrimary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func statusCard(_ receipt: ReceiptEntry) -> some View {
        if receipt.details.completeness < 3 {
            notice(icon: "exclamationmark.circle.fill", tint: .amber, title: "Check the details",
                   detail: "Some details couldn't be read. Missing values use placeholders in the file name.")
        }
        notice(icon: "icloud.slash", tint: .blue, title: "Connect a cloud account to upload",
               detail: "The file is prepared and matched with the rule. Uploads go straight from this device to your \(receipt.filingPlan(rules: rules.rules)?.rule.provider.title ?? "cloud").")
    }

    private func notice(icon: String, tint: Tint, title: String, detail: String) -> some View {
        Card(style: .info) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: icon).font(.title2).foregroundStyle(tint.foreground)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                    Text(detail).font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                }
            }
        }
    }

    private var actions: some View {
        VStack(spacing: 12) {
            Button("Connect Google Drive") { router.push(.cloudOverview) }
                .buttonStyle(.primary)
            Button("Review Rule") { router.push(.storageRules) }
                .buttonStyle(.secondary)
            Label("Details were read on this device and stay here.", systemImage: "lock.shield")
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
        }
        .padding(.top, 4)
    }
}

#Preview {
    NavigationStack { ReceiptFilingView(receiptID: "demo-central") }
        .previewEnvironment()
}
