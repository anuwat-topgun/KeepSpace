package com.smartstorage.cleaner.media

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Reads the device media library through MediaStore (sizes come straight from the index). */
class MediaStoreSource(private val resolver: ContentResolver) {

    fun load(): List<MediaItem> = query(images = true) + query(images = false)

    private fun query(images: Boolean): List<MediaItem> {
        val collection: Uri = if (images) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val hasFavorite = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val pathColumn = if (hasRelativePath) MediaStore.MediaColumns.RELATIVE_PATH else @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(pathColumn)
            if (!images) add(MediaStore.Video.VideoColumns.DURATION)
            if (hasFavorite) add(MediaStore.MediaColumns.IS_FAVORITE)
        }.toTypedArray()

        val result = mutableListOf<MediaItem>()
        resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val taken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modified = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val displayName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val size = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val width = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val height = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val path = c.getColumnIndexOrThrow(pathColumn)
            val duration = if (images) -1 else c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
            val favorite = if (hasFavorite) c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE) else -1

            while (c.moveToNext()) {
                val folder = c.getString(path).orEmpty().lowercase()
                val kind = when {
                    images && "screenshot" in folder -> MediaItem.Kind.Screenshot
                    images -> MediaItem.Kind.Photo
                    "screenrecord" in folder || "screen record" in folder -> MediaItem.Kind.ScreenRecording
                    else -> MediaItem.Kind.Video
                }
                // DATE_TAKEN (ms) can be missing for downloaded files; fall back to DATE_ADDED (s).
                val createdAt = c.getLong(taken).takeIf { it > 0 } ?: (c.getLong(added) * 1000)
                result += MediaItem(
                    id = ContentUris.withAppendedId(collection, c.getLong(id)).toString(),
                    kind = kind,
                    createdAt = createdAt,
                    bytes = c.getLong(size),
                    width = c.getInt(width),
                    height = c.getInt(height),
                    durationMs = if (duration >= 0) c.getLong(duration) else 0,
                    isFavorite = favorite >= 0 && c.getInt(favorite) == 1,
                    modifiedAt = c.getLong(modified) * 1000,
                    fileName = c.getString(displayName),
                )
            }
        }
        return result
    }
}
