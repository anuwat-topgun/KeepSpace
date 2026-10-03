import SwiftUI

struct CloudOverviewView: View {
    @Environment(CloudStore.self) private var cloud
    @Environment(AppRouter.self) private var router
    @Environment(MonetizationStore.self) private var monetization

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Cloud Backup", subtitle: "Direct from this device to your account. KeepSpace never receives your files.")
                .padding(.bottom, 8)

            ForEach(cloud.connections) { connection in
                Card {
                    VStack(spacing: 16) {
                        ListTile(systemImage: connection.isConnected ? "checkmark.icloud.fill" : "icloud",
                                 tint: connection.isConnected ? .teal : .blue,
                                 title: connection.provider.title,
                                 subtitle: connection.isConnected ? (connection.accountName ?? "Connected") : (connection.isConfigured ? "Not connected" : "OAuth setup required"),
                                 showsChevron: false)
                        if connection.isConnected {
                            Button("Disconnect") { cloud.disconnect(connection.provider) }
                                .buttonStyle(.secondaryOutlined)
                        } else {
                            // One cloud account is free; connecting another opens the paywall. Connected accounts are never disconnected.
                            let canConnect = monetization.allowances.canConnectCloudAccount(existing: cloud.connections.filter(\.isConnected).count)
                            Button {
                                if canConnect { cloud.connect(connection.provider) } else { router.presentPaywall(focus: .multipleCloudAccounts) }
                            } label: {
                                HStack(spacing: 8) {
                                    Text(connection.isConfigured ? localizedFormat("Connect %@", connection.provider.title) : "Setup required".localizedUI)
                                    if !canConnect { ProChip() }
                                }
                            }
                            .buttonStyle(PrimaryButtonStyle(showsArrow: false))
                            .disabled(!connection.isConfigured)
                        }
                    }
                }
            }

            if let notice = cloud.notice {
                Label(notice, systemImage: "info.circle")
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
            }

            if !cloud.uploads.isEmpty {
                SectionLabel("Backup Activity")
                ForEach(cloud.uploads.suffix(6).reversed()) { upload in
                    Card {
                        VStack(alignment: .leading, spacing: 12) {
                            ListTile(systemImage: icon(upload.status), tint: upload.status == .failed ? .coral : .teal,
                                     title: upload.sourceName,
                                     subtitle: "\(upload.provider.title) · \(upload.status.title.localizedUI) · \(upload.bytes.formattedBytes)",
                                     showsChevron: false)
                            if upload.status == .uploading || upload.status == .verifying {
                                ProgressView(value: Double(upload.progress), total: 100).tint(Palette.accent)
                            }
                            if let error = upload.error { Text(error).font(Typography.metadata).foregroundStyle(Palette.textSecondary) }
                        }
                    }
                }
                if cloud.uploads.contains(where: { $0.status == .failed }) {
                    Button("Retry failed uploads") { cloud.retryFailed() }.buttonStyle(.secondary)
                }
                if cloud.activeCount > 0 {
                    Button("Cancel pending backup") { cloud.cancelPending() }.buttonStyle(.secondaryOutlined)
                }
            }

            Button(cloud.uploads.contains(where: { $0.status == .backedUp }) ? "Back Up More" : "Back Up Now") {
                router.push(.manualBackup)
            }
            .buttonStyle(.primary)
        }
        .navigationTitle("Cloud")
    }

    private func icon(_ status: CloudUploadStatus) -> String {
        switch status {
        case .backedUp: "checkmark.icloud.fill"
        case .failed: "exclamationmark.triangle.fill"
        case .cancelled: "xmark.circle.fill"
        default: "clock.arrow.circlepath"
        }
    }
}

#Preview { NavigationStack { CloudOverviewView() }.previewEnvironment() }
