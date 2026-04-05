package crossnote.domain.note

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextConstraintsTest {

    @Test
    fun `validateNoteTitle trims whitespace`() {
        val result = validateNoteTitle("   Hallo Welt   ")

        assertEquals("Hallo Welt", result)
    }

    @Test
    fun `validateNoteTitle allows empty title`() {
        val result = validateNoteTitle("     ")

        assertEquals("", result)
    }

    @Test
    fun `validateNoteTitle throws when title is too long`() {
        val tooLong = "a".repeat(TextConstraints.NOTE_TITLE_MAX + 1)

        assertFailsWith<ValidationException> {
            validateNoteTitle(tooLong)
        }
    }

    @Test
    fun `validateNotebookName trims whitespace`() {
        val result = validateNotebookName("   Studium   ")

        assertEquals("Studium", result)
    }

    @Test
    fun `validateNotebookName throws when empty after trim`() {
        assertFailsWith<ValidationException> {
            validateNotebookName("    ")
        }
    }

    @Test
    fun `validateNotebookName throws when name is too long`() {
        val tooLong = "a".repeat(TextConstraints.NOTEBOOK_NAME_MAX + 1)

        assertFailsWith<ValidationException> {
            validateNotebookName(tooLong)
        }
    }
}