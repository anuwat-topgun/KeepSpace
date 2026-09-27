package com.smartstorage.cleaner.media

// Mirrors ios/SmartStorage/MediaEngine/DuplicateFinder.swift.

/**
 * Finds byte-for-byte identical files. Only files that share an exact size with another file of the
 * same kind are hashed, so a scan never reads the whole library. Pure, for tests.
 */
object DuplicateFinder {
    data class Group(
        /** The copy that stays: a favourite, else the oldest. */
        val keeper: MediaItem,
        /** The other copies, safe to remove. */
        val copies: List<MediaItem>,
    ) {
        val reclaimableBytes: Long get() = copies.sumOf { it.bytes }
    }

    /** Files worth hashing: same size and same kind (still vs. video) as at least one other file. */
    fun candidates(items: List<MediaItem>): List<MediaItem> =
        items.filter { it.bytes > 0 }.groupBy { it.bytes to it.isVideo }.values.filter { it.size > 1 }.flatten()

    /** Groups of identical files from their content hashes (items without a hash are ignored). */
    fun groups(items: List<MediaItem>, hashes: Map<String, String>): List<Group> =
        items.mapNotNull { item -> hashes[item.id]?.let { "${item.bytes}:$it" to item } }
            .groupBy({ it.first }, { it.second })
            .values.filter { it.size > 1 }
            .map { members ->
                val sorted = members.sortedWith(compareByDescending<MediaItem> { it.isFavorite }.thenBy { it.createdAt }.thenBy { it.id })
                Group(sorted.first(), sorted.drop(1))
            }
            .sortedWith(compareByDescending<Group> { it.reclaimableBytes }.thenBy { it.keeper.id })
}
