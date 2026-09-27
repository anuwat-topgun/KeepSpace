package com.smartstorage.cleaner.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Mirrors DuplicateFinderTests in ios/SmartStorageTests/MediaEngineTests.swift.
class DuplicateFinderTest {
    private fun item(id: String, kind: MediaItem.Kind = MediaItem.Kind.Photo, bytes: Long = 2_000_000, day: Long = 1, favorite: Boolean = false) =
        MediaItem(id, kind, day * 86_400_000, bytes, 4032, 3024, if (kind == MediaItem.Kind.Video) 10_000 else 0, favorite)

    @Test fun onlySameSizeSameKindFilesAreHashed() {
        val items = listOf(item("a"), item("b"), item("c", bytes = 3_000_000), item("v", MediaItem.Kind.Video), item("z", bytes = 0), item("y", bytes = 0))
        assertEquals(setOf("a", "b"), DuplicateFinder.candidates(items).map { it.id }.toSet())
    }

    @Test fun keepsFavouriteElseOldest() {
        val items = listOf(item("new", day = 5), item("old", day = 1), item("fav", day = 9, favorite = true), item("other", day = 2))
        val groups = DuplicateFinder.groups(items, mapOf("new" to "h1", "old" to "h1", "fav" to "h1", "other" to "h2"))
        assertEquals(1, groups.size)
        assertEquals("fav", groups.single().keeper.id)
        assertEquals(listOf("old", "new"), groups.single().copies.map { it.id })
        assertEquals("old", DuplicateFinder.groups(listOf(item("new", day = 5), item("old", day = 1)), mapOf("new" to "h", "old" to "h")).single().keeper.id)
    }

    @Test fun hashPlanReusesUntilEdited() {
        val a = item("a")
        val edited = item("b").copy(modifiedAt = 99)
        val cached = mapOf(
            "a" to CachedHash("a", a.modifiedAt, a.bytes, "h"),
            "b" to CachedHash("b", 0, edited.bytes, "h"),
            "gone" to CachedHash("gone", 0, 1, "x"),
        )
        val plan = CachePlanner.planHashes(listOf(a, edited), cached)
        assertEquals(mapOf("a" to "h"), plan.hits)
        assertEquals(listOf("b"), plan.toHash.map { it.id })
        assertEquals(listOf("gone"), plan.staleIds)
    }

    @Test fun copiesComeFirstAndAreNotCountedTwice() {
        val original = item("orig", MediaItem.Kind.Screenshot, day = 1)
        val copy = item("copy", MediaItem.Kind.Screenshot, day = 2)
        val content = LibraryReportBuilder().build(listOf(original, copy), emptyList(), 100, 50, now = 400 * 86_400_000L,
            fileHashes = mapOf("orig" to "h", "copy" to "h"))
        assertEquals("Exact Duplicates", content.cleanupCandidates.first().title)
        assertEquals(2_000_000L, content.cleanupCandidates.first().bytes)
        assertEquals(listOf("copy"), content.reviewSets[ReviewKind.Duplicates]?.map { it.id })
        assertTrue(content.reviewSets[ReviewKind.Duplicates]!!.single().preselected)
        assertEquals(listOf("orig"), content.reviewSets[ReviewKind.OldScreenshots]?.map { it.id })
    }
}
