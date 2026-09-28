package com.smartstorage.cleaner.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupTest {
    @Test fun v11DefaultsAreConservative() {
        val preferences = UploadPreferences()
        assertTrue(preferences.keepLocalCopies)
        assertTrue(preferences.wifiOnly)
        assertTrue(preferences.renameAutomatically)
    }

    @Test fun queueCarriesProviderDestinationAndState() {
        val item = CloudUploadItem(provider = CloudProvider.OneDrive, scope = CloudBackupScope.Receipts,
            sourceUri = "content://media/1", sourceName = "receipt.jpg", destinationFolder = "Receipts/2026",
            bytes = 42, status = CloudUploadStatus.Verifying, progress = 92)
        assertEquals(CloudProvider.OneDrive, item.provider)
        assertEquals("Receipts/2026", item.destinationFolder)
        assertEquals(CloudUploadStatus.Verifying, item.status)
        assertEquals(92, item.progress)
    }
}
