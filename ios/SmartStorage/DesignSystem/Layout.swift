import SwiftUI

/// Standard scrolling screen: warm background, size-class gutters and a capped content width
/// so cards never stretch edge-to-edge on iPad.
struct ScreenScaffold<Content: View>: View {
    var maxWidth: CGFloat = Metrics.readableWidth
    @ViewBuilder var content: Content

    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Metrics.stackSpacing) {
                content
            }
            .frame(maxWidth: maxWidth, alignment: .leading)
            .padding(.horizontal, Metrics.gutter(sizeClass))
            .padding(.bottom, 32)
            .frame(maxWidth: .infinity)
        }
        .scrollContentBackground(.hidden)
        .background(Palette.background.ignoresSafeArea())
    }
}

/// Grid that shows one column on phones; on tablets it adds columns only when each can stay
/// at least `minColumnWidth` wide (e.g. 1 column in iPad portrait next to the sidebar, 2 in landscape).
struct AdaptiveGrid<Content: View>: View {
    var spacing: CGFloat = Metrics.stackSpacing
    var minColumnWidth: CGFloat = 320
    @ViewBuilder var content: Content

    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        let column = sizeClass == .regular
            ? GridItem(.adaptive(minimum: minColumnWidth), spacing: spacing, alignment: .top)
            : GridItem(.flexible(), spacing: spacing, alignment: .top)
        LazyVGrid(columns: [column], spacing: spacing) {
            content
        }
    }
}
