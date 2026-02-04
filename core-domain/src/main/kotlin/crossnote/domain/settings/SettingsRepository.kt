package crossnote.domain.settings

interface SettingsRepository {

    fun set(key: String, value: String)

    fun get(key: String): String?

    fun delete(key: String)
}
