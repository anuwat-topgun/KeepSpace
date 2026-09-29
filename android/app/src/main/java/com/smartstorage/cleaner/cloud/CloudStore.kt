package com.smartstorage.cleaner.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.BackoffPolicy
import com.smartstorage.cleaner.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest as GoogleAuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.smartstorage.cleaner.media.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest as AppAuthAuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class CloudStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("cloud_backup", Context.MODE_PRIVATE)
    private val secure = SecureAuthStore(app)
    private val authService = AuthorizationService(app)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<CloudState> = _state.asStateFlow()

    fun authorizationIntent(provider: CloudProvider): Intent? {
        if (provider == CloudProvider.GoogleDrive) return null
        val clientId = clientId(provider)
        if (clientId.isBlank()) {
            notice("Add ${provider.title} OAuth client ID to the release configuration first.")
            return null
        }
        val request = AppAuthAuthorizationRequest.Builder(configuration(provider), clientId, ResponseTypeValues.CODE, REDIRECT_URI)
            .setScope(scopes(provider))
            .setAdditionalParameters(if (provider == CloudProvider.GoogleDrive) mapOf("access_type" to "offline", "prompt" to "consent") else emptyMap())
            .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /** Google explicitly disallows browser custom-scheme OAuth on Android; use AuthorizationClient. */
    fun authorizeGoogle(activity: Activity, launchResolution: (IntentSenderRequest) -> Unit) {
        val request = GoogleAuthorizationRequest.builder().setRequestedScopes(GOOGLE_SCOPES).build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    launchResolution(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
                } else saveGoogleAuthorization(result)
            }
            .addOnFailureListener { notice(it.localizedMessage ?: "Couldn't connect Google Drive.") }
    }

    fun completeGoogleAuthorization(activity: Activity, data: Intent?) {
        runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data) }
            .onSuccess(::saveGoogleAuthorization)
            .onFailure { notice(it.localizedMessage ?: "Google Drive connection was cancelled.") }
    }

    private fun saveGoogleAuthorization(result: AuthorizationResult) {
        val token = result.accessToken ?: run { notice("Google Drive returned no access token."); return }
        secure.put(GOOGLE_TOKEN_KEY, JSONObject().put("accessToken", token)
            .put("expiresAt", System.currentTimeMillis() + 55 * 60 * 1000L).toString())
        updateConnection(CloudProvider.GoogleDrive, connected = true, accountName = "Google Drive")
        notice("Google Drive connected. Backups go directly to this account.")
    }

    fun completeAuthorization(provider: CloudProvider, data: Intent?) {
        val response = data?.let(AuthorizationResponse::fromIntent)
        val error = data?.let(AuthorizationException::fromIntent)
        if (response == null) {
            notice(error?.errorDescription ?: "Connection cancelled.")
            return
        }
        authService.performTokenRequest(response.createTokenExchangeRequest()) { token, tokenError ->
            if (token == null) {
                notice(tokenError?.errorDescription ?: "Couldn't exchange the authorization code.")
                return@performTokenRequest
            }
            val auth = AuthState(response, token, tokenError)
            secure.put(authKey(provider), auth.jsonSerializeString())
            val account = response.additionalParameters["login_hint"]
            updateConnection(provider, connected = true, accountName = account ?: provider.title)
            notice("${provider.title} connected. Backups go directly to this account.")
        }
    }

    fun disconnect(provider: CloudProvider) {
        if (provider == CloudProvider.GoogleDrive) {
            val token = secure.get(GOOGLE_TOKEN_KEY)?.let { runCatching { JSONObject(it).optString("accessToken") }.getOrNull() }
            secure.remove(GOOGLE_TOKEN_KEY)
            if (!token.isNullOrBlank()) ioScope.launch { revokeGoogleToken(token) }
        }
        secure.remove(authKey(provider))
        updateConnection(provider, connected = false, accountName = null)
        notice("${provider.title} disconnected. Existing cloud files were not changed.")
    }

    fun updatePreferences(value: UploadPreferences) {
        persist(_state.value.copy(preferences = value.copy(keepLocalCopies = true)))
    }

    fun enqueue(provider: CloudProvider, scope: CloudBackupScope, folder: String, items: List<MediaItem>) {
        if (!_state.value.connections.any { it.provider == provider && it.connected }) {
            notice("Connect ${provider.title} before starting a backup.")
            return
        }
        val cleanFolder = folder.trim().trim('/').ifBlank { scope.title }
        val queued = items.map { media ->
            val original = media.fileName ?: "${scope.name.lowercase()}-${media.id.hashCode().toUInt()}"
            val name = if (_state.value.preferences.renameAutomatically) {
                val extension = original.substringAfterLast('.', "jpg")
                "KeepSpace-${Instant.ofEpochMilli(media.createdAt).toString().take(10)}-${media.id.hashCode().toUInt()}.$extension"
            } else original
            CloudUploadItem(provider = provider, scope = scope, sourceUri = media.id, sourceName = name,
                destinationFolder = cleanFolder, bytes = media.bytes)
        }
        if (queued.isEmpty()) {
            notice("No ${scope.title.lowercase()} are available to back up.")
            return
        }
        persist(_state.value.copy(uploads = _state.value.uploads + queued, notice = "Queued ${queued.size} items for ${provider.title}."))
        schedule()
    }

    fun cancelPending() {
        WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
        val changed = _state.value.uploads.map {
            if (it.status in setOf(CloudUploadStatus.Waiting, CloudUploadStatus.Uploading, CloudUploadStatus.Verifying)) it.copy(status = CloudUploadStatus.Cancelled) else it
        }
        persist(_state.value.copy(uploads = changed, notice = "Pending backup cancelled."))
    }

    fun retryFailed() {
        persist(_state.value.copy(uploads = _state.value.uploads.map {
            if (it.status == CloudUploadStatus.Failed) it.copy(status = CloudUploadStatus.Waiting, error = null, progress = 0) else it
        }))
        schedule()
    }

    internal fun authState(provider: CloudProvider): AuthState? = secure.get(authKey(provider))?.let {
        runCatching { AuthState.jsonDeserialize(it) }.getOrNull()
    }
    internal fun googleAccessToken(): String? = secure.get(GOOGLE_TOKEN_KEY)?.let { raw ->
        runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.optLong("expiresAt") > System.currentTimeMillis() }?.optString("accessToken")
    }

    internal fun saveAuth(provider: CloudProvider, auth: AuthState) = secure.put(authKey(provider), auth.jsonSerializeString())
    internal fun snapshot(): CloudState = load()
    internal fun replaceUpload(updated: CloudUploadItem) {
        val current = load()
        persist(current.copy(uploads = current.uploads.map { if (it.id == updated.id) updated else it }))
    }

    private fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (_state.value.preferences.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val work = OneTimeWorkRequestBuilder<CloudUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    private fun updateConnection(provider: CloudProvider, connected: Boolean, accountName: String?) {
        val connections = _state.value.connections.map {
            if (it.provider == provider) it.copy(connected = connected, accountName = accountName) else it
        }
        persist(_state.value.copy(connections = connections))
    }

    private fun notice(message: String) = persist(_state.value.copy(notice = message))
    private fun revokeGoogleToken(token: String) {
        val connection = URL("https://oauth2.googleapis.com/revoke?token=${Uri.encode(token)}").openConnection() as HttpURLConnection
        try { connection.requestMethod = "POST"; connection.connectTimeout = 15_000; connection.readTimeout = 15_000; connection.responseCode }
        finally { connection.disconnect() }
    }
    private fun clientId(provider: CloudProvider) = when (provider) {
        CloudProvider.GoogleDrive -> "google-identity-services"
        CloudProvider.OneDrive -> BuildConfig.MICROSOFT_OAUTH_CLIENT_ID
    }
    private fun authKey(provider: CloudProvider) = "auth.${provider.name}"

    private fun configuration(provider: CloudProvider) = when (provider) {
        CloudProvider.GoogleDrive -> AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"), Uri.parse("https://oauth2.googleapis.com/token"),
            null, Uri.parse("https://oauth2.googleapis.com/revoke"))
        CloudProvider.OneDrive -> AuthorizationServiceConfiguration(
            Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
            Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/token"))
    }

    private fun scopes(provider: CloudProvider) = when (provider) {
        CloudProvider.GoogleDrive -> "openid email profile https://www.googleapis.com/auth/drive.file"
        CloudProvider.OneDrive -> "openid profile email offline_access Files.ReadWrite.AppFolder"
    }

    @Synchronized private fun persist(value: CloudState) {
        prefs.edit { putString(KEY_STATE, value.toJson().toString()) }
        _state.value = value
    }

    private fun load(): CloudState = prefs.getString(KEY_STATE, null)?.let { raw ->
        runCatching { JSONObject(raw).toCloudState() }.getOrNull()
    } ?: CloudState(
        connections = CloudProvider.entries.map { CloudConnection(it, configured = clientId(it).isNotBlank()) },
        preferences = UploadPreferences(), uploads = emptyList(),
    )

    companion object {
        val REDIRECT_URI: Uri = Uri.parse("keepspace://oauth2redirect")
        const val WORK_NAME = "cloud-backup"
        private const val KEY_STATE = "state"
        private const val GOOGLE_TOKEN_KEY = "google.accessToken"
        private val GOOGLE_SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/drive.file"),
            Scope("openid"), Scope("profile"), Scope("email"),
        )
    }
}

