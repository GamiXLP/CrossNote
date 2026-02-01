package crossnote.domain.revision

import crossnote.domain.note.NoteId
import java.time.Instant

data class RevisionId(val value: String)

data class Revision(
    val id: RevisionId,
    val noteId: NoteId,
    val title: String,
    val content: String,
    val createdAt: Instant
)