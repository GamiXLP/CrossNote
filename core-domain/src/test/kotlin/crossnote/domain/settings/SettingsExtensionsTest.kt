package crossnote.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsExtensionsTest {

    @Test
    fun `setBoolean stores boolean as string`() {
        val repo = FakeSettingsRepository()

        repo.setBoolean("feature.enabled", true)

        assertEquals("true", repo.get("feature.enabled"))
    }

    @Test
    fun `getBoolean returns stored true value`() {
        val repo = FakeSettingsRepository()
        repo.set("feature.enabled", "true")

        val result = repo.getBoolean("feature.enabled", default = false)

        assertTrue(result)
    }

    @Test
    fun `getBoolean returns stored false value`() {
        val repo = FakeSettingsRepository()
        repo.set("feature.enabled", "false")

        val result = repo.getBoolean("feature.enabled", default = true)

        assertFalse(result)
    }

    @Test
    fun `getBoolean returns default when key is missing`() {
        val repo = FakeSettingsRepository()

        val result = repo.getBoolean("missing.key", default = true)

        assertTrue(result)
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