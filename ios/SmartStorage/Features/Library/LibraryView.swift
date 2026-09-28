import SwiftUI

/// Library tab root. Not in the mockups — lists the media review flows as entry points.
struct LibraryView: View {
    @Environment(AppRouter.self) private var router

    private let entries: [(Route, String, Tint, String)] = [
        (.similarPhotos, "photo.on.rectangle.angled", .coral, "Grouped look-alike shots"),
        (.screenshots, "viewfinder", .blue, "Understood by content"),
        (.videos, "video.fill", .purple, "Large files and recordings"),
        (.memories, "heart.fill", .coral, "Protected by default"),
        (.manualBackup, "icloud.and.arrow.up.fill", .teal, "Copy selected media to your cloud"),
    ]

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Library", subtitle: "Review and organize your media.")
                .padding(.bottom, 8)

            AdaptiveGrid {
                ForEach(entries, id: \.0) { route, icon, tint, subtitle in
                    Button {
                        router.push(route)
                    } label: {
                        CardRow(systemImage: icon, tint: tint, title: route.title, subtitle: subtitle)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}
