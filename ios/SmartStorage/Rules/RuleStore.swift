import Foundation
import Observation

/// The user's storage rules, saved on the device. Starts from the spec's example rules.
@MainActor
@Observable
final class RuleStore {
    private(set) var rules: [StorageRule]
    private let defaults: UserDefaults
    private static let key = "storageRules.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        rules = Self.load(from: defaults) ?? StorageRule.defaults
    }

    var activeCount: Int { rules.filter(\.isEnabled).count }

    /// The first enabled rule for a trigger wins, in list order (spec §7.9).
    func rule(for trigger: RuleTrigger) -> StorageRule? {
        rules.first { $0.isEnabled && $0.trigger == trigger }
    }

    func save(_ rule: StorageRule) {
        if let index = rules.firstIndex(where: { $0.id == rule.id }) {
            rules[index] = rule
        } else {
            rules.append(rule)
        }
        persist()
    }

    func setEnabled(_ enabled: Bool, for id: StorageRule.ID) {
        guard let index = rules.firstIndex(where: { $0.id == id }) else { return }
        rules[index].isEnabled = enabled
        persist()
    }

    func delete(_ id: StorageRule.ID) {
        rules.removeAll { $0.id == id }
        persist()
    }

    func move(from source: IndexSet, to destination: Int) {
        rules.move(fromOffsets: source, toOffset: destination)
        persist()
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(rules) { defaults.set(data, forKey: Self.key) }
    }

    static func load(from defaults: UserDefaults) -> [StorageRule]? {
        defaults.data(forKey: key).flatMap { try? JSONDecoder().decode([StorageRule].self, from: $0) }
    }
}
