package crossnote.domain.revision

import crossnote.domain.note.NoteId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RevisionTest {

    @Test
    fun `revision stores all values correctly`() {
        val id = RevisionId("rev-1")
        val noteId = NoteId("note-1")
        val createdAt = Instant.parse("2025-01-01T10:00:00Z")

        val revision = Revision(
            id = id,
            noteId = noteId,
            title = "Titel",
            content = "Inhalt",
            createdAt = createdAt
        )

        assertEquals(id, revision.id)
        assertEquals(noteId, revision.noteId)
        assertEquals("Titel", revision.title)
        assertEquals("Inhalt", revision.content)
        assertEquals(createdAt, revision.createdAt)
    }

    @Test
    fun `revisionId stores value correctly`() {
        val id = RevisionId("abc-123")

        assertEquals("abc-123", id.value)
    }
}