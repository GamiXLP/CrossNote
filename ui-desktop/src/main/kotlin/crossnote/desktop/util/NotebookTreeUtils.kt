package crossnote.desktop.util

import crossnote.domain.note.NotebookId
import crossnote.infra.persistence.SqliteNotebookRepository

object NotebookTreeUtils {

    fun collectSubtreeIds(notebookRepo: SqliteNotebookRepository, root: NotebookId): List<NotebookId> {
        val all = notebookRepo.findAllIncludingTrashed()
        val byParent = all.groupBy { it.parentId }

        val result = mutableListOf<NotebookId>()
        fun dfs(id: NotebookId) {
            result.add(id)
            byParent[id].orEmpty().forEach { child -> dfs(child.id) }
        }
        dfs(root)
        return result
    }
}
