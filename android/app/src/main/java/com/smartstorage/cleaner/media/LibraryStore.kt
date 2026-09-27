package com.smartstorage.cleaner.media

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.os.storage.StorageManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.smartstorage.cleaner.model.CleanupPlan
import com.smartstorage.cleaner.model.LibraryContent
import com.smartstorage.cleaner.model.StorageSummary
import com.smartstorage.cleaner.model.formattedBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
    /** Short confirmation shown after a delete/compress; cleared when dismissed. */
    val notice: String? = null,
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
    private val cache by lazy { AnalysisStore.open(app) }
    private val prefs = app.getSharedPreferences("keepspace", Context.MODE_PRIVATE)
    private var scanJob: Job? = null

    // Last scan inputs, so deletions update the screens without a full rescan.
    private var lastItems: List<MediaItem> = emptyList()
    private var lastAnalyzed: List<AnalyzedPhoto> = emptyList()
    private var lastScreenshotInfo: Map<String, ScreenshotInfo> = emptyMap()

    private val _state = MutableStateFlow(
        if (demo) LibraryState(LibraryAccess.Authorized, ScanPhase.Ready, LibraryContent.demo, isDemo = true)
        else LibraryState(currentAccess(), ScanPhase.Idle, LibraryContent.empty(deviceStorage())),
    )
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun setCleanupTarget(bytes: Long?) = _state.update { it.copy(cleanupTarget = bytes) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /** Deletes after confirmation. Items go to the system Trash (30 days) on Android 11+. */
    suspend fun delete(ids: Set<String>, actions: MediaActions): DeletionOutcome {
        if (_state.value.isDemo || ids.isEmpty()) return DeletionOutcome.Cancelled
        val bytes = lastItems.filter { it.id in ids }.sumOf { it.bytes }
        val outcome = try {
            if (actions.trash(ids.map(Uri::parse))) DeletionOutcome.Deleted(ids.size, bytes) else DeletionOutcome.Cancelled
        } catch (e: Exception) {
            DeletionOutcome.Failed(e.message ?: "Unknown error")
        }
        when (outcome) {
            is DeletionOutcome.Deleted -> {
                val where = if (actions.needsInAppConfirmation) "deleted" else "moved to Trash. Empty it in your gallery to free the space now"
                val noun = if (outcome.count == 1) "item" else "items"
                _state.update { it.copy(notice = "${outcome.count} $noun (${outcome.bytes.formattedBytes()}) $where.") }
                // Updating results can remove the calling screen (and cancel its scope), so this must
                // not suspend; cache cleanup runs in the store's own scope.
                removeFromResults(ids)
            }
            is DeletionOutcome.Failed -> _state.update { it.copy(notice = "Couldn't delete: ${outcome.message}") }
            DeletionOutcome.Cancelled -> Unit
        }
        return outcome
    }

    /** Returns an error message to show, or null on success / cancel. */
    suspend fun compress(videoId: String, preset: CompressionPreset, actions: MediaActions, onProgress: (Float) -> Unit): String? {
        val video = lastItems.firstOrNull { it.id == videoId } ?: return "This video is no longer in your library."
        return try {
            when (val result = actions.compress(video, preset, onProgress)) {
                is MediaActions.CompressionOutcome.Replaced -> {
                    _state.update {
                        it.copy(notice = "Compressed ${result.originalBytes.formattedBytes()} → ${result.newBytes.formattedBytes()}. The original is in Trash.")
                    }
                    scan() // pick up the new copy; everything else comes from the cache
                    null
                }
                is MediaActions.CompressionOutcome.NotWorthIt ->
                    "Compression would only save ${result.saved.formattedBytes()}, so the original was kept."
                MediaActions.CompressionOutcome.Cancelled -> null
            }
        } catch (e: Exception) {
            "Compression failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun removeFromResults(ids: Set<String>) {
        lastItems = lastItems.filterNot { it.id in ids }
        lastAnalyzed = lastAnalyzed.filterNot { it.id in ids }
        lastScreenshotInfo = lastScreenshotInfo - ids
        rebuild()
        scope.launch(Dispatchers.IO) {
            cache.delete(ids.toList())
            cache.deleteScreenshots(ids.toList())
        }
    }

    /** Stops any running scan; the store is unusable afterwards. */
    fun close() = scope.cancel()

    /** Call after the permission dialog returns, and whenever the app comes to the foreground. */
    fun refreshAccess(afterRequest: Boolean = false) {
        if (_state.value.isDemo) return
        if (afterRequest) prefs.edit { putBoolean(KEY_ASKED, true) }
        val access = currentAccess()
        _state.update { it.copy(access = access) }
        // Incremental thanks to the cache, so returning to the app always picks up library changes.
        if (access.canRead) scan()
    }

    fun scan() {
        val s = _state.value
        if (s.isDemo || !s.access.canRead || s.isScanning) return
        scanJob = scope.launch { runScan() }
    }

    private suspend fun runScan() {
        _state.update { it.copy(phase = ScanPhase.LoadingLibrary) }
        val items = withContext(Dispatchers.IO) { MediaStoreSource(app.contentResolver).load() }

        // Reuse cached analysis; only new or edited items go through the AI again.
        val photos = items.filter { it.kind == MediaItem.Kind.Photo }
        val screenshots = items.filter { it.kind == MediaItem.Kind.Screenshot }
        // Text is read from screenshots and from photos that look like paper receipts.
        fun toRead(analyzed: List<AnalyzedPhoto>) = screenshots + analyzed.filter(PaperReceiptDetector::isCandidate).map { it.item }
        val (plan, cachedReads) = withContext(Dispatchers.IO) {
            CachePlanner.plan(photos, cache.loadAll()).also { cache.delete(it.staleIds) } to cache.loadScreenshots()
        }
        val earlyReads = CachePlanner.planScreenshots(toRead(plan.hits), cachedReads)
        // Counts only — never filenames or other personal data.
        Log.i(TAG, "scan: ${items.size} items, ${plan.hits.size} cached, ${plan.toAnalyze.size} to analyze, ${plan.staleIds.size} stale; text reads ${earlyReads.hits.size} cached, ${earlyReads.toAnalyze.size} to read")

        // Publish straight away: sizes plus everything the cache already knows.
        lastItems = items
        lastAnalyzed = plan.hits
        lastScreenshotInfo = earlyReads.hits
        val estimate = plan.toAnalyze.size + earlyReads.toAnalyze.size
        rebuild(if (estimate == 0) ScanPhase.Ready else ScanPhase.Analyzing(0, estimate))

        if (plan.toAnalyze.isNotEmpty()) {
            val fresh = withContext(Dispatchers.Default) { analyze(plan.toAnalyze, estimate) }
            lastAnalyzed = plan.hits + fresh
        }

        // Newly analyzed photos may have added receipt candidates.
        val reads = CachePlanner.planScreenshots(toRead(lastAnalyzed), cachedReads)
        withContext(Dispatchers.IO) { cache.deleteScreenshots(reads.staleIds) }
        lastScreenshotInfo = lastScreenshotInfo - reads.staleIds.toSet()
        val total = plan.toAnalyze.size + reads.toAnalyze.size
        if (reads.toAnalyze.isEmpty()) {
            rebuild(ScanPhase.Ready)
            return
        }
        rebuild(ScanPhase.Analyzing(plan.toAnalyze.size, total))
        val read = withContext(Dispatchers.Default) { readScreenshots(reads.toAnalyze, offset = plan.toAnalyze.size, total = total) }
        lastScreenshotInfo = lastScreenshotInfo + read
        rebuild(ScanPhase.Ready)
    }

    private fun rebuild(phase: ScanPhase? = null) {
        val storage = deviceStorage()
        val content = builder.build(lastItems, lastAnalyzed, storage.totalBytes, storage.freeBytes, screenshotInfo = lastScreenshotInfo)
        _state.update { if (phase != null) it.copy(content = content, phase = phase) else it.copy(content = content) }
    }

    /** OCR + barcode pass over screenshots, two at a time, persisting every [BATCH_SIZE]. */
    private suspend fun readScreenshots(shots: List<MediaItem>, offset: Int, total: Int): Map<String, ScreenshotInfo> = coroutineScope {
        val done = AtomicInteger()
        val permits = Semaphore(2)
        val pending = mutableListOf<CachedScreenshot>()
        val lock = Mutex()
        suspend fun flush(force: Boolean) {
            val batch = lock.withLock {
                if (!force && pending.size < BATCH_SIZE) return
                pending.toList().also { pending.clear() }
            }
            withContext(Dispatchers.IO) { cache.saveScreenshots(batch) }
        }
        ScreenshotAnalyzer(app.contentResolver, ThaiOcr(app)).use { reader ->
            val results = shots.map { item ->
                async {
                    permits.withPermit {
                        // Photos here are paper-receipt candidates: read larger, text only.
                        val photo = item.kind == MediaItem.Kind.Photo
                        val info = reader.analyze(
                            Uri.parse(item.id),
                            side = if (photo) PaperReceiptDetector.READ_SIDE else ScreenshotAnalyzer.READ_SIDE,
                            isPhoto = photo,
                        )
                        val n = done.incrementAndGet()
                        if (n % 5 == 0 || n == shots.size) _state.update { it.copy(phase = ScanPhase.Analyzing(offset + n, total)) }
                        if (info != null) {
                            lock.withLock { pending += CachedScreenshot(item.id, item.modifiedAt, SCREENSHOT_READER_VERSION, info) }
                            flush(force = false)
                        }
                        info?.let { item.id to it }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
            flush(force = true)
            results
        }
    }

    /** Analyzes [photos] four at a time, persisting every [BATCH_SIZE] results so interrupted scans keep progress. */
    private suspend fun analyze(photos: List<MediaItem>, total: Int): List<AnalyzedPhoto> = coroutineScope {
        val done = AtomicInteger()
        val permits = Semaphore(4)
        val pending = mutableListOf<AnalyzedPhoto>()
        val pendingLock = Mutex()
        suspend fun flush(force: Boolean) {
            val batch = pendingLock.withLock {
                if (!force && pending.size < BATCH_SIZE) return
                pending.toList().also { pending.clear() }
            }
            withContext(Dispatchers.IO) {
                cache.save(batch.map { CachedAnalysis(it.id, it.item.modifiedAt, ANALYZER_VERSION, it.features) })
            }
        }

        ImageAnalyzer(app.contentResolver).use { analyzer ->
            val results = photos.map { item ->
                async {
                    permits.withPermit {
                        val result = analyzer.analyze(Uri.parse(item.id))?.let { AnalyzedPhoto(item, it) }
                        val n = done.incrementAndGet()
                        if (n % 10 == 0 || n == photos.size) _state.update { it.copy(phase = ScanPhase.Analyzing(n, total)) }
                        if (result != null) {
                            pendingLock.withLock { pending += result }
                            flush(force = false)
                        }
                        result
                    }
                }
            }.awaitAll().filterNotNull()
            flush(force = true)
            results
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
        private const val BATCH_SIZE = 25
        private const val TAG = "KeepSpaceScan"

        /** Permissions to request for library access on this OS version. */
        val permissions: Array<String>
            get() = when {
                // ACCESS_MEDIA_LOCATION (10+) lets trips be told from home; photos work without it.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    Manifest.permission.ACCESS_MEDIA_LOCATION,
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.ACCESS_MEDIA_LOCATION,
                )
                // Android 8–10: deleting other apps' media needs write access too.
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_MEDIA_LOCATION, // 10 only; ignored on 8–9
                )
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
    }
}

val LocalLibraryStore = staticCompositionLocalOf<LibraryStore> { error("LibraryStore not provided") }

/** Current library state for the calling composable; recomposes as scans progress. */
@Composable
fun libraryState(): LibraryState = LocalLibraryStore.current.state.collectAsState().value

/** Requests library permissions; provided by the activity, which owns the permission launcher. */
val LocalRequestLibraryAccess = staticCompositionLocalOf<() -> Unit> { {} }
