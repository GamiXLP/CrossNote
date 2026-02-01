package crossnote.domain.note

import java.time.Instant

data class NoteId(val value: String)

data class Note(
    val id: NoteId,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val trashedAt: Instant?
) {
    fun moveToTrash(now: Instant): Note =
        copy(trashedAt = now, updatedAt = now)

    fun restore(now: Instant): Note =
        copy(trashedAt = null, updatedAt = now)

    fun isTrashed(): Boolean = trashedAt != null
}

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