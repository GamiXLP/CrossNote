package crossnote.desktop

import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId

sealed class TrashNode {
    object Root : TrashNode()
    data class FolderBranch(val notebookId: NotebookId, val name: String) : TrashNode()
    data class NoteLeaf(val noteId: NoteId, val title: String) : TrashNode()

    fun displayText(): String = when (this) {
        Root -> "Papierkorb"
        is FolderBranch -> name
        is NoteLeaf -> title
    }
}
