package com.smartstorage.cleaner.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Localized format string with runtime values (prices, counts, dates). The English source is the catalogue key; such
 * strings are listed explicitly in i18n/extract.py because the extractor skips interpolated Kotlin on purpose.
 * Apple-style `%@` placeholders are accepted in the source (the generated Android resources use `%s`).
 */
@Composable
fun localizedFormat(source: String, vararg args: Any): String {
    val id = I18nCatalog.resources[source]
    return if (id != null) stringResource(id, *args) else source.replace("%@", "%s").format(*args)
}

/** Resolves a plain catalogue string held in a variable (the `Text` overload does this for composables). */
@Composable
fun localized(source: String): String = I18nCatalog.resources[source]?.let { stringResource(it) } ?: source

/** [localizedFormat] for code that isn't composable (stores that post notices). Uses the app's current locale. */
fun android.content.Context.localizedFormat(source: String, vararg args: Any): String {
    val id = I18nCatalog.resources[source]
    return if (id != null) getString(id, *args) else source.replace("%@", "%s").format(*args)
}

/** Singular format for a count of 1, plural otherwise. Other languages use the same neutral wording for both. */
@Composable
fun localizedCount(count: Int, one: String, other: String): String = localizedFormat(if (count == 1) one else other, count)
