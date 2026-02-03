package crossnote.infra.persistence

import crossnote.domain.note.NoteId
import crossnote.domain.revision.Revision
import crossnote.domain.revision.RevisionId
import crossnote.domain.revision.RevisionRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryRevisionRepository : RevisionRepository {
    private val data = ConcurrentHashMap<String, Revision>()

    override fun save(revision: Revision) {
        data[revision.id.value] = revision
    }

    override fun findById(id: RevisionId): Revision? = data[id.value]

    override fun findByNoteId(noteId: NoteId): List<Revision> =
        data.values
            .filter { it.noteId == noteId }
            .sortedByDescending { it.createdAt }
    
    override fun deleteById(id: RevisionId) {
        data.remove(id.value)
    }

    override fun deleteByNoteId(noteId: NoteId) {
        val toRemove = data.values.filter { it.noteId == noteId }.map { it.id.value }
        toRemove.forEach { data.remove(it) }
    }
}