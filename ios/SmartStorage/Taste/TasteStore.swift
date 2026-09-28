import Foundation
import Observation

/// Personalized AI Taste: learns, on this device, which photos a person keeps and lets Best Shot
/// lean that way. Opt-out and resettable; stores only three weights and a count (see `TasteProfile`).
@MainActor
@Observable
final class TasteStore {
    private(set) var isEnabled: Bool
    private(set) var profile: TasteProfile

    private let defaults: UserDefaults
    private enum Key {
        static let enabled = "taste.enabled"
        static let profile = "taste.v1"
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        isEnabled = defaults.object(forKey: Key.enabled) as? Bool ?? true
        profile = defaults.data(forKey: Key.profile).flatMap { try? JSONDecoder().decode(TasteProfile.self, from: $0) } ?? TasteProfile()
    }

    /// The weights Best Shot should use right now.
    var weights: ScoreWeights { isEnabled ? profile.effective : .standard }

    /// Choices learned from so far.
    var decisions: Int { profile.decisions }

    func setEnabled(_ on: Bool) {
        isEnabled = on
        defaults.set(on, forKey: Key.enabled)
    }

    /// Records that the photo at `chosen` was kept out of a group. Does nothing while switched off.
    func learn(chosen: Int, among features: [ScoreFeatures]) {
        guard isEnabled else { return }
        profile.learn(chosen: chosen, among: features)
        save()
    }

    /// Forgets everything learned; Best Shot goes back to the standard mix.
    func reset() {
        profile = TasteProfile()
        save()
    }

    private func save() {
        if let data = try? JSONEncoder().encode(profile) { defaults.set(data, forKey: Key.profile) }
    }
}
