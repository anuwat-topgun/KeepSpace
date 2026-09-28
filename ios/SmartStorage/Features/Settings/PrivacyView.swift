import SwiftUI

/// Privacy & Security: what stays on the device, and a way to forget what was learned about the library.
struct PrivacyView: View {
    @Environment(LibraryStore.self) private var library
    @State private var confirmingClear = false
    @State private var cleared = false

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Privacy & Security", subtitle: "AI stays here. Backups go only where you choose.")
                .padding(.bottom, 8)

            fact("cpu", .blue, "AI runs on this device",
                 "Similar photos, best shots, text in screenshots and receipts are all found with models built into iOS. Nothing is sent to a server.")
            fact("text.viewfinder", .purple, "Text is never stored",
                 "Screenshots and receipts are read only to sort them. KeepSpace keeps the category, a ticket's date, and a receipt's merchant, date and amount — not the text.")
            fact("location.fill", .mint, "Locations stay here too",
                 "Photo locations are used on this device to tell trips from home. They are never looked up online or shared.")
            fact("icloud.and.arrow.up", .teal, "Direct cloud backup",
                 "When you start a backup, selected files go over encrypted HTTPS directly to your Google Drive or OneDrive. KeepSpace has no media server and never receives them.")
            fact("trash.slash", .coral, "You confirm every delete",
                 "iOS asks before anything is removed, and items stay in Recently Deleted for 30 days. Favorites and memories are never preselected.")

            Card(padding: 16) {
                VStack(alignment: .leading, spacing: 12) {
                    ListTile(systemImage: "arrow.counterclockwise", tint: .amber, title: "Clear analysis cache",
                             subtitle: "Forget what KeepSpace learned about your library. It will look again on the next scan.", showsChevron: false)
                    Button("Clear Cache", role: .destructive) { confirmingClear = true }
                        .buttonStyle(.secondaryOutlined)
                        .disabled(library.isDemo)
                }
            }
            .confirmationDialog("Clear the analysis cache?", isPresented: $confirmingClear, titleVisibility: .visible) {
                Button("Clear Cache", role: .destructive) {
                    Task { await library.clearAnalysisCache(); cleared.toggle() }
                }
            } message: {
                Text("Your photos aren't touched. The next scan takes longer while KeepSpace re-analyzes them.")
            }
            .sensoryFeedback(.success, trigger: cleared)
        }
    }

    private func fact(_ icon: String, _ tint: Tint, _ title: String, _ detail: String) -> some View {
        Card(padding: 16) {
            HStack(alignment: .top, spacing: 14) {
                IconTile(systemName: icon, tint: tint, size: 44)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                    Text(detail).font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    NavigationStack { PrivacyView() }.previewEnvironment()
}
