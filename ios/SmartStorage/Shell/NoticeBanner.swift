import SwiftUI

/// Calm confirmation after a delete or compress. Dismisses itself after a few seconds or on tap.
struct NoticeBanner: View {
    @Environment(LibraryStore.self) private var library

    var body: some View {
        if let notice = library.notice {
            Button { library.notice = nil } label: {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Tint.mint.foreground)
                    Text(notice)
                        .font(Typography.metadata)
                        .foregroundStyle(Palette.textPrimary)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .padding(14)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .shadow(color: .black.opacity(0.12), radius: 16, y: 6)
                .frame(maxWidth: Metrics.readableWidth)
                .padding(.horizontal, 16)
            }
            .buttonStyle(.plain)
            .transition(.move(edge: .top).combined(with: .opacity))
            .sensoryFeedback(.success, trigger: notice)
            .task(id: notice) {
                try? await Task.sleep(for: .seconds(6))
                withAnimation { library.notice = nil }
            }
            .accessibilityAddTraits(.isStaticText)
        }
    }
}
