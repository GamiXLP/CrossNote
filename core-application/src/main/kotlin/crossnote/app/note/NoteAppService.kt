package crossnote.app.note

import crossnote.domain.note.*
import crossnote.domain.revision.*
import java.time.Duration
import java.util.UUID

data class NoteSummaryDto(val id: String, val title: String)
data class RevisionSummaryDto(val id: String, val createdAtIso: String)

data class NotebookTreeDto(
    val rootNotes: List<NoteSummaryDto>,
    val notebooks: List<NotebookNodeDto>
)

data class NotebookNodeDto(
    val id: String,
    val name: String,
    val notes: List<NoteSummaryDto>,
    val subNotebooks: List<NotebookNodeDto>
)

class NoteAppService(
    private val repo: NoteRepository,
    private val revisionRepo: RevisionRepository,
    private val ids: IdGenerator,
    private val clock: Clock
) {

    /**
     * notebookId = null  -> Root-Notiz
     * notebookId != null -> Notiz in Ordner
     */
    fun createNote(
        title: String,
        content: String,
        notebookId: NotebookId? = null
    ): NoteId {
        val now = clock.now()
        val note = Note(
            id = ids.newId(),
            notebookId = notebookId,
            title = validateNoteTitle(title),
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

        // Revision = Zustand VOR der Änderung
        saveRevisionSnapshot(existing)

        val now = clock.now()
        val updated = existing.copy(
            title = validateNoteTitle(title),
            content = content,
            updatedAt = now
        )
        repo.save(updated)
    }

    fun getNote(id: NoteId): Note =
        repo.findById(id) ?: error("Note not found: ${id.value}")

    fun moveToTrash(id: NoteId) {
        val existing = repo.findById(id) ?: error("Note not found: ${id.value}")
        repo.save(existing.moveToTrash(clock.now()))
    }

    fun restore(id: NoteId) {
        val existing = repo.findById(id) ?: error("Note not found: ${id.value}")
        repo.save(existing.restore(clock.now()))
    }

    fun listActiveNotes(): List<NoteSummaryDto> =
        repo.findAll()
            .asSequence()
            .filter { !it.isTrashed() }
            .sortedByDescending { it.updatedAt }
            .map { it.toSummary() }
            .toList()

    fun listTrashedNotes(): List<NoteSummaryDto> =
        repo.findAll()
            .asSequence()
            .filter { it.isTrashed() }
            .sortedByDescending { it.updatedAt }
            .map { it.toSummary() }
            .toList()

    // ---------- Revision Use-Cases ----------

    fun listRevisions(noteId: NoteId): List<RevisionSummaryDto> =
        revisionRepo.findByNoteId(noteId)
            .sortedByDescending { it.createdAt }
            .map { RevisionSummaryDto(it.id.value, it.createdAt.toString()) }

    fun restoreFromRevision(noteId: NoteId, revisionId: RevisionId) {
        val note = repo.findById(noteId) ?: error("Note not found: ${noteId.value}")
        val rev = revisionRepo.findById(revisionId) ?: error("Revision not found: ${revisionId.value}")

        if (rev.noteId != noteId) {
            error("Revision does not belong to note: ${revisionId.value}")
        }

        // Revision vom aktuellen Zustand speichern (damit Restore auch rückgängig wäre)
        saveRevisionSnapshot(note)

        val now = clock.now()
        val restored = note.copy(
            title = rev.title,
            content = rev.content,
            updatedAt = now
        )
        repo.save(restored)
    }

    private fun saveRevisionSnapshot(note: Note) {
        val rev = Revision(
            id = RevisionId(UUID.randomUUID().toString()),
            noteId = note.id,
            title = note.title,
            content = note.content,
            createdAt = clock.now()
        )
        revisionRepo.save(rev)
    }

    // ---------- Purge / Retention ----------

    fun purgeNotePermanently(id: NoteId) {
        val existing = repo.findById(id) ?: return
        if (!existing.isTrashed()) {
            error("Cannot permanently delete an active note (not in trash).")
        }

        revisionRepo.deleteByNoteId(id)
        repo.deleteById(id)
    }

    fun purgeTrashedOlderThan(days: Long) {
        val cutoff = clock.now().minus(Duration.ofDays(days))

        // Kein Smart-Cast über Modulgrenzen: trashedAt einmal in lokale Variable ziehen
        val expired = repo.findAll()
            .filter { note ->
                val trashedAt = note.trashedAt
                trashedAt != null && trashedAt.isBefore(cutoff)
            }

        for (note in expired) {
            revisionRepo.deleteByNoteId(note.id)
            repo.deleteById(note.id)
        }
    }

    fun getRevision(id: RevisionId): Revision =
        revisionRepo.findById(id) ?: error("Revision not found: ${id.value}")

    fun clockNowForUi() = clock.now()

    fun deleteRevision(revisionId: RevisionId) {
        revisionRepo.deleteById(revisionId)
    }

    // ---------- Notebook / Tree Use-Cases ----------

    fun listNotebookTree(
        notebookRepo: NotebookRepository
    ): NotebookTreeDto {

        val allActiveNotes = repo.findAll()
            .filter { !it.isTrashed() }
            
        val allNotebooks = notebookRepo.findAll()

        // Root-Notizen (ohne Ordner)
        val rootNotes =
            allActiveNotes
                .filter { it.notebookId == null }
                .sortedBy { it.title.lowercase() }
                .map { it.toSummary() }

        // Recursive helper to build notebook tree
        fun buildNotebookNode(notebook: Notebook): NotebookNodeDto {
            val notesInNotebook = allActiveNotes
                .filter { it.notebookId == notebook.id }
                .sortedBy { it.title.lowercase() }
                .map { it.toSummary() }
                
            val children = allNotebooks
                .filter { it.parentId == notebook.id }
                .sortedBy { it.name.lowercase() }
                .map { buildNotebookNode(it) }
                
            return NotebookNodeDto(
                id = notebook.id.value,
                name = notebook.name,
                notes = notesInNotebook,
                subNotebooks = children
            )
        }

        val topLevelNotebooks = allNotebooks
            .filter { it.parentId == null }
            .sortedBy { it.name.lowercase() }
            .map { buildNotebookNode(it) }

        return NotebookTreeDto(
            rootNotes = rootNotes,
            notebooks = topLevelNotebooks
        )
    }

    private fun Note.toSummary(): NoteSummaryDto =
        NoteSummaryDto(
            id = id.value,
            title = title.ifBlank { "(Ohne Titel)" }
        )
    
    fun createNotebook(name: String, parentId: NotebookId? = null): NotebookId {
        val validated = validateNotebookName(name)
        val id = NotebookId(UUID.randomUUID().toString())
        // The actual saving should be done by the caller using repo.save(Notebook(id, validated, parentId))
        // or we need access to notebookRepo here.
        return id
    }

    fun moveNoteToNotebook(noteId: NoteId, notebookId: NotebookId?) {
        val note = repo.findById(noteId) ?: error("Note not found: ${noteId.value}")
        if (note.isTrashed()) error("Cannot move trashed note.")

        val now = clock.now()
        val moved = note.copy(
            notebookId = notebookId, // null = Root
            updatedAt = now
        )
        repo.save(moved)
    }
    
    fun moveNotebook(notebookId: NotebookId, newParentId: NotebookId?, notebookRepo: NotebookRepository) {
        val notebook = notebookRepo.findById(notebookId) ?: error("Notebook not found: ${notebookId.value}")
        
        // Prevent circular dependency
        if (newParentId != null) {
            var current: NotebookId? = newParentId
            while (current != null) {
                if (current == notebookId) error("Cannot move notebook into its own descendant")
                current = notebookRepo.findById(current)?.parentId
            }
        }
        
        val moved = notebook.copy(parentId = newParentId)
        notebookRepo.save(moved)
    }
}
