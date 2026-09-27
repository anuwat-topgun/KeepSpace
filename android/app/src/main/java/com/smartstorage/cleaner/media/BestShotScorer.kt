package com.smartstorage.cleaner.media

import kotlin.math.abs

/**
 * Picks the keeper in a group of similar photos and explains why. Mirrors `BestShotScorer.swift`.
 *
 * Sharpness and face quality are scored relative to the best in the group (value / max), so a 2%
 * sharpness edge stays a 2% edge. Only reasons that are actually true for the pick are reported.
 */
data class BestShotScorer(
    val sharpnessWeight: Double = 0.45,
    val faceWeight: Double = 0.35,
    val exposureWeight: Double = 0.20,
) {
    enum class Reason { Sharpest, BestFaces, BestExposure, Favorite }

    data class Pick(val index: Int, val reasons: List<Reason>)

    fun pick(photos: List<AnalyzedPhoto>): Pick? {
        if (photos.isEmpty()) return null
        // A photo the user already favourited always wins; respect explicit intent.
        photos.indexOfFirst { it.item.isFavorite }.takeIf { it >= 0 }?.let { return Pick(it, listOf(Reason.Favorite)) }

        val sharpness = relativeToBest(photos.map { it.features.sharpness })
        val hasFaces = photos.any { it.features.faceQuality != null }
        val faces = relativeToBest(photos.map { it.features.faceQuality ?: 0.0 })
        // Exposure: 1 at mid-grey, 0 at pure black/white.
        val exposure = photos.map { 1 - abs(it.features.exposure - 0.5) * 2 }
        val bestExposure = exposure.max()

        val scores = photos.indices.map { i ->
            sharpnessWeight * sharpness[i] + (if (hasFaces) faceWeight * faces[i] else 0.0) + exposureWeight * exposure[i]
        }
        val best = scores.indices.maxBy { scores[it] }

        val reasons = buildList {
            if (sharpness[best] >= 0.999) add(Reason.Sharpest)
            if (hasFaces && faces[best] >= 0.999) add(Reason.BestFaces)
            if (exposure[best] >= bestExposure - 0.001) add(Reason.BestExposure)
            if (isEmpty()) add(if (sharpness[best] >= exposure[best]) Reason.Sharpest else Reason.BestExposure)
        }
        return Pick(best, reasons)
    }

    /** Scale to 0..1 relative to the group's best; all-zero inputs map to 1 so nobody is penalised. */
    private fun relativeToBest(values: List<Double>): List<Double> {
        val hi = values.max()
        return if (hi <= 0) values.map { 1.0 } else values.map { maxOf(it, 0.0) / hi }
    }
}
