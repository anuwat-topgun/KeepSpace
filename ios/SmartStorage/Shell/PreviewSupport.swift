import SwiftUI

@MainActor private let previewMonetization = MonetizationStore(defaults: UserDefaults(suiteName: "preview.monetization") ?? .standard)
@MainActor private let previewLanguage = AppLanguageStore(defaults: UserDefaults(suiteName: "preview.language") ?? .standard)

extension View {
    /// Router + demo library, for SwiftUI previews of screens that read the environment.
    func previewEnvironment() -> some View {
        environment(AppRouter())
            .environment(LibraryStore(demo: true))
            .environment(TasteStore(defaults: UserDefaults(suiteName: "preview") ?? .standard))
            .environment(RuleStore(defaults: UserDefaults(suiteName: "preview") ?? .standard))
            .environment(WeeklyCleanReminder(defaults: UserDefaults(suiteName: "preview") ?? .standard))
            .environment(CloudStore(defaults: UserDefaults(suiteName: "preview.cloud") ?? .standard))
            .environment(previewMonetization)
            .environment(ProStoreService(monetization: previewMonetization))
            .environment(previewLanguage)
    }
}
