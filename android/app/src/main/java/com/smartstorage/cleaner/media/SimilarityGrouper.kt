package com.smartstorage.cleaner.media

/**
 * Groups photos taken close together in time that also look alike. Mirrors the iOS grouper, with
 * a perceptual-hash distance (bits of 64) in place of Vision feature prints.
 *
 * Photos are walked in capture order; a photo joins the current group when it was taken within
 * [maxGapMs] of the previous photo *and* its hash is within [maxDistance] bits of any member.
 */
data class SimilarityGrouper(
    val maxGapMs: Long = 120_000,
    val maxDistance: Int = 12,
    val minGroupSize: Int = 2,
) {
    fun groups(photos: List<AnalyzedPhoto>): List<List<AnalyzedPhoto>> {
        val result = mutableListOf<List<AnalyzedPhoto>>()
        var current = mutableListOf<AnalyzedPhoto>()
        for (photo in photos.sortedBy { it.item.createdAt }) {
            val last = current.lastOrNull()
            val joins = last != null &&
                photo.item.createdAt - last.item.createdAt <= maxGapMs &&
                current.any { it.features.distanceTo(photo.features) <= maxDistance }
            if (joins) {
                current.add(photo)
            } else {
                if (current.size >= minGroupSize) result.add(current)
                current = mutableListOf(photo)
            }
        }
        if (current.size >= minGroupSize) result.add(current)
        return result
    }
}
