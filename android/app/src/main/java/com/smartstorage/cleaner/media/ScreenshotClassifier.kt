package com.smartstorage.cleaner.media

import java.time.LocalDate
import java.time.ZoneOffset

// Mirrors ios/SmartStorage/MediaEngine/ScreenshotClassifier.swift.

/** What a screenshot is about, from on-device OCR + barcode detection. */
enum class ScreenshotKind(val title: String) {
    Shopping("Shopping"), Receipts("Receipts"), Chats("Chats"), QrCodes("QR Codes"), Tickets("Tickets"), Other("Other");

    /** Documents people may need later are never suggested for cleanup just for being old. */
    val isImportant: Boolean get() = this == Receipts || this == Tickets
}

/** The only things kept from a screenshot's text. The text itself is never stored. */
data class ScreenshotInfo(
    val kind: ScreenshotKind,
    /** For tickets: the latest date printed on it (travel / event day), epoch millis. */
    val eventDate: Long? = null,
    /** For travel tickets: "BKK → TYO". */
    val route: String? = null,
) {
    fun isExpired(now: Long): Boolean = kind == ScreenshotKind.Tickets && eventDate != null && eventDate < now - DAY_MS

    private companion object {
        const val DAY_MS = 24L * 3600 * 1000
    }
}

/** Keyword classifier over recognised text (English + Thai). Pure, so it is unit-tested. */
object ScreenshotClassifier {
    private val keywords = mapOf(
        ScreenshotKind.Tickets to listOf(
            "boarding pass", "boarding", "gate", "seat", "flight", "pnr", "e-ticket", "eticket", "ticket", "admit one",
            "departure", "arrival", "passenger", "check-in", "บัตรโดยสาร", "ที่นั่ง", "เที่ยวบิน", "ตั๋ว", "ประตูขึ้นเครื่อง",
        ),
        ScreenshotKind.Receipts to listOf(
            "receipt", "subtotal", "total", "tax", "vat", "invoice", "amount paid", "paid", "change due", "cashier",
            "payment", "transaction", "transfer successful", "ใบเสร็จ", "รวมทั้งสิ้น", "ยอดรวม", "ภาษี", "ชำระเงิน",
            "โอนเงินสำเร็จ", "จำนวนเงิน",
        ),
        ScreenshotKind.Shopping to listOf(
            "add to cart", "add to bag", "buy now", "checkout", "free shipping", "in stock", "sold", "reviews",
            "wishlist", "shopee", "lazada", "amazon", "discount", "sale", "ตะกร้า", "ซื้อเลย", "ส่งฟรี", "ลดราคา", "ขายแล้ว",
        ),
        ScreenshotKind.Chats to listOf(
            "typing", "delivered", "seen", "message", "reply", "online", "last seen", "whatsapp", "messenger", "line",
            "imessage", "ส่งข้อความ", "อ่านแล้ว", "พิมพ์ข้อความ", "ตอบกลับ",
        ),
    )

    /** Thai bank-transfer slips ("โอนเงินสำเร็จ") are receipts even though they always carry a QR code. */
    private val strong = setOf(
        "boarding pass", "e-ticket", "receipt", "invoice", "add to cart", "buy now",
        "transfer successful", "บัตรโดยสาร", "ใบเสร็จ", "ตะกร้า", "โอนเงินสำเร็จ",
    )

    /** Lower index wins ties. */
    private val priority = listOf(
        ScreenshotKind.Tickets, ScreenshotKind.Receipts, ScreenshotKind.QrCodes,
        ScreenshotKind.Shopping, ScreenshotKind.Chats, ScreenshotKind.Other,
    )

    private val priceRegex = Regex("""(?:[$€£฿]\s?\d[\d,]*(?:\.\d{2})?|\d[\d,]*\.\d{2}\s?(?:thb|usd|บาท))""")
    private val clockRegex = Regex("""\b\d{1,2}:\d{2}\b""")
    private val amountRegex = Regex("""\b\d[\d,]*\.\d{2}\b""")
    private val routeRegex = Regex("""\b([A-Z]{3})\s*(?:→|->|–|-|to|✈)\s*([A-Z]{3})\b""")

