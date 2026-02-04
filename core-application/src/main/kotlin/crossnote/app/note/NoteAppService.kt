package crossnote.app.note

import crossnote.domain.note.*
import crossnote.domain.revision.*
import java.time.Duration
import java.util.UUID

data class NoteSummaryDto(val id: String, val title: String)
data class RevisionSummaryDto(val id: String, val createdAtIso: String)

class NoteAppService(
    private val repo: NoteRepository,
    private val revisionRepo: RevisionRepository,
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

        // Revision = Zustand VOR der Änderung
        saveRevisionSnapshot(existing)

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
            .map { NoteSummaryDto(it.id.value, it.title.ifBlank { "(Ohne Titel)" }) }
            .toList()

    fun listTrashedNotes(): List<NoteSummaryDto> =
        repo.findAll()
            .asSequence()
            .filter { it.isTrashed() }
            .sortedByDescending { it.updatedAt }
            .map { NoteSummaryDto(it.id.value, it.title.ifBlank { "(Ohne Titel)" }) }
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

        // Aufräumen von Revisionen (SQLite macht es zusätzlich per CASCADE)
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

}