package com.smartstorage.cleaner.media

import kotlin.math.abs

/**
 * How much Best Shot cares about each thing. Sums to 1. The standard mix is tuned by hand; a person's
 * own mix is learned on the device from the photos they choose to keep ([TasteProfile]).
 */
data class ScoreWeights(val sharpness: Double, val face: Double, val exposure: Double) {
    companion object {
        val Standard = ScoreWeights(sharpness = 0.45, face = 0.35, exposure = 0.20)
    }
}

/**
 * One photo's standing inside its group, each 0..1: sharpness and faces relative to the group's best,
 * exposure absolute (1 at mid-grey). This is what a choice is learned from — no pixels, no ids.
 */
data class ScoreFeatures(val sharpness: Double, val face: Double, val exposure: Double)

/**
 * Picks the keeper in a group of similar photos and explains why. Mirrors `BestShotScorer.swift`.
 *
 * Sharpness and face quality are scored relative to the best in the group (value / max), so a 2%
 * sharpness edge stays a 2% edge. Only reasons that are actually true for the pick are reported.
 */
data class BestShotScorer(
    val sharpnessWeight: Double = ScoreWeights.Standard.sharpness,
    val faceWeight: Double = ScoreWeights.Standard.face,
    val exposureWeight: Double = ScoreWeights.Standard.exposure,
) {
    constructor(weights: ScoreWeights) : this(weights.sharpness, weights.face, weights.exposure)

    enum class Reason { Sharpest, BestFaces, BestExposure, Favorite }

    data class Pick(val index: Int, val reasons: List<Reason>)

    /** Each photo's relative standing; faces count only when someone in the group has one. */
    fun features(photos: List<AnalyzedPhoto>): List<ScoreFeatures> {
        if (photos.isEmpty()) return emptyList()
        val sharpness = relativeToBest(photos.map { it.features.sharpness })
        val hasFaces = photos.any { it.features.faceQuality != null }
        val faces = if (hasFaces) relativeToBest(photos.map { it.features.faceQuality ?: 0.0 }) else photos.map { 0.0 }
        return photos.indices.map { i ->
            // Exposure: 1 at mid-grey, 0 at pure black/white.
            ScoreFeatures(sharpness[i], faces[i], 1 - abs(photos[i].features.exposure - 0.5) * 2)
        }
    }

    fun pick(photos: List<AnalyzedPhoto>): Pick? {
        if (photos.isEmpty()) return null
        // A photo the user already favourited always wins; respect explicit intent.
        photos.indexOfFirst { it.item.isFavorite }.takeIf { it >= 0 }?.let { return Pick(it, listOf(Reason.Favorite)) }

        val scored = features(photos)
        val scores = scored.map { sharpnessWeight * it.sharpness + faceWeight * it.face + exposureWeight * it.exposure }
        val best = scores.indices.maxBy { scores[it] }

        val bestExposure = scored.maxOf { it.exposure }
        val hasFaces = photos.any { it.features.faceQuality != null }
        val reasons = buildList {
            if (scored[best].sharpness >= 0.999) add(Reason.Sharpest)
            if (hasFaces && scored[best].face >= 0.999) add(Reason.BestFaces)
            if (scored[best].exposure >= bestExposure - 0.001) add(Reason.BestExposure)
            if (isEmpty()) add(if (scored[best].sharpness >= scored[best].exposure) Reason.Sharpest else Reason.BestExposure)
        }
        return Pick(best, reasons)
    }

    /** Scale to 0..1 relative to the group's best; all-zero inputs map to 1 so nobody is penalised. */
    private fun relativeToBest(values: List<Double>): List<Double> {
        val hi = values.max()
        return if (hi <= 0) values.map { 1.0 } else values.map { maxOf(it, 0.0) / hi }
    }
}
