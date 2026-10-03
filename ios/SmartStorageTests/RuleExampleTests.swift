import Foundation
import Testing
@testable import SmartStorage

struct RuleExampleTests {
    @Test func shippedExamplesDoNotCountTowardTheFreeRuleLimit() {
        #expect(StorageRule.defaults.allSatisfy { $0.isExample })
        var off = StorageRule.defaults[0]
        off.isEnabled = false
        #expect(off.isExample) // switching an example off doesn't make it the person's own
    }

    @Test func editingAnExampleOrAddingOneMakesItTheirOwn() {
        var edited = StorageRule.defaults[0]
        edited.folderTemplate = "/Mine/{YEAR}/"
        #expect(!edited.isExample)
        var added = StorageRule.defaults[0]
        added.id = UUID()
        added.name = "Mine"
        #expect(!added.isExample)
    }
}
