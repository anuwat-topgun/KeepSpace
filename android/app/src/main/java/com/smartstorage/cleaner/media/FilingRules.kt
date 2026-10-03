package com.smartstorage.cleaner.media

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

// Mirrors ios/SmartStorage/MediaEngine/FilingRules.swift.

enum class CloudProvider(val title: String) { GoogleDrive("Google Drive"), OneDrive("OneDrive") }

/**
 * What happens to the original after a verified upload (spec §7.8). Nothing is ever deleted without
 * the system confirmation; "after 30 days" only changes *when* the suggestion appears.
 */
enum class AfterUploadAction(val title: String, val detail: String) {
    SuggestDeletion("Suggest deletion", "Show a smart suggestion once the upload is verified."),
    KeepOnDevice("Keep on device", "Keep the original file on your device."),
    DeleteAfter30Days(
        "Suggest deletion after 30 days",
        "Suggest removing the original 30 days after a verified upload. You still confirm every deletion.",
    ),
}

enum class RuleTrigger(val title: String, val suggestedFolder: String, val suggestedFileName: String) {
    Photo("Photos", "/Photos/{YEAR}/{MONTH}/", "{ORIGINAL_NAME}"),
    Screenshot("Screenshots", "/Pictures/Screenshots/{YEAR}/{MONTH}/", "{ORIGINAL_NAME}"),
    Receipt("Receipts", "/Receipts/{YEAR}/{MONTH}/{MERCHANT}/", "{DATE}_{MERCHANT}_{AMOUNT}"),
    LargeVideo("Large Videos", "/Videos/Compressed/", "{ORIGINAL_NAME}"),
    ScreenRecording("Screen Recordings", "/Videos/Screen Recordings/{YEAR}/", "{ORIGINAL_NAME}"),
    Favorite("Favorites", "/Favorites/{YEAR}/", "{ORIGINAL_NAME}"),
}

/** "When X, save to provider at folder template, named by file-name template" (spec §7.4). */
data class StorageRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val trigger: RuleTrigger,
    val provider: CloudProvider,
    val folderTemplate: String,
    val fileNameTemplate: String,
    val afterUpload: AfterUploadAction,
    val isEnabled: Boolean = true,
) {
    /**
     * True for the built-in example rules exactly as shipped. Editing one makes it the person's own, and only
     * their own rules count toward the Free plan's rule limit.
     */
    val isExample: Boolean get() = defaults.any { it.copy(id = id, isEnabled = isEnabled) == this }

    companion object {
        /** The spec's example rules (§7.3), used until the rule builder (v1.2 UI) lets people edit them. */
        val defaults = listOf(
            StorageRule("default-receipts", "Receipts", RuleTrigger.Receipt, CloudProvider.GoogleDrive,
                "/Receipts/{YEAR}/{MONTH}/{MERCHANT}/", "{DATE}_{MERCHANT}_{AMOUNT}", AfterUploadAction.SuggestDeletion),
            StorageRule("default-screenshots", "Screenshots", RuleTrigger.Screenshot, CloudProvider.OneDrive,
                "/Pictures/Screenshots/{YEAR}/{MONTH}/", "{ORIGINAL_NAME}", AfterUploadAction.KeepOnDevice),
            StorageRule("default-photos", "Photos", RuleTrigger.Photo, CloudProvider.GoogleDrive,
                "/Photos/{YEAR}/{MONTH}/", "{ORIGINAL_NAME}", AfterUploadAction.KeepOnDevice),
            StorageRule("default-large-videos", "Large Videos", RuleTrigger.LargeVideo, CloudProvider.OneDrive,
                "/Videos/Compressed/", "{ORIGINAL_NAME}", AfterUploadAction.KeepOnDevice),
        )
    }
}

sealed interface RuleProblem {
    data object EmptyFolder : RuleProblem
    data object EmptyFileName : RuleProblem
    data class UnknownVariables(val names: List<String>) : RuleProblem
}

/** Everything that would stop the rule from resolving cleanly, for the builder's inline hints. */
val StorageRule.problems: List<RuleProblem>
    get() = buildList {
        if (TemplateResolver.sanitize(folderTemplate.replace("/", "")).isEmpty()) add(RuleProblem.EmptyFolder)
        if (TemplateResolver.sanitize(fileNameTemplate).isEmpty()) add(RuleProblem.EmptyFileName)
        val unknown = TemplateResolver.unknownVariables(folderTemplate + fileNameTemplate)
        if (unknown.isNotEmpty()) add(RuleProblem.UnknownVariables(unknown))
    }

/** Example of where a file would land, using sample values (receipt-style for receipts). */
fun StorageRule.preview(now: Long = System.currentTimeMillis()): FilingPlan {
    val receipt = trigger == RuleTrigger.Receipt
    val values = TemplateValues(
        date = now, merchant = if (receipt) "Central Department Store" else null, amount = if (receipt) BigDecimal(3450) else null,
        category = if (receipt) "Shopping" else trigger.title, mediaType = trigger.title,
        originalName = if (receipt) "IMG_0412.JPG" else "IMG_2048.HEIC",
    )
    return FilingPlan(this, TemplateResolver.folder(folderTemplate, values),
        TemplateResolver.fileName(fileNameTemplate, values, if (receipt) "jpg" else "heic"))
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
    /** Variables the resolver understands, in the order the builder offers them. */
    val folderVariables = listOf("{YEAR}", "{MONTH}", "{DAY}", "{DATE}", "{CATEGORY}", "{MERCHANT}", "{EVENT}", "{MEDIA_TYPE}", "{AMOUNT}")
    val fileNameVariables = listOf("{DATE}", "{MERCHANT}", "{AMOUNT}", "{ORIGINAL_NAME}", "{CATEGORY}", "{INDEX}", "{YEAR}", "{MONTH}", "{DAY}")
    private val known = (folderVariables + fileNameVariables).toSet()

    /** "{YAER}" and friends. Case-sensitive on purpose: "{year}" isn't substituted, so it must be flagged. */
    fun unknownVariables(template: String): List<String> =
        Regex("""\{[^{}]*\}""").findAll(template).map { it.value }.filter { it !in known }.distinct().toList()

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
