package com.smartstorage.cleaner.cloud

import java.util.UUID

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
    val status: CloudUploadStatus = CloudUploadStatus.Waiting,
    val progress: Int = 0,
    val attempt: Int = 0,
    val error: String? = null,
    val remoteId: String? = null,
)

data class CloudState(
    val connections: List<CloudConnection>,
    val preferences: UploadPreferences,
    val uploads: List<CloudUploadItem>,
    val notice: String? = null,
) {
    val activeCount: Int get() = uploads.count { it.status in setOf(CloudUploadStatus.Waiting, CloudUploadStatus.Uploading, CloudUploadStatus.Verifying) }
}
