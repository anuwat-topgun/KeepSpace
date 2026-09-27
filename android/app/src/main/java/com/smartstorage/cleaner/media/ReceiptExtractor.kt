package com.smartstorage.cleaner.media

import java.math.BigDecimal

// Mirrors ios/SmartStorage/MediaEngine/ReceiptExtractor.swift.

/** Spending category guessed from the merchant and receipt text. */
enum class ReceiptCategory(val title: String) {
    FoodAndDrink("Food & Drink"), Groceries("Groceries"), Shopping("Shopping"), Transport("Transport"),
    Bills("Bills"), Health("Health"), Transfer("Transfer"), Other("Other"),
}

/** What was read off a receipt. Kept on the device only. */
data class ReceiptDetails(
    val merchant: String? = null,
    /** Epoch millis (12:00 UTC on the printed day). */
    val date: Long? = null,
    val amount: BigDecimal? = null,
    /** ISO 4217 code ("THB", "USD"). */
    val currency: String? = null,
    val category: ReceiptCategory = ReceiptCategory.Other,
) {
    /** How many of the four key fields were found; drives the "check the details" hint. */
    val completeness: Int get() = listOf(merchant != null, date != null, amount != null, currency != null).count { it }
}

/** Pulls merchant, date, total and category out of recognised receipt text. Pure, for tests. */
object ReceiptExtractor {
    private const val DAY_MS = 24L * 3600 * 1000

    fun extract(text: String, now: Long = System.currentTimeMillis()): ReceiptDetails {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lower = text.lowercase()
        return ReceiptDetails(
            merchant = merchant(lines),
            date = DateExtractor.dates(text).filter { it <= now + DAY_MS }.maxOrNull(),
            amount = total(lines),
            currency = currency(lower),
            category = category(lower),
        )
    }

    // region Merchant

    private val notMerchant = listOf(
        "receipt", "tax invoice", "invoice", "ใบเสร็จ", "ใบกำกับภาษี", "โอนเงินสำเร็จ", "transfer",
        "thank you", "welcome", "order", "boarding",
    )
    private val genericSuffixes = listOf(
        "department store", "public company limited", "company limited", "co., ltd.", "co.,ltd.", "co. ltd", "co ltd",
        "ltd.", "ltd", "limited", "inc.", "inc", "corporation", "store", "(thailand)", "thailand", "บริษัท", "จำกัด", "(มหาชน)", "สาขา",
    )
    /** Honorifics on transfer recipients ("นาย สมชาย ใจดี") don't belong in folder names. */
    private val honorifics = listOf("นางสาว", "นาย", "นาง", "น.ส.", "mr.", "mrs.", "ms.", "miss", "mr", "mrs", "ms")

    private val yearLike = Regex("""(?<!\d)(19|20|25)\d{2}(?!\d)""")

    fun merchant(lines: List<String>): String? {
        // Transfer slips name the recipient: "ไปยัง นาย สมชาย ใจดี" / "To: Somchai".
        for (line in lines) {
            for (prefix in listOf("ไปยัง", "to:", "to ")) {
                if (line.lowercase().startsWith(prefix)) {
                    val name = line.drop(prefix.length).trim(::isEdgeJunk)
                    if (name.isNotEmpty()) return name
                }
            }
        }
        return lines.take(6).firstOrNull { line ->
            val lower = line.lowercase()
            line.length >= 3 && line.any { it.isLetter() } && notMerchant.none { it in lower } &&
                amounts(line).isEmpty() && DateExtractor.dates(line).isEmpty() &&
                // A year means a date line the parser couldn't read (e.g. Thai misread as Latin).
                !yearLike.containsMatchIn(line) &&
                // Mostly digits: a misread amount ("120.0O"), not a name.
                line.count { it.isLetter() } > line.count { it.isDigit() }
        }
    }

    /** Short name used in folder and file names ("Central Department Store" → "Central"). */
    fun shortName(merchant: String): String {
        var name = merchant
        val lower = name.lowercase()
        honorifics.firstOrNull { lower.startsWith("$it ") || (it.endsWith(".") && lower.startsWith(it)) }?.let { name = name.drop(it.length) }
        for (suffix in genericSuffixes) {
            // Whole words only, so "Storehouse" keeps its "Store".
            name = name.replace(Regex("(?i)(?<!\\p{L})" + Regex.escape(suffix) + "(?!\\p{L})"), " ")
        }
        name = name.replace(Regex("\\s+"), " ").trim(::isEdgeJunk)
        if (name.isEmpty()) return merchant
        // OCR'd headers are often ALL CAPS; soften those, keep any deliberate casing as it is.
        if (name != name.uppercase()) return name
        return name.split(" ").joinToString(" ") { w -> w.take(1).uppercase() + w.drop(1).lowercase() }
    }

