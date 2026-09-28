import SwiftUI

/// AI Taste: what Best Shot has learned from the photos kept, with an off switch and a reset.
struct AITasteView: View {
    @Environment(TasteStore.self) private var taste
    @State private var confirmingReset = false

    private var progress: String {
        let n = taste.decisions
        if n == 0 { return "No choices yet. Keep a photo other than the recommended one in Best Shot and it starts learning." }
        if n >= TasteProfile.fullTrustAfter { return "Learned from \(n) choices. Your taste now fully guides Best Shot." }
        return "Learning from \(n) \(n == 1 ? "choice" : "choices"). Best Shot leans further your way after \(TasteProfile.fullTrustAfter)."
    }

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "AI Taste", subtitle: "Best Shot learns what you like to keep.")
                .padding(.bottom, 8)

            Card(padding: 16) {
                Toggle(isOn: Binding(get: { taste.isEnabled }, set: { taste.setEnabled($0) })) {
                    ListTile(systemImage: "wand.and.stars", tint: .purple, title: "Learn from my choices",
                             subtitle: "Uses the photos you keep to fine-tune recommendations.", showsChevron: false)
                }
                .tint(Palette.accent)
            }

            Card(padding: 16) {
                VStack(alignment: .leading, spacing: 14) {
                    Text("What Best Shot looks for").font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                    weightRow("Sharpness", standard: ScoreWeights.standard.sharpness, yours: taste.weights.sharpness)
                    weightRow("Faces", standard: ScoreWeights.standard.face, yours: taste.weights.face)
                    weightRow("Lighting", standard: ScoreWeights.standard.exposure, yours: taste.weights.exposure)
                    Text(taste.isEnabled ? progress : "Switched off. Best Shot uses the standard mix.")
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textSecondary)
                }
            }

            Card(style: .info) {
                Label("Only three numbers and a count are stored, on this device. Photos, and what's in them, never are. A favorite you set always wins.",
                      systemImage: "lock.shield")
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
            }

            Button("Reset What I've Taught", role: .destructive) { confirmingReset = true }
                .buttonStyle(.secondaryOutlined)
                .disabled(taste.decisions == 0)
                .confirmationDialog("Reset your taste?", isPresented: $confirmingReset, titleVisibility: .visible) {
                    Button("Reset", role: .destructive) { taste.reset() }
                } message: {
                    Text("Best Shot goes back to its standard mix.")
                }
        }
    }

    /// A bar for how much a factor counts, with the standard mix marked for comparison.
    private func weightRow(_ title: String, standard: Double, yours: Double) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title).font(Typography.body).foregroundStyle(Palette.textPrimary)
                Spacer()
                Text("\(Int((yours * 100).rounded()))%").font(Typography.metadata).foregroundStyle(Palette.textSecondary)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.icyBlue)
                    Capsule().fill(Palette.accent).frame(width: proxy.size.width * yours)
                    // Where the standard mix sits.
                    Rectangle().fill(Palette.textSecondary.opacity(0.6)).frame(width: 2, height: 14)
                        .offset(x: proxy.size.width * standard - 1)
                }
            }
            .frame(height: 10)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title): \(Int((yours * 100).rounded())) percent, standard \(Int((standard * 100).rounded())) percent")
    }
}

#Preview {
    NavigationStack { AITasteView() }.previewEnvironment()
}
