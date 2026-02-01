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
        val note = Note(ids.newId(), title, content, now, now)
        repo.save(note)
        return note.id
    }

    fun listNotes(): List<NoteSummaryDto> =
        repo.findAll().map { NoteSummaryDto(it.id.value, it.title.ifBlank { "(Ohne Titel)" }) }
}