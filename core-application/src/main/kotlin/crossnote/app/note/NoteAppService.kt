package crossnote.app.note

import crossnote.domain.note.*

data class NoteSummaryDto(val id: String, val title: String)

class NoteAppService(
    private val repo: NoteRepository,
    private val ids: IdGenerator,
    private val clock: Clock
) {
    fun createNote(title: String, content: String): NoteId {
        val now = clock.now()
        val note = Note(
            id = ids.newId(),
            title = title.trim(),
            content = content,
            createdAt = now,
            updatedAt = now
        )
        repo.save(note)
        return note.id
    }

    fun updateNote(id: NoteId, title: String, content: String) {
        val existing = repo.findById(id) ?: error("Note not found: ${id.value}")
        val now = clock.now()
        val updated = existing.copy(
            title = title.trim(),
            content = content,
            updatedAt = now
        )
        repo.save(updated)
    }

    fun getNote(id: NoteId): Note =
        repo.findById(id) ?: error("Note not found: ${id.value}")

    fun listNotes(): List<NoteSummaryDto> =
        repo.findAll()
            .sortedByDescending { it.updatedAt }
            .map { note ->
                NoteSummaryDto(
                    id = note.id.value,
                    title = note.title.ifBlank { "(Ohne Titel)" }
                )
            }
}