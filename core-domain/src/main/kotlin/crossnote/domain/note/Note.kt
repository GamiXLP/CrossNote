package crossnote.domain.note

import java.time.Instant

data class NoteId(val value: String)
// data class NotebookId(val value: String)

/**
 * notebookId:
 *  - null  = Root (keinem Ordner zugeordnet)
 *  - !=null = gehört zu einem Ordner
 */
data class Note(
    val id: NoteId,
    val notebookId: NotebookId?,   // ⭐ NEU
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

    fun isInRoot(): Boolean = notebookId == null

    fun belongsTo(notebookId: NotebookId): Boolean =
        this.notebookId == notebookId
}

interface NoteRepository {
    fun save(note: Note)
    fun findById(id: NoteId): Note?
    fun findAll(): List<Note>
    fun deleteById(id: NoteId)
}

interface IdGenerator {
    fun newId(): NoteId
}

interface Clock {
    fun now(): Instant
}