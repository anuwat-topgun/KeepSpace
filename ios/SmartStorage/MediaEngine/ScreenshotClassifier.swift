import Foundation

/// What a screenshot is about, from on-device OCR + barcode detection.
enum ScreenshotKind: String, CaseIterable, Hashable, Sendable, Codable {
    case shopping, receipts, chats, qrCodes, tickets, other

    var title: String {
        switch self {
        case .shopping: "Shopping"
        case .receipts: "Receipts"
        case .chats: "Chats"
        case .qrCodes: "QR Codes"
        case .tickets: "Tickets"
        case .other: "Other"
        }
    }

    var systemImage: String {
        switch self {
        case .shopping: "bag.fill"
        case .receipts: "doc.text.fill"
        case .chats: "bubble.left.and.bubble.right.fill"
        case .qrCodes: "qrcode.viewfinder"
        case .tickets: "ticket.fill"
        case .other: "viewfinder"
        }
    }

    var tint: Tint {
        switch self {
        case .shopping: .coral
        case .receipts: .amber
        case .chats: .mint
        case .qrCodes: .blue
        case .tickets: .purple
        case .other: .gray
        }
    }

    /// Documents people may need later are never suggested for cleanup just for being old.
    var isImportant: Bool { self == .receipts || self == .tickets }
}

/// The only things kept from a screenshot's text. The text itself is never stored.
struct ScreenshotInfo: Sendable, Equatable {
    let kind: ScreenshotKind
    /// For tickets: the latest date printed on it (travel / event day).
    var eventDate: Date? = nil
    /// For travel tickets: "BKK → TYO".
    var route: String? = nil
    /// For receipts: merchant, date, total… (kept on device only).
    var receipt: ReceiptDetails? = nil

    func isExpired(now: Date) -> Bool {
        guard kind == .tickets, let eventDate else { return false }
        return eventDate < now.addingTimeInterval(-24 * 3600)
    }
}

/// Keyword classifier over recognised text (English + Thai). Pure, so it is unit-tested.
enum ScreenshotClassifier {
    private static let keywords: [ScreenshotKind: [String]] = [
        .tickets: ["boarding pass", "boarding", "gate", "seat", "flight", "pnr", "e-ticket", "eticket", "ticket", "admit one",
                   "departure", "arrival", "passenger", "check-in", "บัตรโดยสาร", "ที่นั่ง", "เที่ยวบิน", "ตั๋ว", "ประตูขึ้นเครื่อง"],
        .receipts: ["receipt", "subtotal", "total", "tax", "vat", "invoice", "amount paid", "paid", "change due", "cashier",
                    "payment", "transaction", "transfer successful", "ใบเสร็จ", "รวมทั้งสิ้น", "ยอดรวม", "ภาษี", "ชำระเงิน",
                    "โอนเงินสำเร็จ", "จำนวนเงิน"],
        .shopping: ["add to cart", "add to bag", "buy now", "checkout", "free shipping", "in stock", "sold", "reviews",
                    "wishlist", "shopee", "lazada", "amazon", "discount", "sale", "ตะกร้า", "ซื้อเลย", "ส่งฟรี", "ลดราคา", "ขายแล้ว"],
        .chats: ["typing", "delivered", "seen", "message", "reply", "online", "last seen", "whatsapp", "messenger", "line",
                 "imessage", "ส่งข้อความ", "อ่านแล้ว", "พิมพ์ข้อความ", "ตอบกลับ"],
    ]
    /// Words that strongly identify a category count double.
    /// Thai bank-transfer slips ("โอนเงินสำเร็จ") are receipts even though they always carry a QR code.
    private static let strong: Set<String> = ["boarding pass", "e-ticket", "receipt", "invoice", "add to cart", "buy now",
                                              "transfer successful", "บัตรโดยสาร", "ใบเสร็จ", "ตะกร้า", "โอนเงินสำเร็จ"]

