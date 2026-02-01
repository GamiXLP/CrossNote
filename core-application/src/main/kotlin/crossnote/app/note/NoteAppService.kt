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
            updatedAt = now,
            trashedAt = null
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

    fun moveToTrash(id: NoteId) {
        val existing = repo.findById(id) ?: error("Note not found: ${id.value}")
        val updated = existing.moveToTrash(clock.now())
        repo.save(updated)
    }

    fun restore(id: NoteId) {
        val existing = repo.findById(id) ?: error("Note not found: ${id.value}")
        val updated = existing.restore(clock.now())
        repo.save(updated)
    }

    fun listActiveNotes(): List<NoteSummaryDto> =
        repo.findAll()
            .asSequence()
            .filter { !it.isTrashed() }
            .sortedByDescending { it.updatedAt }
            .map { NoteSummaryDto(it.id.value, it.title.ifBlank { "(Ohne Titel)" }) }
            .toList()

    fun listTrashedNotes(): List<NoteSummaryDto> =
        repo.findAll()
            .asSequence()
            .filter { it.isTrashed() }
            .sortedByDescending { it.updatedAt }
            .map { NoteSummaryDto(it.id.value, it.title.ifBlank { "(Ohne Titel)" }) }
            .toList()
}