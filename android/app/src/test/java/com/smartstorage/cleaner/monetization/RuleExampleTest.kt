package com.smartstorage.cleaner.monetization

import com.smartstorage.cleaner.media.StorageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleExampleTest {
    @Test fun shippedExamplesDoNotCountTowardTheFreeRuleLimit() {
        assertTrue(StorageRule.defaults.all { it.isExample })
        // Switching an example off doesn't make it the person's own.
        assertTrue(StorageRule.defaults.first().copy(isEnabled = false).isExample)
        val allowances = Allowances(ProStatus.Free, UsageLedger.empty(0), 0)
        assertTrue(allowances.canCreateRule(StorageRule.defaults.count { !it.isExample }))
    }

    @Test fun editingAnExampleOrAddingOneMakesItTheirOwn() {
        val edited = StorageRule.defaults.first().copy(folderTemplate = "/Mine/{YEAR}/")
        assertFalse(edited.isExample)
        val added = StorageRule.defaults.first().copy(id = "new", name = "Mine")
        assertFalse(added.isExample)
        val own = listOf(edited).count { !it.isExample }
        assertEquals(1, own)
        assertFalse(Allowances(ProStatus.Free, UsageLedger.empty(0), 0).canCreateRule(own))
    }
}
