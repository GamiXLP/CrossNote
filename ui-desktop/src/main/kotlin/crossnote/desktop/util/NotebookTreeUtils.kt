package crossnote.desktop.util

import crossnote.domain.note.NotebookId
import crossnote.infra.persistence.SqliteNotebookRepository

import javafx.scene.Group
import javafx.scene.shape.SVGPath


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

    fun createClosedFolderIcon(): Group {
        val back = SVGPath().apply {
            content = "M16 17a4 4 0 0 0-4 4v38a4 4 0 0 0 4 4h48a4 4 0 0 0 4-4V29a4 4 0 0 0-4-4H35.4c-.367 0-.711-.177-.924-.475l-.34-.474l-.376-.526l-.377-.525l-3.099-4.329A4 4 0 0 0 27.032 17z"
            fill = javafx.scene.paint.Color.web("#f2994a")
        }

        val front = SVGPath().apply {
            content = "M12 25h56v38H12z"
            fill = javafx.scene.paint.Color.web("#f2c94c")
        }

        return Group(back, front).apply {
            scaleX = 0.4
            scaleY = 0.4
        }
    }


    fun createOpenFolderIcon(): Group {
        val front = SVGPath().apply {
            content = "M16.104 40.31A8 8 0 0 1 23.638 35h44.686c2.766 0 4.697 2.74 3.767 5.345l-7.143 20A4 4 0 0 1 61.181 63H10.838a2 2 0 0 1-1.883-2.673z"
            fill = javafx.scene.paint.Color.web("#f2c94c")
        }

        val back = SVGPath().apply {
            content = "M8 21a4 4 0 0 1 4-4h11.032a4 4 0 0 1 3.252 1.671l3.1 4.329l.376.525l.376.526l.34.474c.213.298.557.475.923.475H60a4 4 0 0 1 4 4v6H23.638a8 8 0 0 0-7.534 5.31l-7.15 20.017a2 2 0 0 0-.042 1.215A3.98 3.98 0 0 1 8 59z"
            fill = javafx.scene.paint.Color.web("#f2994a")
        }

        return Group(back, front).apply {
            scaleX = 0.4
            scaleY = 0.4
        }
    }
}
