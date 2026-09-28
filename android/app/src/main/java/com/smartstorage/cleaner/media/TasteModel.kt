package com.smartstorage.cleaner.media

import kotlin.math.exp

// Mirrors ios/SmartStorage/MediaEngine/TasteModel.swift.

/**
 * What Best Shot has learned about a person's taste, from the photos they chose to keep.
 *
 * Every time someone keeps one photo out of a group, the chosen photo "beats" each of the others.
 * A tiny pairwise logistic model nudges the three weights toward whatever made the chosen photo stand
 * out (Bradley–Terry with a pull back to the standard mix). Only the weights and a count are kept —
 * never photos, ids, or what was in them — and it is trusted gradually, so a handful of choices can't
 * wreck the recommendations. Pure, for tests.
 */
data class TasteProfile(val learned: ScoreWeights = ScoreWeights.Standard, val decisions: Int = 0) {
    /** 0 (no choices yet) … 1 (fully trusted). */
    val confidence: Double get() = minOf(1.0, decisions.toDouble() / FULL_TRUST_AFTER)

    /** The mix Best Shot uses: the standard one, moving toward the learned one as trust grows. */
    val effective: ScoreWeights
        get() {
            val c = confidence
            val s = ScoreWeights.Standard
            return ScoreWeights(
                s.sharpness + (learned.sharpness - s.sharpness) * c,
                s.face + (learned.face - s.face) * c,
                s.exposure + (learned.exposure - s.exposure) * c,
            )
        }

    /** Learns from one decision: the photo at [chosen] was kept out of the group described by [features]. */
    fun learn(chosen: Int, among: List<ScoreFeatures>): TasteProfile {
        if (among.size < 2 || chosen !in among.indices) return this
        val w = doubleArrayOf(learned.sharpness, learned.face, learned.exposure)
        val winner = among[chosen]
        val step = LEARNING_RATE / (among.size - 1) // groups of any size weigh the same
        among.forEachIndexed { index, other ->
            if (index == chosen) return@forEachIndexed
            val d = doubleArrayOf(winner.sharpness - other.sharpness, winner.face - other.face, winner.exposure - other.exposure)
            val z = STEEPNESS * (w[0] * d[0] + w[1] * d[1] + w[2] * d[2])
            val miss = 1 - 1 / (1 + exp(-z)) // how surprised the model was
            for (k in w.indices) w[k] += step * miss * d[k]
        }
        val standard = doubleArrayOf(ScoreWeights.Standard.sharpness, ScoreWeights.Standard.face, ScoreWeights.Standard.exposure)
        for (k in w.indices) w[k] = maxOf(FLOOR, w[k] + PULL_TO_STANDARD * (standard[k] - w[k]))
        val total = w.sum()
        return TasteProfile(ScoreWeights(w[0] / total, w[1] / total, w[2] / total), decisions + 1)
    }

    companion object {
        /** Choices after which the learned mix is used in full. */
        const val FULL_TRUST_AFTER = 20
        /** How sharply a score gap turns into a preference; tuned so a 0.5 gap in one feature is decisive. */
        private const val STEEPNESS = 6.0
        private const val LEARNING_RATE = 0.15
        /** Pull toward the standard mix on every step, so weights can't run away. */
        private const val PULL_TO_STANDARD = 0.02
        /** No feature is ever ignored completely. */
        private const val FLOOR = 0.03
    }
}
