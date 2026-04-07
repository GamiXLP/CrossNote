package crossnote.infra.persistence

import crossnote.domain.note.Note
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SqliteNoteRepositoryTest {

    @Test
    fun `save and findById works`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            val note = createNote(id = "n1")

            repo.save(note)

            val result = repo.findById(NoteId("n1"))

            assertNotNull(result)
            assertEquals("Titel", result.title)
        }
    }

    @Test
    fun `save updates existing note`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            val original = createNote(id = "n1")
            val updated = original.copy(title = "Neu")

            repo.save(original)
            repo.save(updated)

            val result = repo.findById(NoteId("n1"))

            assertNotNull(result)
            assertEquals("Neu", result.title)
        }
    }

    @Test
    fun `findAll returns all notes`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            repo.save(createNote(id = "n1"))
            repo.save(createNote(id = "n2", notebookId = NotebookId("nb1")))

            val result = repo.findAll()

            assertEquals(2, result.size)
        }
    }

    @Test
    fun `deleteById removes note`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            repo.save(createNote(id = "n1"))

            repo.deleteById(NoteId("n1"))

            assertNull(repo.findById(NoteId("n1")))
        }
    }

    @Test
    fun `listRootNoteSummaries returns only active root notes sorted by updatedAt descending`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)

            repo.save(
                createNote(
                    id = "n1",
                    title = "A",
                    updatedAt = Instant.parse("2025-01-01T10:00:00Z")
                )
            )
            repo.save(
                createNote(
                    id = "n2",
                    title = "B",
                    updatedAt = Instant.parse("2025-01-02T10:00:00Z")
                )
            )
            repo.save(
                createNote(
                    id = "n3",
                    notebookId = NotebookId("nb1")
                )
            )
            repo.save(
                createNote(
                    id = "n4",
                    trashedAt = Instant.parse("2025-01-03T10:00:00Z")
                )
            )

            val result = repo.listRootNoteSummaries()

            assertEquals(2, result.size)
            assertEquals("n2", result[0].id)
            assertEquals("n1", result[1].id)
        }
    }

    @Test
    fun `listNoteSummariesInNotebook returns only active notes in notebook sorted by updatedAt descending`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            val notebookId = NotebookId("nb1")

            repo.save(createNote(id = "n1", notebookId = notebookId))
            repo.save(
                createNote(
                    id = "n2",
                    notebookId = notebookId,
                    updatedAt = Instant.parse("2025-01-03T10:00:00Z")
                )
            )
            repo.save(createNote(id = "n3", notebookId = NotebookId("other")))
            repo.save(
                createNote(
                    id = "n4",
                    notebookId = notebookId,
                    trashedAt = Instant.parse("2025-01-04T10:00:00Z")
                )
            )

            val result = repo.listNoteSummariesInNotebook(notebookId)

            assertEquals(2, result.size)
            assertEquals("n2", result[0].id)
            assertEquals("n1", result[1].id)
        }
    }

    @Test
    fun `setTrashedAt updates trashed flag`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            val noteId = NoteId("n1")
            val trashedAt = Instant.parse("2025-01-05T10:00:00Z")

            repo.save(createNote(id = "n1"))

            repo.setTrashedAt(noteId, trashedAt)

            assertEquals(trashedAt, repo.findById(noteId)?.trashedAt)
        }
    }

    @Test
    fun `findNotebookIdOfNote returns notebook id`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            repo.save(createNote(id = "n1", notebookId = NotebookId("nb1")))

            val result = repo.findNotebookIdOfNote(NoteId("n1"))

            assertEquals(NotebookId("nb1"), result)
        }
    }

    private fun createDatabase(): SqliteDatabase {
        val tempDir = Files.createTempDirectory("crossnote-test-note")
        return SqliteDatabase(tempDir.resolve("test.db"))
    }

    private fun createNote(
        id: String,
        title: String = "Titel",
        notebookId: NotebookId? = null,
        updatedAt: Instant = Instant.parse("2025-01-01T10:00:00Z"),
        trashedAt: Instant? = null
    ): Note {
        val createdAt = Instant.parse("2025-01-01T09:00:00Z")
        return Note(
            id = NoteId(id),
            notebookId = notebookId,
            title = title,
            content = "Inhalt",
            createdAt = createdAt,
            updatedAt = updatedAt,
            trashedAt = trashedAt
        )
    }

    @Test
    fun `findById returns null when note does not exist`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)

            val result = repo.findById(NoteId("missing"))

            assertNull(result)
        }
    }

    @Test
    fun `setTrashedAt can clear trashed flag`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            val noteId = NoteId("n1")
            val trashedAt = Instant.parse("2025-01-05T10:00:00Z")

            repo.save(createNote(id = "n1", trashedAt = trashedAt))

            repo.setTrashedAt(noteId, null)

            assertNull(repo.findById(noteId)?.trashedAt)
        }
    }

    @Test
    fun `findNotebookIdOfNote returns null for root note`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)
            repo.save(createNote(id = "n1", notebookId = null))

            val result = repo.findNotebookIdOfNote(NoteId("n1"))

            assertNull(result)
        }
    }

    @Test
    fun `findNotebookIdOfNote returns null when note does not exist`() {
        createDatabase().use { db ->
            val repo = SqliteNoteRepository(db)

            val result = repo.findNotebookIdOfNote(NoteId("missing"))

            assertNull(result)
        }
    }
}