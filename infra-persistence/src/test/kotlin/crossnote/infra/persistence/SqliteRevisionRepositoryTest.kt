package crossnote.infra.persistence

import crossnote.domain.note.Note
import crossnote.domain.note.NoteId
import crossnote.domain.revision.Revision
import crossnote.domain.revision.RevisionId
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SqliteRevisionRepositoryTest {

    @Test
    fun `save and findById works`() {
        createDatabase().use { db ->
            val noteRepo = SqliteNoteRepository(db)
            val repo = SqliteRevisionRepository(db)

            noteRepo.save(createNote("n1"))
            repo.save(createRevision("r1", "n1"))

            val result = repo.findById(RevisionId("r1"))

            assertNotNull(result)
            assertEquals("r1", result.id.value)
        }
    }

    @Test
    fun `findByNoteId returns revisions sorted descending`() {
        createDatabase().use { db ->
            val noteRepo = SqliteNoteRepository(db)
            val repo = SqliteRevisionRepository(db)

            noteRepo.save(createNote("n1"))
            noteRepo.save(createNote("n2"))

            repo.save(createRevision("r1", "n1", Instant.parse("2025-01-01T10:00:00Z")))
            repo.save(createRevision("r2", "n1", Instant.parse("2025-01-02T10:00:00Z")))
            repo.save(createRevision("r3", "n2", Instant.parse("2025-01-03T10:00:00Z")))

            val result = repo.findByNoteId(NoteId("n1"))

            assertEquals(2, result.size)
            assertEquals("r2", result[0].id.value)
            assertEquals("r1", result[1].id.value)
        }
    }

    @Test
    fun `deleteById removes revision`() {
        createDatabase().use { db ->
            val noteRepo = SqliteNoteRepository(db)
            val repo = SqliteRevisionRepository(db)

            noteRepo.save(createNote("n1"))
            repo.save(createRevision("r1", "n1"))

            repo.deleteById(RevisionId("r1"))

            assertNull(repo.findById(RevisionId("r1")))
        }
    }

    @Test
    fun `deleteByNoteId removes all revisions of note`() {
        createDatabase().use { db ->
            val noteRepo = SqliteNoteRepository(db)
            val repo = SqliteRevisionRepository(db)

            noteRepo.save(createNote("n1"))
            noteRepo.save(createNote("n2"))

            repo.save(createRevision("r1", "n1"))
            repo.save(createRevision("r2", "n1"))
            repo.save(createRevision("r3", "n2"))

            repo.deleteByNoteId(NoteId("n1"))

            assertEquals(0, repo.findByNoteId(NoteId("n1")).size)
            assertNotNull(repo.findById(RevisionId("r3")))
        }
    }

    private fun createDatabase(): SqliteDatabase {
        val tempDir = Files.createTempDirectory("crossnote-test-revision")
        return SqliteDatabase(tempDir.resolve("test.db"))
    }

    private fun createNote(id: String): Note {
        val now = Instant.parse("2025-01-01T10:00:00Z")
        return Note(
            id = NoteId(id),
            notebookId = null,
            title = "Titel",
            content = "Inhalt",
            createdAt = now,
            updatedAt = now,
            trashedAt = null
        )
    }

    private fun createRevision(
        id: String,
        noteId: String,
        createdAt: Instant = Instant.parse("2025-01-01T10:00:00Z")
    ): Revision {
        return Revision(
            id = RevisionId(id),
            noteId = NoteId(noteId),
            title = "Titel",
            content = "Inhalt",
            createdAt = createdAt
        )
    }

    @Test
    fun `findById returns null when revision does not exist`() {
        createDatabase().use { db ->
            val repo = SqliteRevisionRepository(db)

            val result = repo.findById(RevisionId("missing"))

            assertNull(result)
        }
    }

    @Test
    fun `findByNoteId returns empty list when note has no revisions`() {
        createDatabase().use { db ->
            val noteRepo = SqliteNoteRepository(db)
            val repo = SqliteRevisionRepository(db)

            noteRepo.save(createNote("n1"))

            val result = repo.findByNoteId(NoteId("n1"))

            assertEquals(0, result.size)
        }
    }
}