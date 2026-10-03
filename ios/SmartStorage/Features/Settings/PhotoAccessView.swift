import Photos
import PhotosUI
import SwiftUI

/// Photo Access: what KeepSpace can see, and how to change it.
struct PhotoAccessView: View {
    @Environment(LibraryStore.self) private var library
    @Environment(\.openURL) private var openURL

    private var title: String {
        switch library.access {
        case .authorized: "Full access"
        case .limited: "Limited access"
        case .denied: "Access is off"
        case .notDetermined: "Not set yet"
        }
    }

    private var detail: String {
        switch library.access {
        case .authorized: "KeepSpace can analyze your whole library on this device to find space you can recover."
        case .limited: "KeepSpace only sees the photos you selected, so suggestions cover just those. Choose more for a fuller picture."
        case .denied: "Without access KeepSpace can't find anything to clean up. Turn it on in Settings."
        case .notDetermined: "KeepSpace asks once, then works entirely on this device."
        }
    }

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Photo Access", subtitle: "Your photos are analyzed on this device only.")
                .padding(.bottom, 8)

            Card(style: .hero) {
                VStack(alignment: .leading, spacing: 10) {
                    StatusBadge(text: title, systemImage: library.access.canRead ? "checkmark.circle.fill" : "exclamationmark.circle.fill",
                                tint: library.access.canRead ? .mint : .amber)
                    Text(LocalizedStringKey(detail)).font(Typography.body).foregroundStyle(Palette.textSecondary)
                }
            }

            if library.access == .notDetermined {
                Button("Allow Photo Access") { Task { await library.requestAccessAndScan() } }
                    .buttonStyle(.primary)
            }
            if library.access == .limited {
                Button("Select More Photos") { presentLimitedPicker() }
                    .buttonStyle(.primary)
            }
            if library.access == .limited {
                Button("Open iOS Settings") { openSettings() }
                    .buttonStyle(.secondaryOutlined)
            } else if library.access != .notDetermined {
                Button("Open iOS Settings") { openSettings() }
                    .buttonStyle(.primary)
            }

            Card(style: .info) {
                Label("Deleting always asks you first, and items stay in Recently Deleted for 30 days.", systemImage: "lock.shield")
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .onAppear { library.refreshAccess() }
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
    }

    private func presentLimitedPicker() {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
              let controller = scene.keyWindow?.rootViewController else { return }
        PHPhotoLibrary.shared().presentLimitedLibraryPicker(from: controller) { _ in
            Task { @MainActor in library.refreshAccess() }
        }
    }
}

#Preview {
    NavigationStack { PhotoAccessView() }.previewEnvironment()
}
