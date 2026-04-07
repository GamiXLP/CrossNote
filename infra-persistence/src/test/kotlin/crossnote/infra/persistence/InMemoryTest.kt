package crossnote.infra.persistence

import crossnote.domain.note.Note
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import crossnote.domain.revision.Revision
import crossnote.domain.revision.RevisionId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryTest {

    @Test
    fun `in memory note repository saves and finds note`() {
        val repo = InMemoryNoteRepository()
        val note = createNote(id = "n1")

        repo.save(note)

        val result = repo.findById(NoteId("n1"))

        assertNotNull(result)
        assertEquals("n1", result.id.value)
        assertEquals("Titel", result.title)
    }

    @Test
    fun `in memory note repository returns all notes`() {
        val repo = InMemoryNoteRepository()
        repo.save(createNote(id = "n1"))
        repo.save(createNote(id = "n2"))

        val result = repo.findAll()

        assertEquals(2, result.size)
    }

    @Test
    fun `in memory note repository deletes note`() {
        val repo = InMemoryNoteRepository()
        repo.save(createNote(id = "n1"))

        repo.deleteById(NoteId("n1"))

        assertNull(repo.findById(NoteId("n1")))
    }

    @Test
    fun `uuid id generator creates non blank ids`() {
        val generator = UuidIdGenerator()

        val id1 = generator.newId()
        val id2 = generator.newId()

        assertTrue(id1.value.isNotBlank())
        assertTrue(id2.value.isNotBlank())
        assertTrue(id1 != id2)
    }

    @Test
    fun `system clock returns a time`() {
        val clock = SystemClock()

        val now = clock.now()

        assertNotNull(now)
    }

    @Test
    fun `in memory revision repository saves and finds revision`() {
        val repo = InMemoryRevisionRepository()
        val revision = createRevision(id = "r1", noteId = "n1")

        repo.save(revision)

        val result = repo.findById(RevisionId("r1"))

        assertNotNull(result)
        assertEquals("r1", result.id.value)
        assertEquals("n1", result.noteId.value)
    }

    @Test
    fun `in memory revision repository finds revisions by note id sorted descending`() {
        val repo = InMemoryRevisionRepository()

        repo.save(
            createRevision(
                id = "r1",
                noteId = "n1",
                createdAt = Instant.parse("2025-01-01T10:00:00Z")
            )
        )
        repo.save(
            createRevision(
                id = "r2",
                noteId = "n1",
                createdAt = Instant.parse("2025-01-02T10:00:00Z")
            )
        )
        repo.save(createRevision(id = "r3", noteId = "other"))

        val result = repo.findByNoteId(NoteId("n1"))

        assertEquals(2, result.size)
        assertEquals("r2", result[0].id.value)
        assertEquals("r1", result[1].id.value)
    }

    @Test
    fun `in memory revision repository deletes by id`() {
        val repo = InMemoryRevisionRepository()
        repo.save(createRevision(id = "r1", noteId = "n1"))

        repo.deleteById(RevisionId("r1"))

        assertNull(repo.findById(RevisionId("r1")))
    }

    @Test
    fun `in memory revision repository deletes all revisions of note`() {
        val repo = InMemoryRevisionRepository()
        repo.save(createRevision(id = "r1", noteId = "n1"))
        repo.save(createRevision(id = "r2", noteId = "n1"))
        repo.save(createRevision(id = "r3", noteId = "n2"))

        repo.deleteByNoteId(NoteId("n1"))

        assertEquals(0, repo.findByNoteId(NoteId("n1")).size)
        assertNotNull(repo.findById(RevisionId("r3")))
    }

    private fun createNote(
        id: String,
        notebookId: NotebookId? = null
    ): Note {
        val now = Instant.parse("2025-01-01T10:00:00Z")
        return Note(
            id = NoteId(id),
            notebookId = notebookId,
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
}