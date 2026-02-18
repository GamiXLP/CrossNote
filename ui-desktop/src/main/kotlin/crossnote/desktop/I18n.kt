package crossnote.desktop

import crossnote.infra.persistence.SqliteSettingsRepository
import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.SimpleStringProperty
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

class I18n(private val settingsRepo: SqliteSettingsRepository) {

    companion object {
        private const val KEY_LANG = "ui.language" // "de" | "en"
        private const val BASE_NAME = "i18n.messages"
    }

    private val langProperty = SimpleStringProperty(settingsRepo.get(KEY_LANG) ?: "de")
    private var bundle: ResourceBundle = loadBundle(langProperty.get())

    fun currentLang(): String = langProperty.get()

    fun langProperty(): ReadOnlyStringProperty = langProperty

    fun setLang(newLang: String) {
        val normalized = newLang.lowercase()
        if (normalized == langProperty.get()) return

        langProperty.set(normalized)
        settingsRepo.set(KEY_LANG, normalized)
        bundle = loadBundle(normalized)
    }

    fun toggleLang() {
        setLang(if (currentLang() == "de") "en" else "de")
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
