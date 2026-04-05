package crossnote.infra.persistence

import kotlin.test.Test
import kotlin.test.assertEquals

class SqliteNoteRepositoryDtoTest {

    @Test
    fun `note summary stores values`() {
        val summary = SqliteNoteRepository.NoteSummary(
            id = "n1",
            title = "Titel"
        )

        assertEquals("n1", summary.id)
        assertEquals("Titel", summary.title)
    }
}