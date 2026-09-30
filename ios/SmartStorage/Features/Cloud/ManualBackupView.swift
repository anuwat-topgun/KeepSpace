import SwiftUI

struct ManualBackupView: View {
    @Environment(CloudStore.self) private var cloud
    @Environment(LibraryStore.self) private var library
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
            Button("Start Backup") {
                cloud.enqueue(provider: provider, scope: scope, folder: folder, items: candidates)
                dismiss()
            }
            .buttonStyle(.primary)
            .disabled(!connectedProviders.contains(provider) || candidates.isEmpty || folder.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .navigationTitle("Back Up Now")
        .onAppear { if let first = connectedProviders.first { provider = first } }
    }

    private func preferenceBinding(_ keyPath: WritableKeyPath<UploadPreferences, Bool>) -> Binding<Bool> {
        Binding(get: { cloud.preferences[keyPath: keyPath] }, set: { value in cloud.preferences[keyPath: keyPath] = value })
    }
}

#Preview { NavigationStack { ManualBackupView() }.previewEnvironment() }
