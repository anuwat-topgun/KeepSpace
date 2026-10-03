import SwiftUI

struct ManualBackupView: View {
    @Environment(CloudStore.self) private var cloud
    @Environment(LibraryStore.self) private var library
    @Environment(MonetizationStore.self) private var monetization
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @State private var provider: CloudProvider = .googleDrive
    @State private var scope: CloudBackupScope = .photos
    @State private var folder = "Photos"

    private var candidates: [MediaItem] { library.backupCandidates(for: scope) }
    private var connectedProviders: [CloudProvider] { cloud.connections.filter(\.isConnected).map(\.provider) }

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Back Up Now", subtitle: "Choose what to copy and where it should go.")
                .padding(.bottom, 8)

            SectionLabel("Content")
            Picker("Content", selection: $scope) {
                ForEach(CloudBackupScope.allCases, id: \.self) { Text($0.title.localizedUI).tag($0) }
            }
            .pickerStyle(.segmented)
            .onChange(of: scope) { _, value in folder = value.title }
            Text("\(candidates.count) items · \(candidates.reduce(Int64(0)) { $0 + $1.bytes }.formattedBytes)")
                .font(Typography.metadata).foregroundStyle(Palette.textSecondary)

            SectionLabel("Destination")
            Picker("Cloud provider", selection: $provider) {
                ForEach(CloudProvider.allCases, id: \.self) { value in
                    Text(value.title.localizedUI).tag(value).disabled(!connectedProviders.contains(value))
                }
            }
            .pickerStyle(.segmented)
            TextField("Folder inside KeepSpace", text: $folder)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.words)

            Card {
                VStack(spacing: 18) {
                    ToggleRow(systemImage: "character.cursor.ibeam", title: "Rename automatically",
                              subtitle: "Uses date and a stable item suffix", isOn: preferenceBinding(\.renameAutomatically))
                    Divider()
                    ToggleRow(systemImage: "wifi", tint: .blue, title: "Wi-Fi only", isOn: preferenceBinding(\.wifiOnly))
                    Divider()
                    ListTile(systemImage: "iphone", tint: .coral, title: "Keep local copies",
                             subtitle: "Always on in v1.1. Cloud Backup never deletes originals.", showsChevron: false)
                }
            }

            if connectedProviders.isEmpty {
                CardRow(systemImage: "icloud.slash", tint: .blue, title: "Connect a cloud account first", subtitle: "Open Cloud in Settings")
            }
            // Free backs up about 100 files a month. Over that, the first files that fit go; the rest wait for next month or Pro.
            let allowed = monetization.allowances.backupAllowedCount(requested: candidates.count)
            let exhausted = !candidates.isEmpty && allowed == 0
            if allowed < candidates.count {
                Card(style: .info) {
                    Text(exhausted ? "You've used this month's free backups."
                         : localizedFormat("Free backs up %d more files this month. The first %d will go; Pro has no limit.", allowed, allowed))
                        .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                }
            }
            Button {
                if exhausted {
                    router.presentPaywall(focus: .unlimitedBackup)
                } else {
                    cloud.enqueue(provider: provider, scope: scope, folder: folder, items: Array(candidates.prefix(allowed)))
                    monetization.recordBackups(files: allowed)
                    dismiss()
                }
            } label: {
                if exhausted { Text("Unlock Unlimited Backup") }
                else if allowed < candidates.count { Text(localizedFormat("Back Up %d Files", allowed)) }
                else { Text("Start Backup") }
            }
            .buttonStyle(.primary)
            .disabled(!exhausted && (!connectedProviders.contains(provider) || candidates.isEmpty || folder.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty))
        }
        .navigationTitle("Back Up Now")
        .onAppear { if let first = connectedProviders.first { provider = first } }
    }

    private func preferenceBinding(_ keyPath: WritableKeyPath<UploadPreferences, Bool>) -> Binding<Bool> {
        Binding(get: { cloud.preferences[keyPath: keyPath] }, set: { value in cloud.preferences[keyPath: keyPath] = value })
    }
}

#Preview { NavigationStack { ManualBackupView() }.previewEnvironment() }
