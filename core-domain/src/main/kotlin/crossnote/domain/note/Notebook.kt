package crossnote.domain.note

import java.time.Instant

data class NotebookId(val value: String)

data class Notebook(
    val id: NotebookId,
    val name: String,
    val parentId: NotebookId? = null,
    val updatedAt: Instant = Instant.now(),
    val trashedAt: Instant? = null
)
