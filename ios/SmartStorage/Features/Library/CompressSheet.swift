import SwiftUI

/// Compression as an alternative to deletion: pick a quality, see the estimated saving, then the
/// system asks once to save the smaller copy and move the original to Recently Deleted.
struct CompressSheet: View {
    let video: VideoItem

    @Environment(LibraryStore.self) private var library
    @Environment(\.dismiss) private var dismiss
    @State private var preset: CompressionPreset = .hd1080
    @State private var progress: Double?
    @State private var errorMessage: String?

    private var presets: [CompressionPreset] {
        CompressionPreset.allCases.filter { video.estimatedSavings($0) != nil }
    }

    var body: some View {
        NavigationStack {
            ScreenScaffold {
                HStack(spacing: 14) {
                    AssetImage(assetID: video.assetID, fallback: video.style, cornerRadius: 12)
                        .frame(width: 96, height: 64)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(video.title).font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                        Text("\(video.bytes.formattedBytes) · \(video.metadata)")
                            .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                    }
                }

                ForEach(presets) { option in
                    let selected = option == preset
                    Button { preset = option } label: {
                        Card(style: selected ? .selected : .plain, padding: 16) {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(option.title).font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                                    Text("Saves about \((video.estimatedSavings(option) ?? 0).formattedBytes)")
                                        .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                                }
                                Spacer()
                                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                                    .font(.title2)
                                    .foregroundStyle(selected ? Palette.accent : Palette.textSecondary.opacity(0.6))
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(progress != nil)
                }

                if let progress {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(progress < 1 ? "Compressing on device…" : "Waiting for your confirmation…")
                            .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                        ProgressView(value: progress).tint(Palette.accent)
                    }
                }

                if let errorMessage {
                    Card(style: .info) {
                        Label(errorMessage, systemImage: "exclamationmark.triangle")
                            .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                    }
                }

                Button("Compress") { Task { await run() } }
                    .buttonStyle(PrimaryButtonStyle(showsArrow: false))
                    .disabled(progress != nil || presets.isEmpty)

                Text("Date, location and favorite are kept. The original moves to Recently Deleted for 30 days after you confirm.")
                    .font(.caption).foregroundStyle(Palette.textSecondary)
            }
            .navigationTitle("Compress Video")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(progress != nil)
                }
            }
            .onAppear { if let first = presets.first { preset = first } }
        }
        .interactiveDismissDisabled(progress != nil)
    }

    private func run() async {
        guard let id = video.assetID else { return }
        errorMessage = nil
        progress = 0
        let message = await library.compress(videoID: id, preset: preset) { value in
            Task { @MainActor in progress = value }
        }
        progress = nil
        if let message {
            errorMessage = message
        } else {
            dismiss()
        }
    }
}
