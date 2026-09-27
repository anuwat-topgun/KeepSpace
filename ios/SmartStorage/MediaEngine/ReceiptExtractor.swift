import Foundation

/// Spending category guessed from the merchant and receipt text.
enum ReceiptCategory: String, CaseIterable, Hashable, Sendable {
    case foodAndDrink, groceries, shopping, transport, bills, health, transfer, other

    var title: String {
        switch self {
        case .foodAndDrink: "Food & Drink"
        case .groceries: "Groceries"
        case .shopping: "Shopping"
        case .transport: "Transport"
        case .bills: "Bills"
        case .health: "Health"
        case .transfer: "Transfer"
        case .other: "Other"
        }
    }
}

/// What was read off a receipt. Kept on the device only.
struct ReceiptDetails: Sendable, Equatable, Hashable {
    var merchant: String?
    var date: Date?
    var amount: Decimal?
    /// ISO 4217 code ("THB", "USD").
    var currency: String?
    var category: ReceiptCategory = .other

    /// How many of the four key fields were found; drives the "needs a check" hint.
    var completeness: Int { [merchant != nil, date != nil, amount != nil, currency != nil].filter { $0 }.count }
}

/// Pulls merchant, date, total and category out of recognised receipt text. Pure, for tests.
enum ReceiptExtractor {
    static func extract(from text: String, now: Date = .now) -> ReceiptDetails {
        let lines = text.split(whereSeparator: \.isNewline).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let lower = text.lowercased()
        return ReceiptDetails(
            merchant: merchant(in: lines),
            date: DateExtractor.dates(in: text, now: now).filter { $0 <= now.addingTimeInterval(24 * 3600) }.max(),
            amount: total(in: lines),
            currency: currency(in: lower),
            category: category(of: lower)
        )
    }

    // MARK: Merchant

    /// Header words that are never the merchant.
    private static let notMerchant = ["receipt", "tax invoice", "invoice", "ใบเสร็จ", "ใบกำกับภาษี", "โอนเงินสำเร็จ", "transfer",
                                      "thank you", "welcome", "order", "boarding"]
    /// Generic suffixes stripped for a short, folder-friendly name ("Central Department Store" → "Central").
    private static let genericSuffixes = ["department store", "public company limited", "company limited", "co., ltd.", "co.,ltd.",
                                          "co. ltd", "co ltd", "ltd.", "ltd", "limited", "inc.", "inc", "corporation", "store",
                                          "(thailand)", "thailand", "บริษัท", "จำกัด", "(มหาชน)", "สาขา"]

