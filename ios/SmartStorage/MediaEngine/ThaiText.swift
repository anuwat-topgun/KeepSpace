import Foundation

/// Thai-aware text helpers for OCR output. Pure, for tests. Mirrors ThaiText.kt.
enum ThaiText {
    private static let months = ["ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."]

    /// "กุย." / "ก ย." / "ก,ย." → "ก.ย.", only between a day and a year so ordinary words are never touched.
    private static let monthRepairs: [(NSRegularExpression, String)] = months.compactMap { month in
        let parts = month.split(separator: ".").map { NSRegularExpression.escapedPattern(for: String($0)) }
        let pattern = #"(?<=\d\s{0,2})"# + parts.joined(separator: #"\s?[.ุ,]?\s?"#) + #"[.,]?(?=\s*\d)"#
        return (try? NSRegularExpression(pattern: pattern)).map { ($0, month) }
    }

    /// Repairs what OCR engines get systematically wrong in Thai: sara am written as nikhahit +
    /// sara aa, and the dots of month abbreviations read as marks.
    static func normalize(_ text: String) -> String {
        var out = text.replacingOccurrences(of: "\u{0E4D}\u{0E32}", with: "\u{0E33}")
        for (regex, month) in monthRepairs {
            out = regex.stringByReplacingMatches(in: out, range: NSRange(out.startIndex..., in: out),
                                                 withTemplate: NSRegularExpression.escapedTemplate(for: month))
        }
        return out
    }

    /// Lower-cased with Thai tone marks removed: OCR often drops them ("ตะกรา" for "ตะกร้า"). For matching only.
    static func fold(_ text: String) -> String {
        String(String.UnicodeScalarView(text.lowercased().unicodeScalars.filter { !(0x0E48...0x0E4B).contains($0.value) }))
    }

    /// True when Latin-only OCR has read Thai script as Latin gibberish ("SNuNNuWUNuau", "Tauâuanỗ", "wndng").
    static func looksLikeMisreadThai(_ text: String) -> Bool {
        let words = text.split(whereSeparator: \.isWhitespace).map { $0.filter(\.isLetter) }.filter { $0.count >= 2 }
        guard !words.isEmpty else { return false }
        let vowels: Set<Character> = ["a", "e", "i", "o", "u", "y"]
        let suspicious = words.filter { word in
            let accented = word.unicodeScalars.contains { (0x00C0...0x024F).contains($0.value) || (0x1E00...0x1EFF).contains($0.value) }
            let chars = Array(word)
            let caseJumps = zip(chars, chars.dropFirst()).filter { $0.isLowercase && $1.isUppercase }.count
            let noVowels = word.count >= 4 && word.contains(where: \.isLowercase) && !word.lowercased().contains(where: vowels.contains)
            return accented || caseJumps >= 2 || noVowels
        }.count
        return suspicious >= 2 || (suspicious >= 1 && suspicious * 5 >= words.count)
    }
}
