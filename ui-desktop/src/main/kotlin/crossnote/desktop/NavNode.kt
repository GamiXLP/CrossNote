package crossnote.desktop

import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId

sealed class NavNode {
    object RootHeader : NavNode()
    data class NotebookBranch(val notebookId: NotebookId, val name: String) : NavNode()
    data class NoteLeaf(val noteId: NoteId, val title: String) : NavNode()

    fun displayText(): String = when (this) {
        RootHeader -> "📄 Root"
        is NotebookBranch -> "📁 $name"
        is NoteLeaf -> "📝 $title"
    }
}
