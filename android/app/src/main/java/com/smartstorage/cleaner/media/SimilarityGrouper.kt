package com.smartstorage.cleaner.media

/**
 * Groups photos taken close together in time that also look alike. Mirrors the iOS grouper, with
 * a perceptual-hash distance (bits of 64) in place of Vision feature prints.
 *
 * Photos are walked in capture order; a photo joins the current group when it was taken within
 * [maxGapMs] of the previous photo, has the same orientation, and its hash is within [maxDistance]
 * bits of every member. Comparing every member prevents similarity from drifting through a
 * sequence where only neighbouring photos look alike.
 */
data class SimilarityGrouper(
    val maxGapMs: Long = 60_000,
    val maxDistance: Int = 8,
    val minGroupSize: Int = 2,
) {
    fun groups(photos: List<AnalyzedPhoto>): List<List<AnalyzedPhoto>> {
        val result = mutableListOf<List<AnalyzedPhoto>>()
        var current = mutableListOf<AnalyzedPhoto>()
        for (photo in photos.sortedBy { it.item.createdAt }) {
            val last = current.lastOrNull()
            val joins = last != null &&
                photo.item.createdAt - last.item.createdAt <= maxGapMs &&
                current.all { sameOrientation(it.item, photo.item) } &&
                current.all { it.features.distanceTo(photo.features) <= maxDistance }
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

    /** Unknown dimensions do not block a valid visual match. */
    private fun sameOrientation(first: MediaItem, second: MediaItem): Boolean {
        val firstOrientation = orientation(first) ?: return true
        val secondOrientation = orientation(second) ?: return true
        return firstOrientation == secondOrientation
    }

    private fun orientation(item: MediaItem): Int? {
        if (item.width <= 0 || item.height <= 0) return null
        return item.width.compareTo(item.height)
    }
}
