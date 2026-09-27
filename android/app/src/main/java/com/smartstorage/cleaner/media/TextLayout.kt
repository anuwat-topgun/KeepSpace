package com.smartstorage.cleaner.media

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Mirrors ios/SmartStorage/MediaEngine/TextLayout.swift.

/**
 * Rebuilds printed rows from recognised text fragments. Receipts print labels and amounts in
 * columns ("TOTAL ........ 456.00"), and OCR often returns each column separately, so the amount
 * loses its label. Photos are also rarely straight, so rows are found along the dominant text
 * angle rather than the image axis. Pure, for tests.
 */
object TextLayout {
    data class Point(val x: Double, val y: Double)

    /** One recognised line with its corners in pixels, y pointing down. */
    data class Fragment(val text: String, val topLeft: Point, val topRight: Point, val bottomRight: Point, val bottomLeft: Point)

    private class Placed(val text: String, val x: Double, val y: Double, val height: Double)

    fun rows(fragments: List<Fragment>): List<String> {
        if (fragments.isEmpty()) return emptyList()
        // Dominant skew from the widest lines: OCR engines report short fragments ("456.00") as
        // axis-aligned boxes, so only long lines show the page's real tilt.
        fun width(f: Fragment) = hypot(f.topRight.x - f.topLeft.x, f.topRight.y - f.topLeft.y)
        val widest = fragments.maxOf(::width)
        val angles = fragments.filter { width(it) >= widest * 0.5 }.map { atan2(it.topRight.y - it.topLeft.y, it.topRight.x - it.topLeft.x) }.sorted()
        val angle = angles[angles.size / 2]
        val sine = sin(angle)
        val cosine = cos(angle)
        val placed = fragments.map { f ->
            val cx = (f.topLeft.x + f.topRight.x + f.bottomRight.x + f.bottomLeft.x) / 4
            val cy = (f.topLeft.y + f.topRight.y + f.bottomRight.y + f.bottomLeft.y) / 4
            // Rotate into the text's own frame so a row has one y.
            Placed(f.text, cosine * cx + sine * cy, -sine * cx + cosine * cy, hypot(f.bottomLeft.x - f.topLeft.x, f.bottomLeft.y - f.topLeft.y))
        }.sortedBy { it.y }

        val rows = mutableListOf<MutableList<Placed>>()
        for (item in placed) {
            val last = rows.lastOrNull()
            if (last != null) {
                val rowY = last.sumOf { it.y } / last.size
                val rowHeight = last.maxOf { it.height }
                if (abs(item.y - rowY) <= maxOf(item.height, rowHeight) * 0.5) {
                    last += item
                    continue
                }
            }
            rows += mutableListOf(item)
        }
        return rows.map { row -> row.sortedBy { it.x }.joinToString("  ") { it.text } }
    }
}
