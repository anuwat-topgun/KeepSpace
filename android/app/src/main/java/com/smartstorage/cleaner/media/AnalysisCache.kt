package com.smartstorage.cleaner.media

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

// Mirrors ios/SmartStorage/MediaEngine/AnalysisCache.swift.

/**
 * Bump whenever [ImageAnalyzer] output changes meaning (new metric, new model), so stale cached
 * results are re-analyzed instead of silently mixed with new ones.
 */
const val ANALYZER_VERSION = 1

data class CachedAnalysis(
    val assetId: String,
    /** Asset modification time when analyzed; an edit invalidates the entry. */
    val modifiedAt: Long,
    val version: Int,
    val features: ImageFeatures,
)

/** Decides what a scan can reuse from the cache and what must be (re)analyzed. Pure, for tests. */
object CachePlanner {
    data class Plan(
        val hits: List<AnalyzedPhoto>,
        val toAnalyze: List<MediaItem>,
        /** Cached assets that no longer exist in the library. */
        val staleIds: List<String>,
    )

    fun plan(photos: List<MediaItem>, cached: Map<String, CachedAnalysis>, version: Int = ANALYZER_VERSION): Plan {
        val hits = mutableListOf<AnalyzedPhoto>()
        val toAnalyze = mutableListOf<MediaItem>()
        for (item in photos) {
            val entry = cached[item.id]
            if (entry != null && entry.version == version && entry.modifiedAt == item.modifiedAt) {
                hits += AnalyzedPhoto(item, entry.features)
            } else {
                toAnalyze += item
            }
        }
        val live = photos.mapTo(HashSet()) { it.id }
        return Plan(hits, toAnalyze, cached.keys.filter { it !in live }.sorted())
    }
}

// region Room

@Entity(tableName = "analysis")
data class AnalysisEntity(
    @PrimaryKey val assetId: String,
    val modifiedAt: Long,
    val version: Int,
    val dHash: Long,
    val sharpness: Double,
    val exposure: Double,
    val faceQuality: Double?,
    val faceCount: Int,
) {
    fun toCached() = CachedAnalysis(assetId, modifiedAt, version, ImageFeatures(dHash, sharpness, exposure, faceQuality, faceCount))

    companion object {
        fun from(entry: CachedAnalysis) = with(entry.features) {
            AnalysisEntity(entry.assetId, entry.modifiedAt, entry.version, dHash, sharpness, exposure, faceQuality, faceCount)
        }
    }
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analysis")
    suspend fun all(): List<AnalysisEntity>

    @Upsert
    suspend fun upsert(entities: List<AnalysisEntity>)

    @Query("DELETE FROM analysis WHERE assetId IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("SELECT COUNT(*) FROM analysis")
    suspend fun count(): Int
}

@Database(entities = [AnalysisEntity::class], version = 1, exportSchema = false)
abstract class AnalysisDatabase : RoomDatabase() {
    abstract fun analysis(): AnalysisDao
}

// endregion

/**
 * On-device cache of analysis results. Stored in the no-backup directory: it is derived,
 * regenerable data, and image fingerprints must never leave the device.
 */
class AnalysisStore private constructor(private val dao: AnalysisDao) {

    suspend fun loadAll(): Map<String, CachedAnalysis> = dao.all().associate { it.assetId to it.toCached() }

    suspend fun save(entries: List<CachedAnalysis>) {
        if (entries.isNotEmpty()) dao.upsert(entries.map(AnalysisEntity::from))
    }

    /** SQLite caps bound parameters (999 on older versions), so delete in chunks. */
    suspend fun delete(ids: List<String>) = ids.chunked(500).forEach { dao.delete(it) }

    suspend fun count(): Int = dao.count()

    companion object {
        fun open(context: Context): AnalysisStore {
            val file = context.noBackupFilesDir.resolve("analysis.db")
            val db = Room.databaseBuilder(context, AnalysisDatabase::class.java, file.absolutePath)
                // Cache only: a schema change can safely start over.
                .fallbackToDestructiveMigration()
                .build()
            return AnalysisStore(db.analysis())
        }
    }
}
