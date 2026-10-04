import Foundation
import Observation

struct AppLanguageOption: Identifiable, Hashable, Sendable {
    let id: String
    let nativeName: String

    static let system = AppLanguageOption(id: "system", nativeName: "Use Device Language")

    static let supported: [AppLanguageOption] = [
        .system,
        .init(id: "en", nativeName: "English"),
        .init(id: "es", nativeName: "Español"),
        .init(id: "pt-BR", nativeName: "Português (Brasil)"),
        .init(id: "fr", nativeName: "Français"),
        .init(id: "de", nativeName: "Deutsch"),
        .init(id: "it", nativeName: "Italiano"),
        .init(id: "ar", nativeName: "العربية"),
        .init(id: "tr", nativeName: "Türkçe"),
        .init(id: "ru", nativeName: "Русский"),
        .init(id: "zh-Hans", nativeName: "简体中文"),
        .init(id: "ja", nativeName: "日本語"),
        .init(id: "ko", nativeName: "한국어"),
        .init(id: "th", nativeName: "ไทย"),
        .init(id: "id", nativeName: "Bahasa Indonesia"),
        .init(id: "vi", nativeName: "Tiếng Việt"),
        .init(id: "nl", nativeName: "Nederlands"),
        .init(id: "pl", nativeName: "Polski"),
        .init(id: "ro", nativeName: "Română"),
        .init(id: "el", nativeName: "Ελληνικά"),
        .init(id: "uk", nativeName: "Українська"),
        .init(id: "hi", nativeName: "हिन्दी"),
        .init(id: "ms", nativeName: "Bahasa Melayu"),
        .init(id: "zh-Hant", nativeName: "繁體中文"),
        .init(id: "sv", nativeName: "Svenska"),
        .init(id: "cs", nativeName: "Čeština"),
    ]
}

@MainActor
@Observable
final class AppLanguageStore {
    nonisolated static let defaultsKey = "app.language"
    private let defaults: UserDefaults

    var selection: String {
        didSet {
            if selection == AppLanguageOption.system.id {
                defaults.removeObject(forKey: Self.defaultsKey)
            } else {
                defaults.set(selection, forKey: Self.defaultsKey)
            }
        }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        selection = defaults.string(forKey: Self.defaultsKey) ?? AppLanguageOption.system.id
    }

    var locale: Locale {
        selection == AppLanguageOption.system.id ? .autoupdatingCurrent : Locale(identifier: selection)
    }

    var displayName: String {
        AppLanguageOption.supported.first(where: { $0.id == selection })?.nativeName
            ?? AppLanguageOption.system.nativeName
    }
}
