package crossnote.app.note

import kotlin.test.Test
import kotlin.test.assertEquals

class DtoTest {

    @Test
    fun `revisionSummaryDto stores values`() {
        val dto = RevisionSummaryDto(
            id = "rev-1",
            createdAtIso = "2025-01-01T10:00:00Z"
        )

        assertEquals("rev-1", dto.id)
        assertEquals("2025-01-01T10:00:00Z", dto.createdAtIso)
    }

    @Test
    fun `notebookWithNotesDto stores values`() {
        val dto = NotebookWithNotesDto(
            id = "nb-1",
            name = "Test",
            notes = emptyList()
        )

        assertEquals("nb-1", dto.id)
        assertEquals("Test", dto.name)
        assertEquals(0, dto.notes.size)
    }
}