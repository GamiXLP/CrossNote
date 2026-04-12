package crossnote.domain.note

import kotlin.test.Test
import kotlin.test.assertEquals

class IdsTest {

    @Test
    fun `noteId stores value`() {
        val id = NoteId("123")
        assertEquals("123", id.value)
    }

    @Test
    fun `notebookId stores value`() {
        val id = NotebookId("abc")
        assertEquals("abc", id.value)
    }

    @Test
    fun `noteId equality works`() {
        val a = NoteId("x")
        val b = NoteId("x")

        assertEquals(a, b)
    }

    @Test
    fun `notebookId equality works`() {
        val a = NotebookId("y")
        val b = NotebookId("y")

        assertEquals(a, b)
    }
}