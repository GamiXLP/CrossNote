package crossnote.desktop.presenter

import crossnote.app.note.NoteAppService
import crossnote.desktop.ThemeManager
import crossnote.desktop.TrashNode
import crossnote.desktop.util.Dialogs
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import crossnote.infra.persistence.SqliteNotebookRepository
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TextField
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import java.time.Duration

class TrashPresenter(
    private val service: NoteAppService,
    private val notebookRepo: SqliteNotebookRepository,
    private val themeManager: ThemeManager,
    private val trashTree: TreeView<TrashNode>,
    private val searchField: TextField,
    private val trashRoot: TreeItem<TrashNode>,
    private val onOpenNoteInTrash: (noteId: String) -> Unit,
    private val onTrashNonNoteSelected: () -> Unit,
    private val onRefreshNotebooks: () -> Unit,
) {

    fun init() {
        trashTree.isShowRoot = false
        trashTree.root = trashRoot

        trashTree.selectionModel.selectedItemProperty().addListener { _, _, new ->
            // ✅ Bei Multi-Select nichts automatisch öffnen/wechseln
            if (trashTree.selectionModel.selectedItems.size != 1) return@addListener

            val node = new?.value ?: return@addListener
            if (node is TrashNode.NoteLeaf) {
                onOpenNoteInTrash(node.noteId.value)
            } else {
                onTrashNonNoteSelected()
            }
        }

        searchField.textProperty().addListener { _, _, _ ->
            refresh()
        }
    }

    fun refresh() {
        val query = searchField.text?.trim()?.lowercase().orEmpty()
        trashRoot.children.clear()

        val trashedNotebooks = notebookRepo.findAllTrashed()
        val trashedIds = trashedNotebooks.map { it.id }.toSet()

        val byParent = trashedNotebooks.groupBy { nb ->
            val p = nb.parentId
            if (p != null && trashedIds.contains(p)) p else null
        }

        val trashedNotes = service.listTrashedNotes().map { n ->
            val full = service.getNote(NoteId(n.id))
            full.notebookId to (n.id to n.title)
        }.groupBy({ it.first }, { it.second })

        fun folderMatchesDeep(nbId: NotebookId): Boolean {
            val nb = trashedNotebooks.firstOrNull { it.id == nbId } ?: return false
            if (nb.name.lowercase().contains(query)) return true

            val notesHere = trashedNotes[nbId].orEmpty()
            if (notesHere.any { it.second.lowercase().contains(query) }) return true

            return byParent[nbId].orEmpty().any { folderMatchesDeep(it.id) }
        }

        fun buildFolderTree(parent: NotebookId?): List<TreeItem<TrashNode>> {
            val children = byParent[parent].orEmpty().sortedBy { it.name.lowercase() }

            return children.mapNotNull { nb ->
                if (query.isNotBlank() && !folderMatchesDeep(nb.id)) return@mapNotNull null

                val folderItem = TreeItem<TrashNode>(TrashNode.FolderBranch(nb.id, nb.name)).apply {
                    isExpanded = query.isNotBlank()
                }

                trashedNotes[nb.id].orEmpty()
                    .sortedBy { it.second.lowercase() }
                    .filter {
                        query.isBlank() ||
                                it.second.lowercase().contains(query) ||
                                nb.name.lowercase().contains(query)
                    }
                    .forEach { (noteId, title) ->
                        folderItem.children.add(TreeItem(TrashNode.NoteLeaf(NoteId(noteId), title)))
                    }

                folderItem.children.addAll(buildFolderTree(nb.id))
                folderItem
            }
        }

        val rootNotes = trashedNotes[null].orEmpty()
            .sortedBy { it.second.lowercase() }
            .filter { query.isBlank() || it.second.lowercase().contains(query) }
            .map { (noteId, title) -> TreeItem<TrashNode>(TrashNode.NoteLeaf(NoteId(noteId), title)) }

        trashRoot.children.addAll(buildFolderTree(null) + rootNotes)
    }

    fun buildContextMenu(node: TrashNode): ContextMenu {
        val menu = when (node) {
            is TrashNode.NoteLeaf -> ContextMenu(
                MenuItem("↺ Wiederherstellen").apply {
                    setOnAction {
                        restoreTrashedNote(node.noteId.value)
                        refresh()
                        onRefreshNotebooks()
                    }
                },
                MenuItem("🗑 Endgültig löschen").apply {
                    setOnAction { purgeTrashedNote(node.noteId) }
                }
            )

            is TrashNode.FolderBranch -> ContextMenu(
                MenuItem("↺ Wiederherstellen (inkl. Inhalt)").apply {
                    setOnAction { restoreNotebookRecursively(node.notebookId) }
                },
                SeparatorMenuItem(),
                MenuItem("🗑 Endgültig löschen (inkl. Inhalt)").apply {
                    setOnAction { purgeTrashedNotebookRecursively(node.notebookId) }
                }
            )

            else -> ContextMenu()
        }

        // ✅ wichtig: damit Toggle sofort wirkt
        themeManager.register(menu)
        return menu
    }

    private fun restoreTrashedNote(id: String) {
        val noteId = NoteId(id)
        val before = service.getNote(noteId)
        val originalNotebookId = before.notebookId

        restoreNotebookChainIfNeeded(originalNotebookId)
        service.restore(noteId)
        service.moveNoteToNotebook(noteId, originalNotebookId)
    }

    fun purgeTrashedNote(noteId: NoteId) {
        if (!Dialogs.confirm(
                title = "Endgültig löschen",
                header = "Notiz endgültig löschen?",
                content = "Diese Notiz wird dauerhaft entfernt und kann nicht wiederhergestellt werden."
            )
        ) return

        service.purgeNotePermanently(noteId)
        refresh()
    }

    private fun restoreNotebookRecursively(rootId: NotebookId) {
        restoreNotebookChainIfNeeded(rootId)

        val idsToRestore = collectSubtreeIds(rootId).toSet()

        idsToRestore.forEach { nbId ->
            if (notebookRepo.isTrashed(nbId)) notebookRepo.restoreFromTrash(nbId)
        }

        service.listTrashedNotes().forEach { summary ->
            val noteId = NoteId(summary.id)
            val full = service.getNote(noteId)
            val nbId = full.notebookId

            if (nbId != null && idsToRestore.contains(nbId)) {
                service.restore(noteId)
                service.moveNoteToNotebook(noteId, nbId)
            }
        }

        refresh()
        onRefreshNotebooks()
    }

    fun purgeTrashedNotebookRecursively(rootId: NotebookId) {
        if (!Dialogs.confirm(
                title = "Ordner endgültig löschen",
                header = "Ordner endgültig löschen?",
                content = "Der Ordner inkl. Unterordner und Notizen wird dauerhaft entfernt und kann nicht wiederhergestellt werden."
            )
        ) return

        val idsToDelete = collectSubtreeIds(rootId).toSet()

        service.listTrashedNotes().forEach { summary ->
            val noteId = NoteId(summary.id)
            val full = service.getNote(noteId)
            val nbId = full.notebookId
            if (nbId != null && idsToDelete.contains(nbId)) {
                service.purgeNotePermanently(noteId)
            }
        }

        idsToDelete.toList().reversed().forEach { nbId ->
            notebookRepo.delete(nbId)
        }

        refresh()
        onRefreshNotebooks()
    }

    fun purgeAllPermanently() {
        if (!Dialogs.confirm(
                title = "Papierkorb leeren",
                header = "Papierkorb wirklich endgültig leeren?",
                content = "Alle Notizen und Ordner im Papierkorb werden dauerhaft entfernt und können nicht wiederhergestellt werden."
            )
        ) return

        val trashedNoteIds = service.listTrashedNotes().map { NoteId(it.id) }
        trashedNoteIds.forEach { service.purgeNotePermanently(it) }

        val trashedNotebooks = notebookRepo.findAllTrashed()
        if (trashedNotebooks.isNotEmpty()) {
            val allIds = trashedNotebooks.map { it.id }.toSet()
            val byId = trashedNotebooks.associateBy { it.id }

            fun depth(id: NotebookId): Int {
                val nb = byId[id] ?: return 0
                val p = nb.parentId ?: return 0
                return if (allIds.contains(p)) 1 + depth(p) else 0
            }

            val deleteOrder = trashedNotebooks
                .sortedByDescending { depth(it.id) }
                .map { it.id }

            deleteOrder.forEach { notebookRepo.delete(it) }
        }

        refresh()
        onRefreshNotebooks()
    }

    fun trashCountdownText(noteId: String): String {
        val note = service.getNote(NoteId(noteId))
        val trashedAt = note.trashedAt ?: return "Papierkorb"
        val now = service.clockNowForUi()

        val elapsedDays = Duration.between(trashedAt, now).toDays()
        val remaining = 30 - elapsedDays

        return when {
            remaining <= 0L -> "Papierkorb: wird heute gelöscht"
            remaining == 1L -> "Papierkorb: 1 Tag übrig"
            else -> "Papierkorb: $remaining Tage übrig"
        }
    }

    private fun restoreNotebookChainIfNeeded(nbId: NotebookId?) {
        var cur = nbId
        while (cur != null && notebookRepo.isTrashed(cur)) {
            notebookRepo.restoreFromTrash(cur)
            cur = notebookRepo.findParentId(cur)
        }
    }

    private fun collectSubtreeIds(root: NotebookId): List<NotebookId> {
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