    // endregion

    /**
     * Whitespace and ASCII punctuation only. Thai vowel and tone marks are combining characters
     * (not "letters" to Kotlin), so a letter-based test would chop "ใจดี" to "ใจด".
     */
    private fun isEdgeJunk(c: Char): Boolean = c.isWhitespace() || (c.code < 128 && !c.isLetterOrDigit())

    // region Amount

    /** Lines that carry the amount actually paid, most specific first. "Subtotal" never counts. */
    private val totalKeywords = listOf(
        "grand total", "total due", "amount due", "amount paid", "net total", "total",
        "รวมทั้งสิ้น", "ยอดสุทธิ", "ยอดรวม", "จำนวนเงิน", "ยอดชำระ", "amount",
    )

    fun total(lines: List<String>): BigDecimal? {
        for (keyword in totalKeywords) {
            // The last matching line is usually the final total (after tax/discounts).
            for (line in lines.asReversed()) {
                val lower = line.lowercase()
                if (keyword !in lower || "subtotal" in lower || "sub total" in lower) continue
                amounts(line).lastOrNull()?.let { return it }
            }
        }
        // No labelled total: the largest amount on the receipt is the best guess.
        return lines.flatMap(::amounts).maxOrNull()
    }

    // "(?!\d|\.\d)" keeps dates like 12.09.2026 from reading as 12.09.
    private val amountRegex =
        Regex("""(?<![\d.])(?:[$€£฿]\s?)?(\d{1,3}(?:,\d{3})+|\d+)(\.\d{2})(?!\d|\.\d)|[$€£฿]\s?(\d{1,3}(?:,\d{3})+|\d+)(?![\d.])""")

    /** Money-looking numbers: "3,450.00", "$6.00", "฿1,290", "500.00 บาท". */
    fun amounts(line: String): List<BigDecimal> = amountRegex.findAll(line).mapNotNull { m ->
        val raw = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] + m.groupValues[2] else m.groupValues[3]
        raw.replace(",", "").toBigDecimalOrNull()
    }.toList()

    // endregion

    fun currency(lower: String): String? = when {
        "฿" in lower || "บาท" in lower || "thb" in lower -> "THB"
        "€" in lower || "eur" in lower -> "EUR"
        "£" in lower || "gbp" in lower -> "GBP"
        "¥" in lower || "jpy" in lower -> "JPY"
        "$" in lower || "usd" in lower -> "USD"
        else -> null
    }

    private val categoryKeywords = listOf(
        ReceiptCategory.Transfer to listOf("โอนเงินสำเร็จ", "transfer successful", "promptpay", "พร้อมเพย์"),
        ReceiptCategory.Transport to listOf("grab", "bolt", "taxi", "bts", "mrt", "airport rail", "ptt", "shell", "esso", "bangchak", "fuel", "parking", "ค่าโดยสาร"),
        ReceiptCategory.Bills to listOf("electricity", "water supply", "internet", "ais", "true move", "dtac", "3bb", "mea", "pea", "ค่าไฟ", "ค่าน้ำ"),
        ReceiptCategory.Health to listOf("pharmacy", "hospital", "clinic", "boots", "watsons", "ร้านยา", "โรงพยาบาล"),
        ReceiptCategory.Groceries to listOf("7-eleven", "7 eleven", "lotus", "big c", "makro", "tops", "villa market", "gourmet market", "supermarket", "family mart"),
        ReceiptCategory.FoodAndDrink to listOf(
            "coffee", "cafe", "café", "restaurant", "starbucks", "latte", "americano", "bar ", "food", "grabfood", "lineman", "kitchen", "กาแฟ", "ร้านอาหาร",
        ),
        ReceiptCategory.Shopping to listOf("department store", "central", "the mall", "siam paragon", "emporium", "uniqlo", "ikea", "shopee", "lazada", "fashion", "เสื้อ"),
    )

    fun category(lower: String): ReceiptCategory =
        categoryKeywords.firstOrNull { (_, words) -> words.any { it in lower } }?.first ?: ReceiptCategory.Other
}
