package com.smartstorage.cleaner.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Mirrors SafetyScoreTests in ios/SmartStorageTests/MediaEngineTests.swift.
class SafetyScoreTest {
    @Test fun levelsByReviewKind() {
        assertEquals(SafetyLevel.VerySafe, ReviewKind.Duplicates.safety)
        assertEquals(SafetyLevel.Safe, ReviewKind.Similar.safety)
        assertEquals(SafetyLevel.ReviewFirst, ReviewKind.Blurry.safety)
        assertEquals(SafetyLevel.ReviewFirst, ReviewKind.LargeVideos.safety)
        assertEquals(SafetyLevel.Safe, ReviewKind.Expired.safety)
    }

    @Test fun scoreIsSizeWeighted() {
        assertEquals(100, SafetyLevel.score(emptyList()))
        assertEquals(100, SafetyLevel.score(listOf(SafetyLevel.VerySafe to 1_000L)))
        // 900 MB very safe + 100 MB review-first = (900*100 + 100*60) / 1000 = 96.
        assertEquals(96, SafetyLevel.score(listOf(SafetyLevel.VerySafe to 900L, SafetyLevel.ReviewFirst to 100L)))
        assertEquals(60, SafetyLevel.score(listOf(SafetyLevel.ReviewFirst to 500L)))
    }

    @Test fun planIsOrderedSafestFirstAndScored() {
        val day = 86_400_000L
        fun shot(id: String, d: Long) = MediaItem(id, MediaItem.Kind.Screenshot, d * day, 1_000_000, 100, 100, 0, false)
        val items = listOf(shot("a", 1), shot("a2", 2), shot("b", 3), shot("c", 4), shot("d", 5))
        val content = LibraryReportBuilder().build(items, emptyList(), 100, 50, now = 400 * day, fileHashes = mapOf("a" to "h", "a2" to "h"))
        assertEquals("Exact Duplicates", content.cleanupCandidates.first().title)
        val plan = content.plan(null)
        assertTrue(plan.lowestSafety <= SafetyLevel.Safe)
        assertTrue(plan.safetyScore in 60..100)
    }
}