    static func merchant(in lines: [String]) -> String? {
        // Transfer slips name the recipient: "ไปยัง นาย สมชาย ใจดี" / "To: Somchai".
        for line in lines {
            for prefix in ["ไปยัง", "to:", "to "] where line.lowercased().hasPrefix(prefix) {
                let name = line.dropFirst(prefix.count).trimmingCharacters(in: .whitespaces.union(.punctuationCharacters))
                if !name.isEmpty { return name }
            }
        }
        for line in lines.prefix(6) {
            let lower = line.lowercased()
            guard line.count >= 3, line.rangeOfCharacter(from: .letters) != nil,
                  !notMerchant.contains(where: { lower.contains($0) }),
                  amounts(in: line).isEmpty, DateExtractor.dates(in: line).isEmpty,
                  // A year means a date line the parser couldn't read (e.g. Thai misread as Latin).
                  line.range(of: #"(?<!\d)(19|20|25)\d{2}(?!\d)"#, options: .regularExpression) == nil,
                  // Mostly digits: a misread amount ("120.0O"), not a name.
                  line.filter(\.isLetter).count > line.filter(\.isNumber).count
            else { continue }
            return line
        }
        return nil
    }

    /// Short name used in folder and file names.
    /// Honorifics on transfer recipients ("นาย สมชาย ใจดี") don't belong in folder names.
    private static let honorifics = ["นางสาว", "นาย", "นาง", "น.ส.", "mr.", "mrs.", "ms.", "miss", "mr", "mrs", "ms"]

    static func shortName(_ merchant: String) -> String {
        var name = merchant
        for title in honorifics where name.lowercased().hasPrefix(title + " ") || name.lowercased().hasPrefix(title) && title.hasSuffix(".") {
            name = String(name.dropFirst(title.count))
            break
        }
        for suffix in genericSuffixes {
            // Whole words only, so "Storehouse" keeps its "Store".
            let pattern = #"(?i)(?<![\p{L}])"# + NSRegularExpression.escapedPattern(for: suffix) + #"(?![\p{L}])"#
            name = name.replacingOccurrences(of: pattern, with: " ", options: .regularExpression)
        }
        name = name.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        name = name.trimmingCharacters(in: CharacterSet.whitespaces.union(.punctuationCharacters))
        guard !name.isEmpty else { return merchant }
        // OCR'd headers are often ALL CAPS; soften those, keep any deliberate casing as it is.
        guard name == name.uppercased() else { return name }
        return name.split(separator: " ").map { $0.prefix(1).uppercased() + $0.dropFirst().lowercased() }.joined(separator: " ")
    }

    // MARK: Amount

    /// Lines that carry the amount actually paid, most specific first. "Subtotal" never counts.
    private static let totalKeywords = ["grand total", "total due", "amount due", "amount paid", "net total", "total",
                                        "รวมทั้งสิ้น", "ยอดสุทธิ", "ยอดรวม", "จำนวนเงิน", "ยอดชำระ", "amount"]

    static func total(in lines: [String]) -> Decimal? {
        for keyword in totalKeywords {
            // The last matching line is usually the final total (after tax/discounts).
            for line in lines.reversed() {
                let lower = line.lowercased()
                guard lower.contains(keyword), !lower.contains("subtotal"), !lower.contains("sub total") else { continue }
                if let amount = amounts(in: line).last { return amount }
            }
        }
        // No labelled total: the largest amount on the receipt is the best guess.
        return lines.flatMap(amounts).max()
    }

    /// Money-looking numbers: "3,450.00", "$6.00", "฿1,290", "500.00 บาท".
    static func amounts(in line: String) -> [Decimal] {
        // "(?!\d|\.\d)" keeps dates like 12.09.2026 from reading as 12.09.
        let pattern = #"(?<![\d.])(?:[$€£฿]\s?)?(\d{1,3}(?:,\d{3})+|\d+)(\.\d{2})(?!\d|\.\d)|[$€£฿]\s?(\d{1,3}(?:,\d{3})+|\d+)(?![\d.])"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        return regex.matches(in: line, range: NSRange(line.startIndex..., in: line)).compactMap { match in
            let groups = (1..<match.numberOfRanges).map { Range(match.range(at: $0), in: line).map { String(line[$0]) } ?? "" }
            let raw = groups[0].isEmpty ? groups[2] : groups[0] + groups[1]
            return Decimal(string: raw.replacingOccurrences(of: ",", with: ""), locale: Locale(identifier: "en_US_POSIX"))
        }
    }

    // MARK: Currency & category

    static func currency(in lower: String) -> String? {
        if lower.contains("฿") || lower.contains("บาท") || lower.contains("thb") { return "THB" }
        if lower.contains("€") || lower.contains("eur") { return "EUR" }
        if lower.contains("£") || lower.contains("gbp") { return "GBP" }
        if lower.contains("¥") || lower.contains("jpy") { return "JPY" }
        if lower.contains("$") || lower.contains("usd") { return "USD" }
        return nil
    }

    private static let categoryKeywords: [(ReceiptCategory, [String])] = [
        (.transfer, ["โอนเงินสำเร็จ", "transfer successful", "promptpay", "พร้อมเพย์"]),
        (.transport, ["grab", "bolt", "taxi", "bts", "mrt", "airport rail", "ptt", "shell", "esso", "bangchak", "fuel", "parking", "ค่าโดยสาร"]),
        (.bills, ["electricity", "water supply", "internet", "ais", "true move", "dtac", "3bb", "mea", "pea", "ค่าไฟ", "ค่าน้ำ"]),
        (.health, ["pharmacy", "hospital", "clinic", "boots", "watsons", "ร้านยา", "โรงพยาบาล"]),
        (.groceries, ["7-eleven", "7 eleven", "lotus", "big c", "makro", "tops", "villa market", "gourmet market", "supermarket", "family mart"]),
        (.foodAndDrink, ["coffee", "cafe", "café", "restaurant", "starbucks", "latte", "americano", "bar ", "food", "grabfood", "lineman",
                         "kitchen", "กาแฟ", "ร้านอาหาร"]),
        (.shopping, ["department store", "central", "the mall", "siam paragon", "emporium", "uniqlo", "ikea", "shopee", "lazada",
                     "fashion", "เสื้อ"]),
    ]

    static func category(of lower: String) -> ReceiptCategory {
        categoryKeywords.first { _, words in words.contains { lower.contains($0) } }?.0 ?? .other
    }
}
