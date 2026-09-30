package com.smartstorage.cleaner.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.smartstorage.cleaner.media.MediaItem

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

    @Test fun libraryCloudStateUsesLatestJobForCurrentAssetVersion() {
        val modified = 1_700_000_000_000L
        val media = MediaItem("content://media/1", MediaItem.Kind.Photo, modified, 42, 100, 200, 0, false, modified)
        val old = CloudUploadItem(provider = CloudProvider.GoogleDrive, scope = CloudBackupScope.Photos,
            sourceUri = media.id, sourceName = "photo.jpg", destinationFolder = "Photos", bytes = 42,
            sourceModifiedAt = modified - 60_000, status = CloudUploadStatus.BackedUp)
        val current = CloudUploadItem(provider = CloudProvider.OneDrive, scope = CloudBackupScope.Photos,
            sourceUri = media.id, sourceName = "photo.jpg", destinationFolder = "Photos", bytes = 42,
            sourceModifiedAt = modified, status = CloudUploadStatus.Verifying, progress = 92)

        val state = AssetCloudState.resolve(media, listOf(old, current))
        assertEquals(CloudUploadStatus.Verifying, state.status)
        assertEquals(CloudProvider.OneDrive, state.provider)
        assertEquals(92, state.progress)
    }
}
