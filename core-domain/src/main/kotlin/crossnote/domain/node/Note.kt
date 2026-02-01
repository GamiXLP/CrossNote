package crossnote.domain.note

import java.time.Instant

data class NoteId(val value: String)

data class Note(
    val id: NoteId,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

interface NoteRepository {
    fun save(note: Note)
    fun findById(id: NoteId): Note?
    fun findAll(): List<Note>
}

interface IdGenerator {
    fun newId(): NoteId
}

interface Clock {
    fun now(): Instant
}