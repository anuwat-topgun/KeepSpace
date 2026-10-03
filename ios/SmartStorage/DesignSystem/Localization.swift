import Foundation

extension String {
    /// Resolve model-driven English UI copy through the same source-keyed Localizable.strings
    /// catalogue used by SwiftUI literals. User content and technical values simply fall back.
    var localizedUI: String { NSLocalizedString(self, bundle: .main, comment: "") }
}

/// Localized format string with runtime values (prices, counts). The English source is the catalogue key, so every
/// such string is listed explicitly in i18n/extract.py — the extractor skips interpolated Swift on purpose.
func localizedFormat(_ source: String, _ arguments: CVarArg...) -> String {
    String(format: source.localizedUI, arguments: arguments)
}

/// Picks the singular or plural format for English (`one` when the count is 1). Other languages use one neutral wording for both.
func localizedCount(_ count: Int, one: String, other: String) -> String {
    localizedFormat(count == 1 ? one : other, count)
}
