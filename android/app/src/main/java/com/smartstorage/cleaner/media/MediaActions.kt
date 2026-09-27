package com.smartstorage.cleaner.media

import android.app.Activity
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMuxer
import androidx.media3.container.Mp4TimestampData
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as Media3Item

/**
 * Changes to the user's library. On Android 11+ every destructive change goes through the system
 * trash request: Android shows its own confirmation and keeps items in Trash for 30 days.
 * On Android 8–10 the app confirms in-app first (see [needsInAppConfirmation]).
 */
class MediaActions(private val activity: ComponentActivity) {
    private var pending: CompletableDeferred<Boolean>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        pending?.complete(it.resultCode == Activity.RESULT_OK)
        pending = null
    }
    private val resolver get() = activity.contentResolver

    /** Before Android 11 there is no system prompt, so the UI must ask first. */
    val needsInAppConfirmation: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    /** Video compression needs MediaStore relative paths (Android 10+). */
    val canCompress: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Moves items to the system trash (after the system prompt), or deletes them on Android 8–10. */
    suspend fun trash(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createTrashRequest(resolver, uris, true)
            val result = CompletableDeferred<Boolean>().also { pending = it }
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            result.await()
        } else {
            withContext(Dispatchers.IO) { uris.forEach { resolver.delete(it, null, null) } }
            true
        }
    }

    sealed interface CompressionOutcome {
        data class Replaced(val originalBytes: Long, val newBytes: Long) : CompressionOutcome
        data object Cancelled : CompressionOutcome
        data class NotWorthIt(val saved: Long) : CompressionOutcome
    }

    /**
     * Re-encodes a video on device, saves the smaller copy next to the original (same capture date),
     * then asks to trash the original. If the user declines, the copy is removed so nothing changes.
     */
    @OptIn(UnstableApi::class)
    suspend fun compress(
        video: MediaItem,
        preset: CompressionPreset,
        onProgress: (Float) -> Unit,
    ): CompressionOutcome {
        val source = Uri.parse(video.id)
        val output = File(activity.cacheDir, "keepspace-${System.currentTimeMillis()}.mp4")
        try {
            transcode(source, output, video, preset, onProgress)
            val newBytes = output.length()
            if (!CompressionEstimator.shouldReplace(video.bytes, newBytes)) {
                return CompressionOutcome.NotWorthIt(maxOf(0, video.bytes - newBytes))
            }
            val copy = withContext(Dispatchers.IO) { saveCopy(source, output, video) }
            return if (trash(listOf(source))) {
                CompressionOutcome.Replaced(video.bytes, newBytes)
            } else {
                // Keep the library exactly as it was: remove the copy we just added (we own it).
                withContext(Dispatchers.IO) { resolver.delete(copy, null, null) }
                CompressionOutcome.Cancelled
            }
        } finally {
            output.delete()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun transcode(source: Uri, output: File, video: MediaItem, preset: CompressionPreset, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val encoder = DefaultEncoderFactory.Builder(activity)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate((preset.bitsPerSecond - 128_000).toInt()).build(),
                    )
                    .build()
                // MediaStore takes DATE_TAKEN from the MP4 header when the file is published, so the
                // original capture time must be written into the container itself.
                val captureTime = Mp4TimestampData.unixTimeToMp4TimeSeconds(video.createdAt)
                val muxer = InAppMuxer.Factory.Builder()
                    .setMetadataProvider { entries ->
                        entries.removeAll { it is Mp4TimestampData }
                        entries.add(Mp4TimestampData(captureTime, captureTime))
                    }
                    .build()
                val transformer = Transformer.Builder(activity)
                    .setMuxerFactory(muxer)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoder)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()

                // Scale so the short side matches the preset; never upscale.
                val (w, h) = targetSize(video.width, video.height, preset.shortSide)
                val edited = EditedMediaItem.Builder(Media3Item.fromUri(source))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))))
                    .build()
                transformer.start(edited, output.absolutePath)

                val handler = Handler(Looper.getMainLooper())
                val holder = ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (!cont.isActive) return
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        handler.postDelayed(this, 250)
                    }
                }
                handler.post(poll)
                cont.invokeOnCancellation {
                    handler.removeCallbacks(poll)
                    transformer.cancel()
                }
            }
        }

    /** Inserts the compressed file beside the original, keeping its capture date and folder. */
    private fun saveCopy(source: Uri, file: File, video: MediaItem): Uri {
        val folder = resolver.query(source, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Movies/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "KeepSpace_${video.createdAt}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.DATE_TAKEN, video.createdAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't save the compressed video")
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    companion object {
        /** Even dimensions (encoders require it) with the short side at most [shortSide]. */
        fun targetSize(width: Int, height: Int, shortSide: Int): Pair<Int, Int> {
            val currentShort = minOf(width, height).coerceAtLeast(1)
            val scale = minOf(1.0, shortSide.toDouble() / currentShort)
            fun even(v: Double) = ((v / 2).roundToInt() * 2).coerceAtLeast(2)
            return even(width * scale) to even(height * scale)
        }
    }
}

val LocalMediaActions = staticCompositionLocalOf<MediaActions?> { null }
