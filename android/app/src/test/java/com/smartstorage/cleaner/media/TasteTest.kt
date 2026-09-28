package com.smartstorage.cleaner.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

// Mirrors TasteTests in ios/SmartStorageTests/MediaEngineTests.swift.
class TasteTest {
    private fun photo(id: String, sharpness: Double, exposure: Double, face: Double? = null) = AnalyzedPhoto(
        MediaItem(id, MediaItem.Kind.Photo, 1_790_000_000_000, 1_000_000, 4032, 3024, 0, false),
        ImageFeatures(0L, sharpness, exposure, face, if (face == null) 0 else 1),
    )

    /** Standard mix picks the sharper "a"; a person who likes good lighting keeps the balanced "b". */
    private val group = listOf(photo("a", 500.0, 0.9), photo("b", 250.0, 0.5))

    private fun sum(w: ScoreWeights) = w.sharpness + w.face + w.exposure

    @Test fun standardWeightsSumToOneAndPickTheSharperPhoto() {
        assertTrue(abs(sum(ScoreWeights.Standard) - 1) < 1e-9)
        assertEquals(0, BestShotScorer().pick(group)?.index)
    }

    @Test fun choicesMoveWeightsTowardWhatWasChosen() {
        val features = BestShotScorer().features(group)
        var profile = TasteProfile()
        repeat(25) { profile = profile.learn(1, features) }
        assertEquals(25, profile.decisions)
        assertTrue(profile.learned.exposure > ScoreWeights.Standard.exposure + 0.1)
        assertTrue(profile.learned.sharpness < ScoreWeights.Standard.sharpness)
        assertTrue(abs(sum(profile.learned) - 1) < 1e-9)
        assertTrue(profile.learned.face >= 0.02) // nothing is ever ignored entirely
        // Fully trusted: the personal mix now picks the balanced photo.
        assertEquals(1, BestShotScorer(profile.effective).pick(group)?.index)
    }

    @Test fun trustGrowsGraduallyAndAFewChoicesBarelyMoveThings() {
        assertEquals(ScoreWeights.Standard, TasteProfile().effective)
        val profile = TasteProfile().learn(1, BestShotScorer().features(group))
        assertEquals(1.0 / TasteProfile.FULL_TRUST_AFTER, profile.confidence, 1e-9)
        assertTrue(abs(profile.effective.exposure - ScoreWeights.Standard.exposure) < 0.02)
        assertEquals(0, BestShotScorer(profile.effective).pick(group)?.index)
    }

    @Test fun agreeingWithTheRecommendationKeepsThePick() {
        val features = BestShotScorer().features(group)
        var profile = TasteProfile()
        repeat(25) { profile = profile.learn(0, features) }
        // Agreeing with a sharpness win says "sharpness matters to me": the pick stays, and lighting counts for less.
        assertEquals(0, BestShotScorer(profile.effective).pick(group)?.index)
        assertTrue(profile.learned.exposure <= ScoreWeights.Standard.exposure)
        assertTrue(profile.learned.sharpness >= ScoreWeights.Standard.sharpness)
    }

    @Test fun groupsWithoutFacesNeverTeachAboutFaces() {
        val features = BestShotScorer().features(group)
        assertTrue(features.all { it.face == 0.0 })
        val profile = TasteProfile().learn(1, features)
        assertTrue(abs(profile.learned.face - TasteProfile().learned.face) < 0.02)
    }

    @Test fun ignoresNonsenseChoices() {
        val features = BestShotScorer().features(group)
        assertEquals(0, TasteProfile().learn(5, features).decisions)
        assertEquals(0, TasteProfile().learn(0, listOf(ScoreFeatures(1.0, 0.0, 1.0))).decisions)
    }
}
