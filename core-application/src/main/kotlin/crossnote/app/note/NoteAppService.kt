package crossnote.app.note

import crossnote.app.sync.NoteWire
import crossnote.app.sync.NotebookWire
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
    private val notebookRepo: NotebookRepository,
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

    /**
     * Updates a note and handles revision creation.
     * If [sessionRevisionId] is provided, it will overwrite that specific revision
     * instead of creating a new one.
     * Returns the revision ID used for this session.
     */
    fun updateNote(
        id: NoteId,
        title: String,
        content: String,
        sessionRevisionId: String? = null
    ): String? {
        val existing = repo.findById(id) ?: error("Note not found: ${id.value}")

        val validatedTitle = validateNoteTitle(title)
        if (existing.title == validatedTitle && existing.content == content) {
            return sessionRevisionId
        }

        val revId = sessionRevisionId ?: UUID.randomUUID().toString()
        
        // Revision = Zustand VOR der Änderung
        saveRevisionSnapshot(existing, revId)

        val now = clock.now()
        val updated = existing.copy(
            title = validatedTitle,
            content = content,
            updatedAt = now
        )
        repo.save(updated)
        
        return revId
    }

    fun getNote(id: NoteId): Note =
        repo.findById(id) ?: error("Note not found: ${id.value}")

    fun moveToTrash(id: NoteId) {
        val existing = repo.findById(id) ?: error("Note not found: ${id.value}")
        repo.save(existing.moveToTrash(clock.now()))
    }

    fun restore(id: NoteId) {
        val existing = repo.findById(id) ?: error("Note not found: ${id.value}")
        
        // If the note was in a notebook that is still trashed, move it to root
        val nbId = existing.notebookId
        val updatedNotebookId = if (nbId != null && notebookRepo.findById(nbId)?.trashedAt != null) {
            null
        } else {
            nbId
        }
        
        repo.save(existing.copy(trashedAt = null, notebookId = updatedNotebookId, updatedAt = clock.now()))
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
        saveRevisionSnapshot(note, UUID.randomUUID().toString())

        val now = clock.now()
        val restored = note.copy(
            title = rev.title,
            content = rev.content,
            updatedAt = now
        )
        repo.save(restored)
    }

    private fun saveRevisionSnapshot(note: Note, revisionId: String) {
        val rev = Revision(
            id = RevisionId(revisionId),
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

    fun listNotebookTree(): NotebookTreeDto {

        val allActiveNotes = repo.findAll()
            .filter { !it.isTrashed() }
            
        val allNotebooks = notebookRepo.findAll()
            .filter { it.trashedAt == null }

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

    fun listTrashedNotebookTree(): NotebookTreeDto {
        val allTrashedNotes = repo.findAll().filter { it.isTrashed() }
        val allNotebooks = notebookRepo.findAll()
        
        val rootTrashedNotes = allTrashedNotes
            .filter { note ->
                val nbId = note.notebookId
                if (nbId == null) true
                else notebookRepo.findById(nbId)?.trashedAt == null 
            }
            .sortedByDescending { it.updatedAt }
            .map { it.toSummary() }

        fun buildTrashedNode(notebook: Notebook): NotebookNodeDto {
            val notesInNotebook = allTrashedNotes
                .filter { it.notebookId == notebook.id }
                .sortedBy { it.title.lowercase() }
                .map { it.toSummary() }
                
            val children = allNotebooks
                .filter { it.parentId == notebook.id }
                .sortedBy { it.name.lowercase() }
                .map { buildTrashedNode(it) }
                
            return NotebookNodeDto(
                id = notebook.id.value,
                name = notebook.name,
                notes = notesInNotebook,
                subNotebooks = children
            )
        }

        val topLevelTrashedNotebooks = allNotebooks
            .filter { notebook ->
                val pId = notebook.parentId
                val isTrashed = notebook.trashedAt != null
                val isTopLevelInTrash = if (pId == null) true
                else notebookRepo.findById(pId)?.trashedAt == null
                
                isTrashed && isTopLevelInTrash
            }
            .sortedBy { it.name.lowercase() }
            .map { buildTrashedNode(it) }

        return NotebookTreeDto(
            rootNotes = rootTrashedNotes,
            notebooks = topLevelTrashedNotebooks
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
        val notebook = Notebook(id, validated, parentId, clock.now())
        notebookRepo.save(notebook)
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
    
    fun moveNotebook(notebookId: NotebookId, newParentId: NotebookId?) {
        val notebook = notebookRepo.findById(notebookId) ?: error("Notebook not found: ${notebookId.value}")
        
        // Prevent circular dependency
        if (newParentId != null) {
            var current: NotebookId? = newParentId
            while (current != null) {
                if (current == notebookId) error("Cannot move notebook into its own descendant")
                current = notebookRepo.findById(current)?.parentId
            }
        }
        
        val moved = notebook.copy(parentId = newParentId, updatedAt = clock.now())
        notebookRepo.save(moved)
    }

    fun moveNotebookToTrash(id: NotebookId) {
        val existing = notebookRepo.findById(id) ?: error("Notebook not found: ${id.value}")
        val now = clock.now()
        
        notebookRepo.save(existing.copy(trashedAt = now, updatedAt = now))
        
        repo.findAll().filter { it.notebookId == id && !it.isTrashed() }.forEach {
            repo.save(it.moveToTrash(now))
        }
        
        notebookRepo.findAll().filter { it.parentId == id && it.trashedAt == null }.forEach {
            moveNotebookToTrash(it.id)
        }
    }

    fun restoreNotebook(id: NotebookId) {
        val existing = notebookRepo.findById(id) ?: error("Notebook not found: ${id.value}")
        val now = clock.now()
        
        // If the parent notebook is still trashed, move this notebook to root
        val pId = existing.parentId
        val updatedParentId = if (pId != null && notebookRepo.findById(pId)?.trashedAt != null) {
            null
        } else {
            pId
        }
        
        notebookRepo.save(existing.copy(trashedAt = null, parentId = updatedParentId, updatedAt = now))
        
        repo.findAll().filter { it.notebookId == id && it.isTrashed() }.forEach {
            repo.save(it.restore(now))
        }
        
        notebookRepo.findAll().filter { it.parentId == id && it.trashedAt != null }.forEach {
            restoreNotebook(it.id)
        }
    }

    fun purgeNotebookPermanently(id: NotebookId) {
        // Purge all notes in this notebook
        repo.findAll().filter { it.notebookId == id }.forEach {
            if (it.isTrashed()) {
                purgeNotePermanently(it.id)
            }
        }
        
        // Purge sub-notebooks
        notebookRepo.findAll().filter { it.parentId == id }.forEach {
            purgeNotebookPermanently(it.id)
        }
        
        notebookRepo.delete(id)
    }

    // ---------- Sync Merging ----------

    fun mergeNote(remote: Note): Boolean {
        val local = repo.findById(remote.id)
        if (local == null) {
            repo.save(remote)
            return true
        }

        val isDifferent = local.title != remote.title || 
                        local.content != remote.content || 
                        local.notebookId != remote.notebookId || 
                        local.trashedAt != remote.trashedAt

        if (!isDifferent) return false

        if (remote.updatedAt.isAfter(local.updatedAt)) {
            // Remote is newer: Save local as revision (backup) and apply remote
            if (local.title != remote.title || local.content != remote.content) {
                saveRevisionSnapshot(local, "sync-backup-" + UUID.randomUUID().toString())
            }
            repo.save(remote)
            return true
        } else if (remote.updatedAt == local.updatedAt) {
            // Conflict: Same timestamp, different content
            saveRevisionSnapshot(local, "conflict-" + UUID.randomUUID().toString())
            repo.save(remote)
            return true
        }
        return false
    }

    fun mergeNotebook(remote: Notebook): Boolean {
        val local = notebookRepo.findById(remote.id)
        if (local == null) {
            notebookRepo.save(remote)
            return true
        }
        if (remote.updatedAt.isAfter(local.updatedAt)) {
            notebookRepo.save(remote)
            return true
        }
        return false
    }

    fun mergeNotesFromWire(body: String): Int {
        val remoteNotes = NoteWire.decodeLines(body)
        var appliedCount = 0
        for (remote in remoteNotes) {
            if (mergeNote(remote)) appliedCount++
        }
        return appliedCount
    }

    fun mergeNotebooksFromWire(body: String): Int {
        val remoteNotebooks = NotebookWire.decodeLines(body)
        var appliedCount = 0
        for (remote in remoteNotebooks) {
            if (mergeNotebook(remote)) appliedCount++
        }
        return appliedCount
    }

    fun getAllNotesAsWire(): String =
        NoteWire.encodeLines(repo.findAll())

    fun getAllNotebooksAsWire(): String =
        NotebookWire.encodeLines(notebookRepo.findAllIncludingTrashed())
}
