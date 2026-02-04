package crossnote.domain.settings

fun SettingsRepository.setBoolean(key: String, value: Boolean) {
    set(key, value.toString())
}

fun SettingsRepository.getBoolean(key: String, default: Boolean): Boolean {
    return get(key)?.toBoolean() ?: default
}
