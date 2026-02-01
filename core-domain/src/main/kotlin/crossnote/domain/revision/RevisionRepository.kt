package crossnote.domain.revision

import crossnote.domain.note.NoteId

interface RevisionRepository {
    fun save(revision: Revision)
    fun findById(id: RevisionId): Revision?
    fun findByNoteId(noteId: NoteId): List<Revision>
}