import Foundation

enum CloudProvider: String, CaseIterable, Hashable, Sendable, Codable {
    case googleDrive, oneDrive

    var title: String { self == .googleDrive ? "Google Drive" : "OneDrive" }
}

/// What happens to the original after a verified upload (spec §7.8).
enum AfterUploadAction: String, Hashable, Sendable, Codable {
    case keepOnDevice, suggestDeletion, deleteAfter30Days
}

enum RuleTrigger: String, CaseIterable, Hashable, Sendable, Codable {
    case photo, screenshot, receipt, largeVideo, screenRecording, favorite
}

/// "When X, save to provider at folder template, named by file-name template" (spec §7.4).
struct StorageRule: Identifiable, Hashable, Sendable, Codable {
    var id = UUID()
    var name: String
    var trigger: RuleTrigger
    var provider: CloudProvider
    var folderTemplate: String
    var fileNameTemplate: String
    var afterUpload: AfterUploadAction
    var isEnabled = true

    /// The spec's example rules (§7.3), used until the rule builder (v1.2 UI) lets people edit them.
    static let defaults: [StorageRule] = [
        StorageRule(name: "Receipts", trigger: .receipt, provider: .googleDrive,
                    folderTemplate: "/Receipts/{YEAR}/{MONTH}/{MERCHANT}/", fileNameTemplate: "{DATE}_{MERCHANT}_{AMOUNT}",
                    afterUpload: .suggestDeletion),
        StorageRule(name: "Screenshots", trigger: .screenshot, provider: .oneDrive,
                    folderTemplate: "/Pictures/Screenshots/{YEAR}/{MONTH}/", fileNameTemplate: "{ORIGINAL_NAME}",
                    afterUpload: .keepOnDevice),
        StorageRule(name: "Photos", trigger: .photo, provider: .googleDrive,
                    folderTemplate: "/Photos/{YEAR}/{MONTH}/", fileNameTemplate: "{ORIGINAL_NAME}", afterUpload: .keepOnDevice),
        StorageRule(name: "Large Videos", trigger: .largeVideo, provider: .oneDrive,
                    folderTemplate: "/Videos/Compressed/", fileNameTemplate: "{ORIGINAL_NAME}", afterUpload: .keepOnDevice),
    ]
}

/// Values a template can use (spec §7.6–7.7). Missing values fall back to readable placeholders.
struct TemplateValues: Sendable {
    var date: Date
    var merchant: String?
    var amount: Decimal?
    var category: String?
    var event: String?
    var mediaType: String?
    var originalName: String?
    var index: Int?
}

/// Resolves folder and file-name templates into safe cloud paths. Pure, for tests.
enum TemplateResolver {
    /// Resolves e.g. "/Receipts/{YEAR}/{MONTH}/{MERCHANT}/" → "/Receipts/2026/09/Central/".
    static func folder(_ template: String, _ values: TemplateValues, calendar: Calendar = .utcGregorian) -> String {
        let segments = template.split(separator: "/", omittingEmptySubsequences: true).map {
            sanitize(substitute(String($0), values, calendar: calendar))
        }.filter { !$0.isEmpty }
        return "/" + segments.map { $0 + "/" }.joined()
    }

    /// Resolves e.g. "{DATE}_{MERCHANT}_{AMOUNT}" + ".jpg" → "2026-09-27_Central_3450.jpg".
    static func fileName(_ template: String, _ values: TemplateValues, extension ext: String, calendar: Calendar = .utcGregorian) -> String {
        var base = sanitize(substitute(template, values, calendar: calendar)).replacingOccurrences(of: " ", with: "-")
        if base.isEmpty { base = "file" }
        let cleanExt = ext.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        // Keep names comfortably under common 255-byte limits.
        return String(base.prefix(120)) + (cleanExt.isEmpty ? "" : "." + cleanExt)
    }

    private static func substitute(_ text: String, _ v: TemplateValues, calendar: Calendar) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: v.date)
        let year = String(format: "%04d", parts.year ?? 0)
        let month = String(format: "%02d", parts.month ?? 0)
        let day = String(format: "%02d", parts.day ?? 0)
        let replacements: [String: String] = [
            "{YEAR}": year,
            "{MONTH}": month,
            "{DAY}": day,
            "{DATE}": "\(year)-\(month)-\(day)",
            "{MERCHANT}": v.merchant.map(ReceiptExtractor.shortName) ?? "Unknown",
            "{AMOUNT}": v.amount.map(amountText) ?? "0",
            "{CATEGORY}": v.category ?? "Other",
            "{EVENT}": v.event ?? "Event",
            "{MEDIA_TYPE}": v.mediaType ?? "Media",
            "{ORIGINAL_NAME}": v.originalName.map { ($0 as NSString).deletingPathExtension } ?? "file",
            "{INDEX}": v.index.map { String($0) } ?? "1",
        ]
        return replacements.reduce(text) { $0.replacingOccurrences(of: $1.key, with: $1.value) }
    }

    /// 3450 for 3450.00, 12.5 → "12.50".
    static func amountText(_ amount: Decimal) -> String {
        var rounded = amount, value = amount
        NSDecimalRound(&rounded, &value, 0, .plain)
        if rounded == amount { return "\(rounded)" }
        return String(format: "%.2f", NSDecimalNumber(decimal: amount).doubleValue)
    }

    /// Removes characters that Drive/OneDrive/Windows reject and trims stray dots and spaces.
    static func sanitize(_ segment: String) -> String {
        let forbidden = CharacterSet(charactersIn: "/\\:*?\"<>|#%").union(.controlCharacters)
        let cleaned = segment.unicodeScalars.map { forbidden.contains($0) ? " " : String($0) }.joined()
        return cleaned.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: " ."))
    }
}

/// Where a receipt will be filed, for the Receipt Filing screen.
struct FilingPlan: Sendable, Equatable {
    let rule: StorageRule
    let folder: String
    let fileName: String

    var fullPath: String { folder + fileName }
}

enum RuleMatcher {
    static func plan(for receipt: ReceiptDetails, capturedAt: Date, originalName: String?, fileExtension: String,
                     rules: [StorageRule] = StorageRule.defaults) -> FilingPlan? {
        guard let rule = rules.first(where: { $0.isEnabled && $0.trigger == .receipt }) else { return nil }
        let values = TemplateValues(date: receipt.date ?? capturedAt, merchant: receipt.merchant, amount: receipt.amount,
                                    category: receipt.category.title, mediaType: "Receipt", originalName: originalName)
        return FilingPlan(rule: rule, folder: TemplateResolver.folder(rule.folderTemplate, values),
                          fileName: TemplateResolver.fileName(rule.fileNameTemplate, values, extension: fileExtension))
    }
}

extension Calendar {
    /// Template dates use UTC noon-anchored days from the extractor, so resolve them in UTC too.
    static let utcGregorian: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()
}
