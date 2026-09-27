package com.smartstorage.cleaner.media

/**
 * Pure image metrics over 8-bit luminance arrays (row-major), so they run in JVM unit tests.
 * Bitmap decoding lives in [ImageAnalyzer].
 */
object ImageMetrics {
    const val MEASURE_SIDE = 256

    /** Variance of the 4-neighbour Laplacian — the classic blur metric. Higher = sharper. */
    fun laplacianVariance(luma: IntArray, side: Int): Double {
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        for (y in 1 until side - 1) {
            for (x in 1 until side - 1) {
                val i = y * side + x
                val v = (luma[i - 1] + luma[i + 1] + luma[i - side] + luma[i + side] - 4 * luma[i]).toDouble()
                sum += v
                sumSquares += v * v
                count++
            }
        }
        val mean = sum / count
        return sumSquares / count - mean * mean
    }

    /** Mean luminance 0..1. */
    fun exposure(luma: IntArray): Double = luma.average() / 255.0

    /**
     * Difference hash from a 9×8 luminance grid: bit = left pixel brighter than its right neighbour.
     * Robust to small shifts, exposure changes and recompression — ideal for burst shots.
     */
    fun dHash(luma9x8: IntArray): Long {
        require(luma9x8.size == 72) { "dHash needs a 9x8 grid" }
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (luma9x8[y * 9 + x] > luma9x8[y * 9 + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** ITU-R BT.601 luma from an ARGB pixel. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}
