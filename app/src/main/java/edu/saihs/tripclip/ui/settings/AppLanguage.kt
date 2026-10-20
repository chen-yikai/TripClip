package edu.saihs.tripclip.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import edu.saihs.tripclip.R

/** UI languages the app ships. */
enum class AppLanguage(
    val tag: String,
    /** The language's name in itself, shown as the option title. */
    val nativeName: String,
    /** The language's name in the current UI language. */
    @StringRes val localizedName: Int,
    /** English name, used as the subtitle when the localized name equals [nativeName]. */
    val englishName: String,
) {
    TraditionalChinese("zh-TW", "繁體中文", R.string.language_name_zh_tw, "Traditional Chinese"),
    English("en", "English", R.string.language_name_en, "English"),
    Japanese("ja", "日本語", R.string.language_name_ja, "Japanese");

    companion object {
        /** The language the UI is shown in: the per-app choice, else the system locale. */
        fun current(context: Context): AppLanguage {
            val appLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
            val locale = if (appLocales.isEmpty) context.resources.configuration.locales[0] else appLocales[0]
            return when (locale.language) {
                "zh" -> TraditionalChinese
                "ja" -> Japanese
                else -> English
            }
        }

        /** Persists the choice (Android 13+ per-app locale); the system recreates the activity. */
        fun apply(context: Context, language: AppLanguage) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.tag)
        }
    }
}