val LocalCloudStore = staticCompositionLocalOf<CloudStore> { error("CloudStore not provided") }

private fun CloudState.toJson() = JSONObject().apply {
    put("connections", JSONArray(connections.map { JSONObject().put("provider", it.provider.name).put("connected", it.connected).put("account", it.accountName).put("configured", it.configured) }))
    put("preferences", JSONObject().put("rename", preferences.renameAutomatically).put("wifi", preferences.wifiOnly).put("keep", true))
    put("uploads", JSONArray(uploads.map { it.toJson() }))
    put("notice", notice)
}

private fun CloudUploadItem.toJson() = JSONObject().apply {
    put("id", id); put("provider", provider.name); put("scope", scope.name); put("uri", sourceUri); put("name", sourceName)
    put("folder", destinationFolder); put("bytes", bytes); put("status", status.name); put("progress", progress); put("attempt", attempt)
    put("error", error); put("remoteId", remoteId)
}

private fun JSONObject.toCloudState(): CloudState {
    val connectionsJson = optJSONArray("connections") ?: JSONArray()
    val connections = CloudProvider.entries.map { provider ->
        (0 until connectionsJson.length()).map { connectionsJson.getJSONObject(it) }.firstOrNull { it.optString("provider") == provider.name }
            ?.let { CloudConnection(provider, it.optBoolean("connected"), it.optString("account").ifBlank { null }, it.optBoolean("configured")) }
            ?: CloudConnection(provider)
    }
    val p = optJSONObject("preferences") ?: JSONObject()
    val uploadsJson = optJSONArray("uploads") ?: JSONArray()
    val uploads = (0 until uploadsJson.length()).map { uploadsJson.getJSONObject(it) }.map { o ->
        CloudUploadItem(o.getString("id"), CloudProvider.valueOf(o.getString("provider")), CloudBackupScope.valueOf(o.getString("scope")),
            o.getString("uri"), o.getString("name"), o.getString("folder"), o.optLong("bytes"),
            CloudUploadStatus.valueOf(o.optString("status", "Waiting")), o.optInt("progress"), o.optInt("attempt"),
            o.optString("error").ifBlank { null }, o.optString("remoteId").ifBlank { null })
    }
    return CloudState(connections, UploadPreferences(p.optBoolean("rename", true), p.optBoolean("wifi", true), true), uploads, optString("notice").ifBlank { null })
}
