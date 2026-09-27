import SwiftUI

extension View {
    /// Router + demo library, for SwiftUI previews of screens that read the environment.
    func previewEnvironment() -> some View {
        environment(AppRouter())
            .environment(LibraryStore(demo: true))
            .environment(RuleStore(defaults: UserDefaults(suiteName: "preview") ?? .standard))
    }
}