    fun classify(text: String, hasQrCode: Boolean, now: Long = System.currentTimeMillis()): ScreenshotInfo {
        val lower = text.lowercase()
        val scores = mutableMapOf<ScreenshotKind, Double>()
        for ((kind, words) in keywords) {
            for (word in words) if (contains(lower, word)) scores.merge(kind, if (word in strong) 2.0 else 1.0, Double::plus)
        }
        // Prices with a currency push towards receipts/shopping; many chat-style timestamps towards chats.
        if (priceRegex.findAll(lower).count() >= 3) {
            scores.merge(ScreenshotKind.Receipts, 1.0, Double::plus)
            scores.merge(ScreenshotKind.Shopping, 0.5, Double::plus)
        }
        // Receipts list several amounts even when the words aren't readable (Thai text here, since the
        // bundled OCR model is Latin-only). Keeps such receipts out of "Old Screenshots".
        if (amountRegex.findAll(lower).count() >= 3) scores.merge(ScreenshotKind.Receipts, 2.0, Double::plus)
        if (clockRegex.findAll(lower).count() >= 4) scores.merge(ScreenshotKind.Chats, 1.5, Double::plus)
        if (hasQrCode) scores.merge(ScreenshotKind.QrCodes, 2.0, Double::plus)

        // Tickets and receipts often carry a QR code too; they win when their own evidence is clear.
        val best = scores.entries.maxWithOrNull(compareBy<Map.Entry<ScreenshotKind, Double>> { it.value }.thenByDescending { priority.indexOf(it.key) })
        if (best == null || best.value < 2) return ScreenshotInfo(if (hasQrCode) ScreenshotKind.QrCodes else ScreenshotKind.Other)
        if (best.key != ScreenshotKind.Tickets) return ScreenshotInfo(best.key)
        return ScreenshotInfo(ScreenshotKind.Tickets, DateExtractor.dates(text).maxOrNull(), route(text))
    }

    /** Whole-word match for Latin keywords (so "line" doesn't match "online"); substring for Thai. */
    private fun contains(text: String, word: String): Boolean {
        if (word.any { it.code > 127 }) return word in text
        return Regex("(?<![a-z])" + Regex.escape(word) + "(?![a-z])").containsMatchIn(text)
    }

    /** "BKK → TYO", "BKK - NRT", "BKK to HND". */
    fun route(text: String): String? = routeRegex.find(text)?.let { "${it.groupValues[1]} → ${it.groupValues[2]}" }
}

/** Finds calendar dates in OCR text: 2026-09-12, 12/09/2026, 12 SEP 2026, Sep 12, 2026, 12SEP26. */
object DateExtractor {
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthAlternation = months.joinToString("|")
    private val iso = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val numeric = Regex("""\b(\d{1,2})[/.](\d{1,2})[/.](\d{2,4})\b""")
    private val dayMonthYear = Regex("""\b(\d{1,2})\s?($monthAlternation)[a-z]*\.?,?\s?(\d{2,4})\b""")
    private val monthDayYear = Regex("""\b($monthAlternation)[a-z]*\.?\s(\d{1,2}),?\s(\d{4})\b""")

    /** Epoch millis at 12:00 UTC on each date found. */
    fun dates(text: String): List<Long> {
        val lower = text.lowercase()
        val found = mutableListOf<Long>()
        fun add(day: Int, month: Int, year: Int) {
            var y = if (year < 100) 2000 + year else year
            if (y > 2400) y -= 543 // Thai Buddhist-era years
            if (month !in 1..12 || day !in 1..31 || y !in 2000..2100) return
            runCatching { LocalDate.of(y, month, day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }.onSuccess { found += it }
        }
        iso.findAll(lower).forEach { m -> val (y, mo, d) = m.destructured; add(d.toInt(), mo.toInt(), y.toInt()) }
        // Day first (Thailand/UK); swap when the second number can't be a month.
        numeric.findAll(lower).forEach { m ->
            val (a, b, y) = m.destructured
            if (b.toInt() > 12) add(b.toInt(), a.toInt(), y.toInt()) else add(a.toInt(), b.toInt(), y.toInt())
        }
        dayMonthYear.findAll(lower).forEach { m -> val (d, mo, y) = m.destructured; add(d.toInt(), months.indexOf(mo) + 1, y.toInt()) }
        monthDayYear.findAll(lower).forEach { m -> val (mo, d, y) = m.destructured; add(d.toInt(), months.indexOf(mo) + 1, y.toInt()) }
        return found
    }
}
