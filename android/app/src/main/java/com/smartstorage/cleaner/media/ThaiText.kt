package com.smartstorage.cleaner.media

// Mirrors ios/SmartStorage/MediaEngine/ThaiText.swift.

/** Thai-aware text helpers for OCR output. Pure, for tests. */
object ThaiText {
    private val months = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")

    /** "กุย." / "ก ย." / "ก,ย." → "ก.ย.", only between a day and a year so ordinary words are never touched. */
    private val monthRepairs = months.map { month ->
        val parts = month.trimEnd('.').split('.')
        Regex("""(?<=\d\s{0,2})""" + parts.joinToString("""\s?[.ุ,]?\s?""") { Regex.escape(it) } + """[.,]?(?=\s*\d)""") to month
    }

    /**
     * Repairs what OCR engines (Tesseract in particular) get systematically wrong in Thai: sara am
     * written as nikhahit + sara aa, and the dots of month abbreviations read as marks.
     */
    fun normalize(text: String): String {
        var out = text.replace("ํา", "ำ")
        for ((regex, month) in monthRepairs) out = regex.replace(out, Regex.escapeReplacement(month))
        return out
    }

    /** Lower-cased with Thai tone marks removed: OCR often drops them ("ตะกรา" for "ตะกร้า"). For matching only. */
    fun fold(text: String): String = text.lowercase().filterNot { it in '่'..'๋' }

    private val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')

    /**
     * True when Latin-only OCR has read Thai script as Latin gibberish ("SNuNNuWUNuau",
     * "Tauâuanỗ", "wndng"), so a Thai-capable reader should take another look.
     */
    fun looksLikeMisreadThai(text: String): Boolean {
        val words = text.split(Regex("\\s+")).map { w -> w.filter { it.isLetter() } }.filter { it.length >= 2 }
        if (words.isEmpty()) return false
        val suspicious = words.count { w ->
            val accented = w.any { it.code in 0x00C0..0x024F || it.code in 0x1E00..0x1EFF }
            val caseJumps = w.zipWithNext().count { (a, b) -> a.isLowerCase() && b.isUpperCase() }
            val noVowels = w.length >= 4 && w.any { it.isLowerCase() } && w.none { it.lowercaseChar() in vowels }
            accented || caseJumps >= 2 || noVowels
        }
        return suspicious >= 2 || (suspicious >= 1 && suspicious * 5 >= words.size)
    }
}
