package com.smartstorage.cleaner.cloud

import java.util.UUID
import com.smartstorage.cleaner.media.MediaItem
import kotlin.math.abs

enum class CloudProvider(val title: String) { GoogleDrive("Google Drive"), OneDrive("OneDrive") }
enum class CloudBackupScope(val title: String) { Photos("Photos"), Screenshots("Screenshots"), Receipts("Receipts") }
enum class CloudUploadStatus { Waiting, Uploading, Verifying, BackedUp, Failed, Cancelled }

data class CloudConnection(
    val provider: CloudProvider,
    val connected: Boolean = false,
    val accountName: String? = null,
    val configured: Boolean = false,
)

data class UploadPreferences(
    val renameAutomatically: Boolean = true,
    val wifiOnly: Boolean = true,
    val keepLocalCopies: Boolean = true,
)

data class CloudUploadItem(
    val id: String = UUID.randomUUID().toString(),
    val provider: CloudProvider,
    val scope: CloudBackupScope,
    val sourceUri: String,
    val sourceName: String,
    val destinationFolder: String,
    val bytes: Long,
    /** Source version at enqueue time, so edits are not shown as already backed up. */
    val sourceModifiedAt: Long? = null,
    val status: CloudUploadStatus = CloudUploadStatus.Waiting,
    val progress: Int = 0,
    val attempt: Int = 0,
    val error: String? = null,
    val remoteId: String? = null,
)

/** One concise state for a tile in the Photos-style library. */
data class AssetCloudState(
    val status: CloudUploadStatus? = null,
    val provider: CloudProvider? = null,
    val progress: Int = 0,
) {
    val isBackedUp: Boolean get() = status == CloudUploadStatus.BackedUp
    val title: String get() = when (status) {
        CloudUploadStatus.Waiting -> "Waiting"
        CloudUploadStatus.Uploading -> "Uploading"
        CloudUploadStatus.Verifying -> "Verifying"
        CloudUploadStatus.BackedUp -> "Backed up"
        CloudUploadStatus.Failed -> "Failed"
        CloudUploadStatus.Cancelled, null -> "Not backed up"
    }

    companion object {
        fun resolve(item: MediaItem, uploads: List<CloudUploadItem>): AssetCloudState {
            val latest = uploads.lastOrNull { upload ->
                upload.sourceUri == item.id &&
                    (upload.sourceModifiedAt == null || abs(upload.sourceModifiedAt - item.modifiedAt) < 1_000)
            } ?: return AssetCloudState()
            return AssetCloudState(latest.status, latest.provider, latest.progress)
        }
    }
}

data class CloudState(
    val connections: List<CloudConnection>,
    val preferences: UploadPreferences,
    val uploads: List<CloudUploadItem>,
    val notice: String? = null,
) {
    val activeCount: Int get() = uploads.count { it.status in setOf(CloudUploadStatus.Waiting, CloudUploadStatus.Uploading, CloudUploadStatus.Verifying) }
}
