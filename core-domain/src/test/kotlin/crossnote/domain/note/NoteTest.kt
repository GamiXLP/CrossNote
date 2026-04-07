package crossnote.domain.note

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteTest {

    private val createdAt = Instant.parse("2025-01-01T10:00:00Z")
    private val updatedAt = Instant.parse("2025-01-01T10:00:00Z")
    private val trashTime = Instant.parse("2025-01-02T12:00:00Z")

    @Test
    fun `moveToTrash sets trashedAt and updatedAt`() {
        val note = createNote()

        val trashed = note.moveToTrash(trashTime)

        assertEquals(trashTime, trashed.trashedAt)
        assertEquals(trashTime, trashed.updatedAt)
    }

    @Test
    fun `restore clears trashedAt and updates updatedAt`() {
        val note = createNote(trashedAt = Instant.parse("2025-01-02T08:00:00Z"))

        val restored = note.restore(trashTime)

        assertNull(restored.trashedAt)
        assertEquals(trashTime, restored.updatedAt)
    }

    @Test
    fun `isTrashed returns true when trashedAt exists`() {
        val note = createNote(trashedAt = trashTime)

        assertTrue(note.isTrashed())
    }

    @Test
    fun `isTrashed returns false when trashedAt is null`() {
        val note = createNote()

        assertFalse(note.isTrashed())
    }

    @Test
    fun `isInRoot returns true when notebookId is null`() {
        val note = createNote(notebookId = null)

        assertTrue(note.isInRoot())
    }

    @Test
    fun `isInRoot returns false when notebookId exists`() {
        val note = createNote(notebookId = NotebookId("nb-1"))

        assertFalse(note.isInRoot())
    }

    @Test
    fun `belongsTo returns true for matching notebook`() {
        val notebookId = NotebookId("nb-1")
        val note = createNote(notebookId = notebookId)

        assertTrue(note.belongsTo(notebookId))
    }

    @Test
    fun `belongsTo returns false for different notebook`() {
        val note = createNote(notebookId = NotebookId("nb-1"))

        assertFalse(note.belongsTo(NotebookId("nb-2")))
    }

    private fun createNote(
        notebookId: NotebookId? = null,
        trashedAt: Instant? = null
    ): Note {
        return Note(
            id = NoteId("note-1"),
            notebookId = notebookId,
            title = "Titel",
            content = "Inhalt",
            createdAt = createdAt,
            updatedAt = updatedAt,
            trashedAt = trashedAt
        )
    }
}