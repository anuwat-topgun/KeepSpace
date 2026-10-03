import SwiftUI

extension View {
    /// Router + demo library, for SwiftUI previews of screens that read the environment.
    func previewEnvironment() -> some View {
        environment(AppRouter())
            .environment(LibraryStore(demo: true))
            .environment(TasteStore(defaults: UserDefaults(suiteName: "preview") ?? .standard))
            .environment(RuleStore(defaults: UserDefaults(suiteName: "preview") ?? .standard))
            .environment(WeeklyCleanReminder(defaults: UserDefaults(suiteName: "preview") ?? .standard))
            .environment(CloudStore(defaults: UserDefaults(suiteName: "preview.cloud") ?? .standard))
            .environment(MonetizationStore(defaults: UserDefaults(suiteName: "preview.monetization") ?? .standard))
    }
}
