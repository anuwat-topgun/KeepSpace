package com.smartstorage.cleaner.media

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.smartstorage.cleaner.model.CleanupPlan
import com.smartstorage.cleaner.model.LibraryContent
import com.smartstorage.cleaner.model.StorageSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class LibraryAccess { NotDetermined, Authorized, Limited, Denied;

    val canRead: Boolean get() = this == Authorized || this == Limited
}

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data object LoadingLibrary : ScanPhase
    data class Analyzing(val done: Int, val total: Int) : ScanPhase
    data object Ready : ScanPhase
}

data class LibraryState(
    val access: LibraryAccess,
    val phase: ScanPhase,
    val content: LibraryContent,
    /** Cleanup target chosen on the Clean tab; null = Maximum Safe Cleanup. */
    val cleanupTarget: Long? = 10_000_000_000,
    val isDemo: Boolean = false,
) {
    val cleanupPlan: CleanupPlan get() = content.plan(cleanupTarget)
    val isScanning: Boolean get() = phase is ScanPhase.LoadingLibrary || phase is ScanPhase.Analyzing
}

/**
 * Single source of truth for library data shown in the UI. Mirrors `LibraryStore.swift`.
 * Lives as long as the activity; analysis results are cached in memory so rescans are incremental.
 */
class LibraryStore(context: Context, demo: Boolean) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val builder = LibraryReportBuilder()
    private val featureCache = ConcurrentHashMap<String, ImageFeatures>()
    private val prefs = app.getSharedPreferences("keepspace", Context.MODE_PRIVATE)
    private var scanJob: Job? = null

    private val _state = MutableStateFlow(
        if (demo) LibraryState(LibraryAccess.Authorized, ScanPhase.Ready, LibraryContent.demo, isDemo = true)
        else LibraryState(currentAccess(), ScanPhase.Idle, LibraryContent.empty(deviceStorage())),
    )
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun setCleanupTarget(bytes: Long?) = _state.update { it.copy(cleanupTarget = bytes) }

    /** Stops any running scan; the store is unusable afterwards. */
    fun close() = scope.cancel()

    /** Call after the permission dialog returns, and whenever the app comes to the foreground. */
    fun refreshAccess(afterRequest: Boolean = false) {
        if (_state.value.isDemo) return
        if (afterRequest) prefs.edit { putBoolean(KEY_ASKED, true) }
        val access = currentAccess()
        val changed = access != _state.value.access
        _state.update { it.copy(access = access) }
        if (access.canRead && (changed || _state.value.phase == ScanPhase.Idle)) scan()
    }

    fun scan() {
        val s = _state.value
        if (s.isDemo || !s.access.canRead || s.isScanning) return
        scanJob = scope.launch { runScan() }
    }

    private suspend fun runScan() {
        _state.update { it.copy(phase = ScanPhase.LoadingLibrary) }
        val storage = deviceStorage()
        val items = withContext(Dispatchers.IO) { MediaStoreSource(app.contentResolver).load() }

        // Publish sizes straight away so Home is useful while photos are still being analyzed.
        val photos = items.filter { it.kind == MediaItem.Kind.Photo }
        _state.update {
            it.copy(
                content = builder.build(items, emptyList(), storage.totalBytes, storage.freeBytes),
                phase = ScanPhase.Analyzing(0, photos.size),
            )
        }

        val analyzed = withContext(Dispatchers.Default) {
            ImageAnalyzer(app.contentResolver).use { analyzer ->
                val done = AtomicInteger()
                val permits = Semaphore(4)
                photos.map { item ->
                    async {
                        permits.withPermit {
                            val features = featureCache[item.id] ?: analyzer.analyze(Uri.parse(item.id))?.also { featureCache[item.id] = it }
                            val n = done.incrementAndGet()
                            if (n % 10 == 0 || n == photos.size) _state.update { it.copy(phase = ScanPhase.Analyzing(n, photos.size)) }
                            features?.let { AnalyzedPhoto(item, it) }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
        _state.update {
            it.copy(content = builder.build(items, analyzed, storage.totalBytes, storage.freeBytes), phase = ScanPhase.Ready)
        }
    }

    private fun currentAccess(): LibraryAccess {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        val full = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)
            else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return when {
            full -> LibraryAccess.Authorized
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> LibraryAccess.Limited
            prefs.getBoolean(KEY_ASKED, false) -> LibraryAccess.Denied
            else -> LibraryAccess.NotDetermined
        }
    }

    private fun deviceStorage(): StorageSummary {
        val stats = app.getSystemService(StorageStatsManager::class.java)
        val total = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
        val free = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
        return StorageSummary(total - free, total, 0, emptyMap())
    }

    companion object {
        private const val KEY_ASKED = "askedPhotoPermission"

        /** Permissions to request for library access on this OS version. */
        val permissions: Array<String>
            get() = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
    }
}

val LocalLibraryStore = staticCompositionLocalOf<LibraryStore> { error("LibraryStore not provided") }

/** Current library state for the calling composable; recomposes as scans progress. */
@Composable
fun libraryState(): LibraryState = LocalLibraryStore.current.state.collectAsState().value

/** Requests library permissions; provided by the activity, which owns the permission launcher. */
val LocalRequestLibraryAccess = staticCompositionLocalOf<() -> Unit> { {} }
