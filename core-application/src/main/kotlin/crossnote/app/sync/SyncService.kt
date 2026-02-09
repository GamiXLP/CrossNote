package crossnote.app.sync

import crossnote.domain.settings.SettingsRepository
import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import java.time.Instant

class SyncService(
    private val settings: SettingsRepository
) {
    object Keys {
        const val ENABLED = "sync.enabled"
        const val SERVER_MODE = "sync.serverMode"
        const val HOST = "sync.host"
        const val PORT = "sync.port"
        const val LAST_PULLED_AT = "sync.lastPulledAt"
        const val LAST_PUSHED_AT = "sync.lastPushedAt"
    }

    data class SyncConfig(
        val enabled: Boolean,
        val serverMode: Boolean,
        val host: String,
        val port: Int,
        val lastPulledAt: Instant?,
        val lastPushedAt: Instant?
    )

    fun loadConfig(): SyncConfig {
        val enabled = settings.getBoolean(Keys.ENABLED, default = true)
        val serverMode = settings.getBoolean(Keys.SERVER_MODE, default = false)

        val host = settings.get(Keys.HOST) ?: "localhost"
        val port = (settings.get(Keys.PORT) ?: "8085").toIntOrNull() ?: 8085

        val lastPulledAt = settings.get(Keys.LAST_PULLED_AT)
            ?.takeIf { it.isNotBlank() }
            ?.let { Instant.parse(it) }

        val lastPushedAt = settings.get(Keys.LAST_PUSHED_AT)
            ?.takeIf { it.isNotBlank() }
            ?.let { Instant.parse(it) }

        return SyncConfig(
            enabled = enabled,
            serverMode = serverMode,
            host = host,
            port = port,
            lastPulledAt = lastPulledAt,
            lastPushedAt = lastPushedAt
        )
    }

    fun saveConfig(cfg: SyncConfig) {
        settings.setBoolean(Keys.ENABLED, cfg.enabled)
        settings.setBoolean(Keys.SERVER_MODE, cfg.serverMode)
        settings.set(Keys.HOST, cfg.host)
        settings.set(Keys.PORT, cfg.port.toString())
        settings.set(Keys.LAST_PULLED_AT, cfg.lastPulledAt?.toString() ?: "")
        settings.set(Keys.LAST_PUSHED_AT, cfg.lastPushedAt?.toString() ?: "")
    }

    fun syncNow(): SyncResult {
        val cfg = loadConfig()
        if (!cfg.enabled) return SyncResult.Disabled
        return SyncResult.NotImplementedYet(cfg)
    }

    sealed class SyncResult {
        data object Disabled : SyncResult()
        data class NotImplementedYet(val cfg: SyncConfig) : SyncResult()
    }
}