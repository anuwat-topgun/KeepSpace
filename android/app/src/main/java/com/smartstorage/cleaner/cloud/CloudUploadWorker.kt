package com.smartstorage.cleaner.cloud

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CloudUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val cloud = CloudStore(appContext)

    override suspend fun doWork(): Result = try {
        withContext(Dispatchers.IO) {
            val pending = cloud.snapshot().uploads.filter { it.status == CloudUploadStatus.Waiting }
            for (item in pending) {
                if (isStopped) return@withContext Result.success()
                try {
                    upload(item)
                } catch (error: Exception) {
                    val nextAttempt = item.attempt + 1
                    val retrying = nextAttempt < MAX_ATTEMPTS
                    cloud.replaceUpload(item.copy(
                        status = if (retrying) CloudUploadStatus.Waiting else CloudUploadStatus.Failed,
                        attempt = nextAttempt,
                        error = error.message ?: "Upload failed",
                    ))
                    if (retrying) return@withContext Result.retry()
                }
            }
            Result.success()
        }
    } finally {
        cloud.close()
    }

    private suspend fun upload(item: CloudUploadItem) {
        cloud.replaceUpload(item.copy(status = CloudUploadStatus.Uploading, progress = 5, error = null))
        val token = freshToken(item.provider)
        val remoteId = when (item.provider) {
            CloudProvider.GoogleDrive -> uploadGoogle(item, token)
            CloudProvider.OneDrive -> uploadOneDrive(item, token)
        }
        cloud.replaceUpload(item.copy(status = CloudUploadStatus.Verifying, progress = 92, remoteId = remoteId, error = null))
        verify(item.provider, remoteId, token)
        cloud.replaceUpload(item.copy(status = CloudUploadStatus.BackedUp, progress = 100, remoteId = remoteId, error = null))
    }

    private suspend fun freshToken(provider: CloudProvider): String = suspendCancellableCoroutine { continuation ->
        if (provider == CloudProvider.GoogleDrive) {
            val token = cloud.googleAccessToken()
            if (token != null) continuation.resume(token)
            else continuation.resumeWithException(IllegalStateException("Google authorization expired. Open Cloud and reconnect, then retry."))
            return@suspendCancellableCoroutine
        }
        val auth = cloud.authState(provider)
        if (auth == null) {
            continuation.resumeWithException(IllegalStateException("${provider.title} is disconnected."))
            return@suspendCancellableCoroutine
        }
        val service = AuthorizationService(applicationContext)
        continuation.invokeOnCancellation { service.dispose() }
        auth.performActionWithFreshTokens(service) { accessToken: String?, _: String?, error: AuthorizationException? ->
            cloud.saveAuth(provider, auth)
            service.dispose()
            if (accessToken != null) continuation.resume(accessToken)
            else continuation.resumeWithException(error ?: IllegalStateException("Authorization expired. Reconnect ${provider.title}."))
        }
    }

    private fun uploadGoogle(item: CloudUploadItem, token: String): String {
        val parent = ensureGoogleFolder(item.destinationFolder, token)
        val metadata = JSONObject().put("name", item.sourceName).put("parents", org.json.JSONArray().put(parent)).toString()
        val start = request("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable", token,
            metadata.toByteArray(), "application/json; charset=UTF-8", mapOf("X-Upload-Content-Type" to contentType(item.sourceName)))
        val location = start.headers["Location"]?.firstOrNull() ?: error("Google Drive did not start an upload session.")
        cloud.replaceUpload(item.copy(status = CloudUploadStatus.Uploading, progress = 35))
        val bytes = applicationContext.contentResolver.openInputStream(Uri.parse(item.sourceUri))?.use { it.readBytes() }
            ?: error("The source file is no longer available.")
        val uploaded = request("PUT", location, token, bytes, contentType(item.sourceName))
        cloud.replaceUpload(item.copy(status = CloudUploadStatus.Uploading, progress = 88))
        return JSONObject(String(uploaded.body)).getString("id")
    }

    private fun ensureGoogleFolder(folder: String, token: String): String {
        var parent = "root"
        for (segment in (listOf("KeepSpace") + folder.split('/').filter { it.isNotBlank() })) {
            val escaped = segment.replace("'", "\\'")
            val query = URLEncoder.encode("name='$escaped' and mimeType='application/vnd.google-apps.folder' and '$parent' in parents and trashed=false", "UTF-8")
            val found = request("GET", "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)&spaces=drive", token)
            val files = JSONObject(String(found.body)).optJSONArray("files")
            parent = if (files != null && files.length() > 0) files.getJSONObject(0).getString("id") else {
                val metadata = JSONObject().put("name", segment).put("mimeType", "application/vnd.google-apps.folder")
                    .put("parents", org.json.JSONArray().put(parent)).toString().toByteArray()
                val made = request("POST", "https://www.googleapis.com/drive/v3/files?fields=id", token, metadata, "application/json")
                JSONObject(String(made.body)).getString("id")
            }
        }
        return parent
    }

    private fun uploadOneDrive(item: CloudUploadItem, token: String): String {
        ensureOneDriveFolder(item.destinationFolder, token)
        val path = (listOf(item.destinationFolder) + item.sourceName).flatMap { it.split('/') }
            .filter { it.isNotBlank() }.joinToString("/") { Uri.encode(it) }
        val bytes = applicationContext.contentResolver.openInputStream(Uri.parse(item.sourceUri))?.use { it.readBytes() }
            ?: error("The source file is no longer available.")
        cloud.replaceUpload(item.copy(status = CloudUploadStatus.Uploading, progress = 35))
        val uploaded = request("PUT", "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$path:/content?@microsoft.graph.conflictBehavior=rename", token, bytes, contentType(item.sourceName))
        cloud.replaceUpload(item.copy(status = CloudUploadStatus.Uploading, progress = 88))
        return JSONObject(String(uploaded.body)).getString("id")
    }

    private fun ensureOneDriveFolder(folder: String, token: String) {
        val segments = folder.split('/').filter { it.isNotBlank() }
        var path = ""
        for (segment in segments) {
            val childPath = (path.split('/').filter { it.isNotBlank() } + segment)
                .joinToString("/") { Uri.encode(it) }
            val childUrl = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$childPath"
            val exists = runCatching { request("GET", childUrl, token) }.isSuccess
            if (!exists) {
                val parentPath = path.split('/').filter { it.isNotBlank() }.joinToString("/") { Uri.encode(it) }
                val childrenUrl = if (parentPath.isBlank()) {
                    "https://graph.microsoft.com/v1.0/me/drive/special/approot/children"
                } else {
                    "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$parentPath:/children"
                }
                val body = JSONObject()
                    .put("name", segment)
                    .put("folder", JSONObject())
                    .put("@microsoft.graph.conflictBehavior", "fail")
                    .toString().toByteArray()
                request("POST", childrenUrl, token, body, "application/json")
            }
            path = if (path.isBlank()) segment else "$path/$segment"
        }
    }

    private fun verify(provider: CloudProvider, remoteId: String, token: String) {
        val url = when (provider) {
            CloudProvider.GoogleDrive -> "https://www.googleapis.com/drive/v3/files/${Uri.encode(remoteId)}?fields=id,size,trashed"
            CloudProvider.OneDrive -> "https://graph.microsoft.com/v1.0/me/drive/items/${Uri.encode(remoteId)}?select=id,size,deleted"
        }
        request("GET", url, token)
    }

    private data class Response(val body: ByteArray, val headers: Map<String, List<String>>)

    private fun request(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { input -> ByteArrayOutputStream().also { input.copyTo(it) }.toByteArray() } ?: byteArrayOf()
            if (code !in 200..299) {
                val detail = String(response).take(300).ifBlank { "HTTP $code" }
                error("Cloud service rejected the upload: $detail")
            }
            return Response(response, connection.headerFields)
        } finally {
            connection.disconnect()
        }
    }

    private fun contentType(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "heic", "heif" -> "image/heic"
        "png" -> "image/png"
        "mov" -> "video/quicktime"
        "mp4" -> "video/mp4"
        else -> "image/jpeg"
    }

    private companion object { const val MAX_ATTEMPTS = 3 }
}
