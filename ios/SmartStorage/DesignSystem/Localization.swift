import Foundation

extension String {
    /// Resolve model-driven English UI copy through the same source-keyed Localizable.strings
    /// catalogue used by SwiftUI literals. User content and technical values simply fall back.
    var localizedUI: String { NSLocalizedString(self, bundle: .main, comment: "") }
}
