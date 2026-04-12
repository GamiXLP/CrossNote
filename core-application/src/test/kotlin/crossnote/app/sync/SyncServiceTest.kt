package crossnote.app.sync

import crossnote.domain.settings.SettingsRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncServiceTest {

    @Test
    fun `loadConfig returns defaults when settings are empty`() {
        val repo = FakeSettingsRepository()
        val service = SyncService(repo)

        val cfg = service.loadConfig()

        assertTrue(cfg.enabled)
        assertFalse(cfg.serverMode)
        assertEquals("localhost", cfg.host)
        assertEquals(8085, cfg.port)
        assertNull(cfg.lastPulledAt)
        assertNull(cfg.lastPushedAt)
    }

    @Test
    fun `loadConfig reads stored values`() {
        val repo = FakeSettingsRepository()
        repo.set(SyncService.Keys.ENABLED, "false")
        repo.set(SyncService.Keys.SERVER_MODE, "true")
        repo.set(SyncService.Keys.HOST, "192.168.1.10")
        repo.set(SyncService.Keys.PORT, "9000")
        repo.set(SyncService.Keys.LAST_PULLED_AT, "2025-01-01T10:00:00Z")
        repo.set(SyncService.Keys.LAST_PUSHED_AT, "2025-01-02T11:00:00Z")

        val service = SyncService(repo)

        val cfg = service.loadConfig()

        assertFalse(cfg.enabled)
        assertTrue(cfg.serverMode)
        assertEquals("192.168.1.10", cfg.host)
        assertEquals(9000, cfg.port)
        assertEquals(Instant.parse("2025-01-01T10:00:00Z"), cfg.lastPulledAt)
        assertEquals(Instant.parse("2025-01-02T11:00:00Z"), cfg.lastPushedAt)
    }

    @Test
    fun `loadConfig uses default port when stored port is invalid`() {
        val repo = FakeSettingsRepository()
        repo.set(SyncService.Keys.PORT, "abc")

        val service = SyncService(repo)

        val cfg = service.loadConfig()

        assertEquals(8085, cfg.port)
    }

    @Test
    fun `loadConfig treats blank timestamps as null`() {
        val repo = FakeSettingsRepository()
        repo.set(SyncService.Keys.LAST_PULLED_AT, "")
        repo.set(SyncService.Keys.LAST_PUSHED_AT, "   ")

        val service = SyncService(repo)

        val cfg = service.loadConfig()

        assertNull(cfg.lastPulledAt)
        assertNull(cfg.lastPushedAt)
    }

    @Test
    fun `saveConfig stores all values`() {
        val repo = FakeSettingsRepository()
        val service = SyncService(repo)

        val cfg = SyncService.SyncConfig(
            enabled = false,
            serverMode = true,
            host = "server.local",
            port = 9090,
            lastPulledAt = Instant.parse("2025-01-03T08:00:00Z"),
            lastPushedAt = Instant.parse("2025-01-04T09:30:00Z")
        )

        service.saveConfig(cfg)

        assertEquals("false", repo.get(SyncService.Keys.ENABLED))
        assertEquals("true", repo.get(SyncService.Keys.SERVER_MODE))
        assertEquals("server.local", repo.get(SyncService.Keys.HOST))
        assertEquals("9090", repo.get(SyncService.Keys.PORT))
        assertEquals("2025-01-03T08:00:00Z", repo.get(SyncService.Keys.LAST_PULLED_AT))
        assertEquals("2025-01-04T09:30:00Z", repo.get(SyncService.Keys.LAST_PUSHED_AT))
    }

    @Test
    fun `saveConfig stores empty strings for null timestamps`() {
        val repo = FakeSettingsRepository()
        val service = SyncService(repo)

        val cfg = SyncService.SyncConfig(
            enabled = true,
            serverMode = false,
            host = "localhost",
            port = 8085,
            lastPulledAt = null,
            lastPushedAt = null
        )

        service.saveConfig(cfg)

        assertEquals("", repo.get(SyncService.Keys.LAST_PULLED_AT))
        assertEquals("", repo.get(SyncService.Keys.LAST_PUSHED_AT))
    }

    @Test
    fun `syncNow returns disabled when sync is turned off`() {
        val repo = FakeSettingsRepository()
        repo.set(SyncService.Keys.ENABLED, "false")
        val service = SyncService(repo)

        val result = service.syncNow()

        assertIs<SyncService.SyncResult.Disabled>(result)
    }

    @Test
    fun `syncNow returns not implemented result when enabled`() {
        val repo = FakeSettingsRepository()
        repo.set(SyncService.Keys.ENABLED, "true")
        repo.set(SyncService.Keys.HOST, "example.org")
        val service = SyncService(repo)

        val result = service.syncNow()

        val typed = assertIs<SyncService.SyncResult.NotImplementedYet>(result)
        assertEquals("example.org", typed.cfg.host)
        assertTrue(typed.cfg.enabled)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val data = mutableMapOf<String, String>()

        override fun get(key: String): String? = data[key]

        override fun set(key: String, value: String) {
            data[key] = value
        }

        override fun delete(key: String) {
            data.remove(key)
        }
    }
}