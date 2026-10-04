package com.smartstorage.cleaner.ui.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class AppLanguageOption(val tag: String?, val nativeName: String)

object AppLanguage {
    private const val PREFS = "keepspace"
    private const val KEY = "app.language"

    val options = listOf(
        AppLanguageOption(null, "Use Device Language"),
        AppLanguageOption("en", "English"),
        AppLanguageOption("es", "Español"),
        AppLanguageOption("pt-BR", "Português (Brasil)"),
        AppLanguageOption("fr", "Français"),
        AppLanguageOption("de", "Deutsch"),
        AppLanguageOption("it", "Italiano"),
        AppLanguageOption("ar", "العربية"),
        AppLanguageOption("tr", "Türkçe"),
        AppLanguageOption("ru", "Русский"),
        AppLanguageOption("zh-Hans", "简体中文"),
        AppLanguageOption("ja", "日本語"),
        AppLanguageOption("ko", "한국어"),
        AppLanguageOption("th", "ไทย"),
        AppLanguageOption("id", "Bahasa Indonesia"),
        AppLanguageOption("vi", "Tiếng Việt"),
        AppLanguageOption("nl", "Nederlands"),
        AppLanguageOption("pl", "Polski"),
        AppLanguageOption("ro", "Română"),
        AppLanguageOption("el", "Ελληνικά"),
        AppLanguageOption("uk", "Українська"),
        AppLanguageOption("hi", "हिन्दी"),
        AppLanguageOption("ms", "Bahasa Melayu"),
        AppLanguageOption("zh-Hant", "繁體中文"),
        AppLanguageOption("sv", "Svenska"),
        AppLanguageOption("cs", "Čeština"),
    )

    fun selectedTag(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) return locales[0].toLanguageTag()
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    }

    fun displayName(context: Context): String =
        options.firstOrNull { it.tag == selectedTag(context) }?.nativeName ?: options.first().nativeName

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val selected = selectedTag(base)
        val locale = selected?.let(Locale::forLanguageTag)
            ?: base.resources.configuration.locales[0]
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    fun select(activity: Activity, tag: String?) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY) else putString(KEY, tag)
        }.apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.recreate()
        }
    }
}
