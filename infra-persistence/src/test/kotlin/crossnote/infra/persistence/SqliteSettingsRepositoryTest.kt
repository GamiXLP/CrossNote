package crossnote.infra.persistence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqliteSettingsRepositoryTest {

    @Test
    fun `set and get setting works`() {
        createDatabase().use { db ->
            val repo = SqliteSettingsRepository(db)

            repo.set("theme", "dark")

            assertEquals("dark", repo.get("theme"))
        }
    }

    @Test
    fun `get returns null when key does not exist`() {
        createDatabase().use { db ->
            val repo = SqliteSettingsRepository(db)

            assertNull(repo.get("missing"))
        }
    }

    @Test
    fun `delete removes setting`() {
        createDatabase().use { db ->
            val repo = SqliteSettingsRepository(db)
            repo.set("theme", "dark")

            repo.delete("theme")

            assertNull(repo.get("theme"))
        }
    }

    private fun createDatabase(): SqliteDatabase {
        val tempDir = Files.createTempDirectory("crossnote-test-settings")
        return SqliteDatabase(tempDir.resolve("test.db"))
    }

    @Test
    fun `set updates existing value`() {
        createDatabase().use { db ->
            val repo = SqliteSettingsRepository(db)

            repo.set("theme", "dark")
            repo.set("theme", "light")

            assertEquals("light", repo.get("theme"))
        }
    }

    @Test
    fun `delete on missing key keeps repository stable`() {
        createDatabase().use { db ->
            val repo = SqliteSettingsRepository(db)

            repo.delete("missing")

            assertNull(repo.get("missing"))
        }
    }
}