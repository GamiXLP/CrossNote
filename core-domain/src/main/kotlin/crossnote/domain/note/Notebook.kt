package crossnote.domain.note

data class NotebookId(val value: String)

data class Notebook(
    val id: NotebookId,
    val name: String,
    val parentId: NotebookId? = null
)