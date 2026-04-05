package crossnote.domain.note

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotebookTest {

    @Test
    fun `notebook stores all values correctly`() {
        val id = NotebookId("nb-1")
        val parentId = NotebookId("parent")
        val trashedAt = Instant.parse("2025-01-01T10:00:00Z")

        val notebook = Notebook(
            id = id,
            name = "Meine Notizen",
            parentId = parentId,
            trashedAt = trashedAt
        )

        assertEquals(id, notebook.id)
        assertEquals("Meine Notizen", notebook.name)
        assertEquals(parentId, notebook.parentId)
        assertEquals(trashedAt, notebook.trashedAt)
    }

    @Test
    fun `notebook allows null parent and trashedAt`() {
        val notebook = Notebook(
            id = NotebookId("nb-2"),
            name = "Root"
        )

        assertNull(notebook.parentId)
        assertNull(notebook.trashedAt)
    }
}