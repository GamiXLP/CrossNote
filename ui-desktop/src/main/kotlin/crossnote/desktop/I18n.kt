package crossnote.desktop

import crossnote.infra.persistence.SqliteSettingsRepository
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

class I18n(private val settingsRepo: SqliteSettingsRepository) {

    companion object {
        private const val KEY_LANG = "ui.language" // "de" | "en"
        private const val BASE_NAME = "i18n.messages"
    }

    private var lang: String = settingsRepo.get(KEY_LANG) ?: "de"
    private var bundle: ResourceBundle = loadBundle(lang)

    fun currentLang(): String = lang

    fun setLang(newLang: String) {
        val normalized = newLang.lowercase()
        if (normalized == lang) return
        lang = normalized
        settingsRepo.set(KEY_LANG, lang)
        bundle = loadBundle(lang)
    }

    fun toggleLang() {
        setLang(if (lang == "de") "en" else "de")
    }

    fun t(key: String, vararg args: Any?): String {
        val raw = if (bundle.containsKey(key)) bundle.getString(key) else "!!$key!!"
        return if (args.isEmpty()) raw else MessageFormat.format(raw, *args)
    }

    private fun loadBundle(code: String): ResourceBundle {
        val locale = when (code.lowercase()) {
            "en" -> Locale.ENGLISH
            else -> Locale.GERMAN
        }
        return ResourceBundle.getBundle(BASE_NAME, locale)
    }
}