    static func classify(text: String, hasQRCode: Bool, now: Date = .now) -> ScreenshotInfo {
        let lower = text.lowercased()
        var scores: [ScreenshotKind: Double] = [:]
        for (kind, words) in keywords {
            for word in words where contains(lower, word) {
                scores[kind, default: 0] += strong.contains(word) ? 2 : 1
            }
        }
        // Prices with a currency push towards receipts/shopping; many chat-style timestamps towards chats.
        let prices = matches(#"(?:[$€£฿]\s?\d[\d,]*(?:\.\d{2})?|\d[\d,]*\.\d{2}\s?(?:thb|usd|บาท))"#, in: lower)
        if prices >= 3 { scores[.receipts, default: 0] += 1; scores[.shopping, default: 0] += 0.5 }
        // Receipts list several amounts even when the words aren't readable (e.g. Thai text on Android,
        // where the OCR model is Latin-only). Keeps such receipts out of "Old Screenshots".
        if matches(#"\b\d[\d,]*\.\d{2}\b"#, in: lower) >= 3 { scores[.receipts, default: 0] += 2 }
        if matches(#"\b\d{1,2}:\d{2}\b"#, in: lower) >= 4 { scores[.chats, default: 0] += 1.5 }
        if hasQRCode { scores[.qrCodes, default: 0] += 2 }

        // Tickets and receipts often carry a QR code too; they win when their own evidence is clear.
        guard let (kind, score) = scores.max(by: { $0.value < $1.value || ($0.value == $1.value && priority($0.key) > priority($1.key)) }),
              score >= 2 else {
            return ScreenshotInfo(kind: hasQRCode ? .qrCodes : .other)
        }
        if kind == .receipts { return ScreenshotInfo(kind: .receipts, receipt: ReceiptExtractor.extract(from: text, now: now)) }
        guard kind == .tickets else { return ScreenshotInfo(kind: kind) }
        return ScreenshotInfo(kind: .tickets, eventDate: DateExtractor.dates(in: text, now: now).max(), route: route(in: text))
    }

    /// Lower = wins ties.
    private static func priority(_ kind: ScreenshotKind) -> Int {
        [.tickets, .receipts, .qrCodes, .shopping, .chats, .other].firstIndex(of: kind) ?? 99
    }

    /// Whole-word match for Latin keywords (so "line" doesn't match "online"); substring for Thai.
    private static func contains(_ text: String, _ word: String) -> Bool {
        guard word.unicodeScalars.allSatisfy({ $0.isASCII }) else { return text.contains(word) }
        let pattern = #"(?<![a-z])"# + NSRegularExpression.escapedPattern(for: word) + #"(?![a-z])"#
        return text.range(of: pattern, options: .regularExpression) != nil
    }

    private static func matches(_ pattern: String, in text: String) -> Int {
        (try? NSRegularExpression(pattern: pattern))?.numberOfMatches(in: text, range: NSRange(text.startIndex..., in: text)) ?? 0
    }

    /// "BKK → TYO", "BKK - NRT", "BKK to HND".
    static func route(in text: String) -> String? {
        let pattern = #"\b([A-Z]{3})\s*(?:→|->|–|-|to|✈)\s*([A-Z]{3})\b"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let from = Range(match.range(at: 1), in: text), let to = Range(match.range(at: 2), in: text)
        else { return nil }
        return "\(text[from]) → \(text[to])"
    }
}

/// Finds calendar dates in OCR text: 2026-09-12, 12/09/2026, 12 SEP 2026, Sep 12, 2026, 12SEP26.
enum DateExtractor {
    private static let months = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]

    static func dates(in text: String, now: Date = .now) -> [Date] {
        let lower = text.lowercased()
        var found: [Date] = []
        let calendar = Calendar(identifier: .gregorian)

        func add(day: Int, month: Int, year: Int) {
            var y = year < 100 ? 2000 + year : year
            if y > 2400 { y -= 543 } // Thai Buddhist-era years
            guard (1...12).contains(month), (1...31).contains(day), (2000...2100).contains(y) else { return }
            var parts = DateComponents(year: y, month: month, day: day, hour: 12)
            parts.timeZone = TimeZone(identifier: "UTC")
            if let date = calendar.date(from: parts) { found.append(date) }
        }

        for m in captures(#"\b(\d{4})-(\d{1,2})-(\d{1,2})\b"#, lower) { add(day: m[2], month: m[1], year: m[0]) }
        // Day first (Thailand/UK); swap when the first number can't be a month-day ordering otherwise.
        for m in captures(#"\b(\d{1,2})[/.](\d{1,2})[/.](\d{2,4})\b"#, lower) {
            if m[1] > 12 { add(day: m[1], month: m[0], year: m[2]) } else { add(day: m[0], month: m[1], year: m[2]) }
        }
        let monthAlternation = months.joined(separator: "|")
        for m in textCaptures(#"\b(\d{1,2})\s?("# + monthAlternation + #")[a-z]*\.?,?\s?(\d{2,4})\b"#, lower) {
            if let day = Int(m[0]), let year = Int(m[2]), let month = months.firstIndex(of: m[1]) { add(day: day, month: month + 1, year: year) }
        }
        for m in textCaptures(#"\b("# + monthAlternation + #")[a-z]*\.?\s(\d{1,2}),?\s(\d{4})\b"#, lower) {
            if let day = Int(m[1]), let year = Int(m[2]), let month = months.firstIndex(of: m[0]) { add(day: day, month: month + 1, year: year) }
        }
        // Thai: "27 ก.ย. 69", "27 ก.ย. 2569", "27 กันยายน 2569". Two-digit years are Buddhist era.
        for m in textCaptures(#"(\d{1,2})\s?("# + thaiMonthPattern + #")\s?(\d{2}|\d{4})(?!\d)"#, text) {
            guard let day = Int(m[0]), var year = Int(m[2]), let month = thaiMonth(m[1]) else { continue }
            if year < 100 { year += 2500 }
            add(day: day, month: month, year: year)
        }
        return found
    }

    /// Abbreviations with or without dots, and full names. Longer forms first so "มี.ค." isn't read as "ม.ค.".
    private static let thaiMonths: [(Int, [String])] = [
        (1, ["มกราคม", "ม.ค.", "มค"]), (2, ["กุมภาพันธ์", "ก.พ.", "กพ"]), (3, ["มีนาคม", "มี.ค.", "มีค"]),
        (4, ["เมษายน", "เม.ย.", "เมย"]), (5, ["พฤษภาคม", "พ.ค.", "พค"]), (6, ["มิถุนายน", "มิ.ย.", "มิย"]),
        (7, ["กรกฎาคม", "ก.ค.", "กค"]), (8, ["สิงหาคม", "ส.ค.", "สค"]), (9, ["กันยายน", "ก.ย.", "กย"]),
        (10, ["ตุลาคม", "ต.ค.", "ตค"]), (11, ["พฤศจิกายน", "พ.ย.", "พย"]), (12, ["ธันวาคม", "ธ.ค.", "ธค"]),
    ]

    private static var thaiMonthPattern: String {
        thaiMonths.flatMap(\.1).sorted { $0.count > $1.count }.map(NSRegularExpression.escapedPattern).joined(separator: "|")
    }

    private static func thaiMonth(_ token: String) -> Int? {
        thaiMonths.first { $0.1.contains(token) }?.0
    }

    private static func captures(_ pattern: String, _ text: String) -> [[Int]] {
        textCaptures(pattern, text).map { $0.compactMap(Int.init) }.filter { $0.count == 3 }
    }

    private static func textCaptures(_ pattern: String, _ text: String) -> [[String]] {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        return regex.matches(in: text, range: NSRange(text.startIndex..., in: text)).map { match in
            (1..<match.numberOfRanges).compactMap { Range(match.range(at: $0), in: text).map { String(text[$0]) } }
        }
    }
}
