package crossnote.infra.persistence

import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteNotebookRepositoryTest {

    @Test
    fun `save and findById works`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val notebook = Notebook(
                id = NotebookId("nb1"),
                name = "Test"
            )

            repo.save(notebook)

            val result = repo.findById(NotebookId("nb1"))

            assertNotNull(result)
            assertEquals("Test", result.name)
        }
    }

    @Test
    fun `findAll returns only non trashed notebooks`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            repo.save(Notebook(NotebookId("a"), "A"))
            repo.save(
                Notebook(
                    NotebookId("b"),
                    "B",
                    trashedAt = Instant.parse("2025-01-01T10:00:00Z")
                )
            )

            val result = repo.findAll()

            assertEquals(1, result.size)
            assertEquals("a", result[0].id.value)
        }
    }

    @Test
    fun `findAllIncludingTrashed returns all`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            repo.save(Notebook(NotebookId("a"), "A"))
            repo.save(
                Notebook(
                    NotebookId("b"),
                    "B",
                    trashedAt = Instant.parse("2025-01-01T10:00:00Z")
                )
            )

            val result = repo.findAllIncludingTrashed()

            assertEquals(2, result.size)
        }
    }

    @Test
    fun `moveToTrash sets trashedAt`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val id = NotebookId("nb1")
            repo.save(Notebook(id, "Test"))

            val now = Instant.parse("2025-01-01T10:00:00Z")
            repo.moveToTrash(id, now)

            assertTrue(repo.isTrashed(id))
        }
    }

    @Test
    fun `restoreFromTrash clears trashedAt`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val id = NotebookId("nb1")
            val now = Instant.parse("2025-01-01T10:00:00Z")

            repo.save(Notebook(id, "Test", trashedAt = now))

            repo.restoreFromTrash(id)

            assertTrue(!repo.isTrashed(id))
        }
    }

    @Test
    fun `isTrashed returns correct value`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val id = NotebookId("nb1")
            repo.save(Notebook(id, "Test"))

            assertTrue(!repo.isTrashed(id))

            repo.moveToTrash(id, Instant.now())

            assertTrue(repo.isTrashed(id))
        }
    }

    @Test
    fun `findParentId returns parent`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val parent = NotebookId("parent")
            val child = NotebookId("child")

            repo.save(Notebook(parent, "Parent"))
            repo.save(Notebook(child, "Child", parentId = parent))

            val result = repo.findParentId(child)

            assertEquals(parent, result)
        }
    }

    @Test
    fun `moveNotebook changes parent`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val a = NotebookId("a")
            val b = NotebookId("b")

            repo.save(Notebook(a, "A"))
            repo.save(Notebook(b, "B"))

            repo.moveNotebook(a, b)

            val parent = repo.findParentId(a)

            assertEquals(b, parent)
        }
    }

    @Test
    fun `delete removes notebook`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val id = NotebookId("nb1")
            repo.save(Notebook(id, "Test"))

            repo.delete(id)

            assertNull(repo.findById(id))
        }
    }

    @Test
    fun `findAllTrashed returns only trashed notebooks`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            repo.save(Notebook(NotebookId("a"), "A"))
            repo.save(
                Notebook(
                    NotebookId("b"),
                    "B",
                    trashedAt = Instant.parse("2025-01-01T10:00:00Z")
                )
            )

            val result = repo.findAllTrashed()

            assertEquals(1, result.size)
            assertEquals("b", result[0].id.value)
        }
    }

    private fun createDb(): SqliteDatabase {
        val dir = Files.createTempDirectory("test-db")
        return SqliteDatabase(dir.resolve("test.db"))
    }

    @Test
    fun `findById returns null when notebook does not exist`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val result = repo.findById(NotebookId("missing"))

            assertNull(result)
        }
    }

    @Test
    fun `findParentId returns null when notebook has no parent`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val id = NotebookId("root")
            repo.save(Notebook(id, "Root"))

            val result = repo.findParentId(id)

            assertNull(result)
        }
    }

    @Test
    fun `findParentId returns null when notebook does not exist`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val result = repo.findParentId(NotebookId("missing"))

            assertNull(result)
        }
    }

    @Test
    fun `isTrashed returns false when notebook does not exist`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val result = repo.isTrashed(NotebookId("missing"))

            assertTrue(!result)
        }
    }

    @Test
    fun `moveNotebook can move notebook to root`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val parent = NotebookId("parent")
            val child = NotebookId("child")

            repo.save(Notebook(parent, "Parent"))
            repo.save(Notebook(child, "Child", parentId = parent))

            repo.moveNotebook(child, null)

            assertNull(repo.findParentId(child))
        }
    }

    @Test
    fun `save updates existing notebook`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            val id = NotebookId("nb1")
            repo.save(Notebook(id, "Alt"))
            repo.save(
                Notebook(
                    id = id,
                    name = "Neu",
                    parentId = NotebookId("parent"),
                    trashedAt = Instant.parse("2025-01-01T10:00:00Z")
                )
            )

            val result = repo.findById(id)

            assertNotNull(result)
            assertEquals("Neu", result.name)
            assertEquals(NotebookId("parent"), result.parentId)
            assertEquals(Instant.parse("2025-01-01T10:00:00Z"), result.trashedAt)
        }
    }

    @Test
    fun `findAllTrashed returns empty list when none are trashed`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            repo.save(Notebook(NotebookId("a"), "A"))
            repo.save(Notebook(NotebookId("b"), "B"))

            val result = repo.findAllTrashed()

            assertEquals(0, result.size)
        }
    }

    @Test
    fun `findAll returns notebooks ordered by name`() {
        createDb().use { db ->
            val repo = SqliteNotebookRepository(db)

            repo.save(Notebook(NotebookId("b"), "Zeta"))
            repo.save(Notebook(NotebookId("a"), "Alpha"))

            val result = repo.findAll()

            assertEquals(listOf("Alpha", "Zeta"), result.map { it.name })
        }
    }
}