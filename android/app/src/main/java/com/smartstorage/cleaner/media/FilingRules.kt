package com.smartstorage.cleaner.media

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

// Mirrors ios/SmartStorage/MediaEngine/FilingRules.swift.

enum class CloudProvider(val title: String) { GoogleDrive("Google Drive"), OneDrive("OneDrive") }

/** What happens to the original after a verified upload (spec §7.8). */
enum class AfterUploadAction { KeepOnDevice, SuggestDeletion, DeleteAfter30Days }

enum class RuleTrigger { Photo, Screenshot, Receipt, LargeVideo, ScreenRecording, Favorite }

/** "When X, save to provider at folder template, named by file-name template" (spec §7.4). */
data class StorageRule(
    val name: String,
    val trigger: RuleTrigger,
    val provider: CloudProvider,
    val folderTemplate: String,
    val fileNameTemplate: String,
    val afterUpload: AfterUploadAction,
    val isEnabled: Boolean = true,
) {
    companion object {
        /** The spec's example rules (§7.3), used until the rule builder (v1.2 UI) lets people edit them. */
        val defaults = listOf(
            StorageRule("Receipts", RuleTrigger.Receipt, CloudProvider.GoogleDrive,
                "/Receipts/{YEAR}/{MONTH}/{MERCHANT}/", "{DATE}_{MERCHANT}_{AMOUNT}", AfterUploadAction.SuggestDeletion),
            StorageRule("Screenshots", RuleTrigger.Screenshot, CloudProvider.OneDrive,
                "/Pictures/Screenshots/{YEAR}/{MONTH}/", "{ORIGINAL_NAME}", AfterUploadAction.KeepOnDevice),
            StorageRule("Photos", RuleTrigger.Photo, CloudProvider.GoogleDrive,
                "/Photos/{YEAR}/{MONTH}/", "{ORIGINAL_NAME}", AfterUploadAction.KeepOnDevice),
            StorageRule("Large Videos", RuleTrigger.LargeVideo, CloudProvider.OneDrive,
                "/Videos/Compressed/", "{ORIGINAL_NAME}", AfterUploadAction.KeepOnDevice),
        )
    }
}

/** Values a template can use (spec §7.6–7.7). Missing values fall back to readable placeholders. */
data class TemplateValues(
    /** Epoch millis. */
    val date: Long,
    val merchant: String? = null,
    val amount: BigDecimal? = null,
    val category: String? = null,
    val event: String? = null,
    val mediaType: String? = null,
    val originalName: String? = null,
    val index: Int? = null,
)

/** Resolves folder and file-name templates into safe cloud paths. Pure, for tests. */
object TemplateResolver {
    /** "/Receipts/{YEAR}/{MONTH}/{MERCHANT}/" → "/Receipts/2026/09/Central/". */
    fun folder(template: String, values: TemplateValues): String {
        val segments = template.split("/").filter { it.isNotEmpty() }.map { sanitize(substitute(it, values)) }.filter { it.isNotEmpty() }
        return "/" + segments.joinToString("") { "$it/" }
    }

    /** "{DATE}_{MERCHANT}_{AMOUNT}" + "jpg" → "2026-09-27_Central_3450.jpg". */
    fun fileName(template: String, values: TemplateValues, extension: String): String {
        val base = sanitize(substitute(template, values)).replace(" ", "-").ifEmpty { "file" }
        val ext = extension.lowercase().trim('.')
        // Keep names comfortably under common 255-byte limits.
        return base.take(120) + if (ext.isEmpty()) "" else ".$ext"
    }

    private fun substitute(text: String, v: TemplateValues): String {
        val day = Instant.ofEpochMilli(v.date).atZone(ZoneOffset.UTC).toLocalDate()
        val year = "%04d".format(day.year)
        val month = "%02d".format(day.monthValue)
        val dayOfMonth = "%02d".format(day.dayOfMonth)
        val replacements = mapOf(
            "{YEAR}" to year,
            "{MONTH}" to month,
            "{DAY}" to dayOfMonth,
            "{DATE}" to "$year-$month-$dayOfMonth",
            "{MERCHANT}" to (v.merchant?.let(ReceiptExtractor::shortName) ?: "Unknown"),
            "{AMOUNT}" to (v.amount?.let(::amountText) ?: "0"),
            "{CATEGORY}" to (v.category ?: "Other"),
            "{EVENT}" to (v.event ?: "Event"),
            "{MEDIA_TYPE}" to (v.mediaType ?: "Media"),
            "{ORIGINAL_NAME}" to (v.originalName?.substringBeforeLast('.') ?: "file"),
            "{INDEX}" to (v.index?.toString() ?: "1"),
        )
        return replacements.entries.fold(text) { acc, (key, value) -> acc.replace(key, value) }
    }

    /** 3450 for 3450.00, "12.50" for 12.5. */
    fun amountText(amount: BigDecimal): String {
        val stripped = amount.stripTrailingZeros()
        return if (stripped.scale() <= 0) stripped.toPlainString() else amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }

    /** Removes characters that Drive/OneDrive/Windows reject and trims stray dots and spaces. */
    fun sanitize(segment: String): String = segment
        .map { if (it in "/\\:*?\"<>|#%" || it.isISOControl()) ' ' else it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.')
}

/** Where a receipt will be filed, for the Receipt Filing screen. */
data class FilingPlan(val rule: StorageRule, val folder: String, val fileName: String) {
    val fullPath: String get() = folder + fileName
}

object RuleMatcher {
    fun plan(
        receipt: ReceiptDetails,
        capturedAt: Long,
        originalName: String?,
        fileExtension: String,
        rules: List<StorageRule> = StorageRule.defaults,
    ): FilingPlan? {
        val rule = rules.firstOrNull { it.isEnabled && it.trigger == RuleTrigger.Receipt } ?: return null
        val values = TemplateValues(
            date = receipt.date ?: capturedAt, merchant = receipt.merchant, amount = receipt.amount,
            category = receipt.category.title, mediaType = "Receipt", originalName = originalName,
        )
        return FilingPlan(rule, TemplateResolver.folder(rule.folderTemplate, values), TemplateResolver.fileName(rule.fileNameTemplate, values, fileExtension))
    }
}
