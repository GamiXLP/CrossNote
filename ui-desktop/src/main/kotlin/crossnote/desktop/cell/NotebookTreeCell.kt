package crossnote.desktop.cell

import crossnote.app.note.NoteAppService
import crossnote.desktop.util.Dialogs
import crossnote.desktop.util.NotebookTreeUtils
import crossnote.desktop.NavNode
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import crossnote.infra.persistence.SqliteNotebookRepository
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TreeCell
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.beans.value.ChangeListener
import javafx.scene.paint.Color


class NotebookTreeCell(
    private val service: NoteAppService,
    private val notebookRepo: SqliteNotebookRepository,
    private val onRefreshTree: () -> Unit,
    private val onOpenNote: (noteId: String) -> Unit,
    private val onDeleteNote: (noteId: NoteId) -> Unit,
    private val onOpenSavestates: (noteId: NoteId) -> Unit,
    private val onStartNewNote: (targetNotebookId: NotebookId?) -> Unit,
    private val onCreateNotebook: (parent: NotebookId?) -> Unit,
    private val onTrashNotebookRecursively: (notebookId: NotebookId) -> Unit,
) : TreeCell<NavNode>() {

    private val closedIcon = NotebookTreeUtils.createClosedFolderIcon()
    private val openIcon = NotebookTreeUtils.createOpenFolderIcon()

    private var expandedListener: ChangeListener<Boolean>? = null

    override fun updateItem(item: NavNode?, empty: Boolean) {
        super.updateItem(item, empty)

        // ===== Cleanup alte Listener =====
        expandedListener?.let {
            treeItem?.expandedProperty()?.removeListener(it)
        }

        if (empty || item == null) {
            text = ""
            graphic = null
            contextMenu = null
            return
        }

        when (item) {

            is NavNode.NotebookBranch -> {

                text = item.name
                val ti = treeItem ?: return

                fun updateIcon() {
                    val icon = if (ti.isExpanded) openIcon else closedIcon

                    // 👉 Klick auf Icon toggelt Expand
                    icon.setOnMouseClicked { e ->
                        ti.isExpanded = !ti.isExpanded
                        e.consume()
                    }

                    graphic = icon
                }

                // Initial setzen
                updateIcon()

                // Listener speichern → wichtig
                expandedListener = ChangeListener { _, _, _ ->
                    updateIcon()
                }

                ti.expandedProperty().addListener(expandedListener)
            }

            is NavNode.NoteLeaf -> {
                text = item.title
                graphic = null
            }

            is NavNode.RootHeader -> {
                text = "Root"
                graphic = null
            }
        }
    }


    init {
        // Drag start: notes + folders
        setOnDragDetected { e ->
            when (val node = item) {
                is NavNode.NoteLeaf -> {
                    val dbb: Dragboard = startDragAndDrop(TransferMode.MOVE)
                    val content = ClipboardContent()
                    content.putString("NOTE:${node.noteId.value}")
                    dbb.setContent(content)
                    e.consume()
                }

                is NavNode.NotebookBranch -> {
                    val dbb: Dragboard = startDragAndDrop(TransferMode.MOVE)
                    val content = ClipboardContent()
                    content.putString("FOLDER:${node.notebookId.value}")
                    dbb.setContent(content)
                    e.consume()
                }

                else -> {}
            }
        }

        // Drag over: accept on folders OR root
        setOnDragOver { e: DragEvent ->
            val payload = e.dragboard.string
            if (payload.isNullOrBlank()) return@setOnDragOver

            val target = item
            val accept = target is NavNode.NotebookBranch || target is NavNode.RootHeader
            if (accept) e.acceptTransferModes(TransferMode.MOVE)

            e.consume()
        }

        // Drop: move note/folder
        setOnDragDropped { e ->
            val payload = e.dragboard.string
            if (payload.isNullOrBlank()) {
                e.isDropCompleted = false
                return@setOnDragDropped
            }

            val target = item
            val targetParent: NotebookId? = when (target) {
                is NavNode.NotebookBranch -> target.notebookId
                is NavNode.RootHeader -> null
                else -> null
            }

            when {
                payload.startsWith("NOTE:") -> {
                    val noteId = NoteId(payload.removePrefix("NOTE:"))
                    service.moveNoteToNotebook(noteId, targetParent)
                    e.isDropCompleted = true
                    onRefreshTree()
                    onOpenNote(noteId.value)
                }

                payload.startsWith("FOLDER:") -> {
                    val folderId = NotebookId(payload.removePrefix("FOLDER:"))

                    if (!canMoveFolder(folderId, targetParent)) {
                        e.isDropCompleted = false
                        return@setOnDragDropped
                    }

                    notebookRepo.moveNotebook(folderId, targetParent)
                    e.isDropCompleted = true
                    onRefreshTree()
                }

                else -> e.isDropCompleted = false
            }

            e.consume()
        }

        // Right-click menus
        setOnContextMenuRequested { e ->
            val node = item ?: return@setOnContextMenuRequested
            treeView?.selectionModel?.select(index)

            contextMenu = when (node) {
                is NavNode.NoteLeaf -> ContextMenu(
                    MenuItem("🗑 Notiz löschen").apply {
                        setOnAction { onDeleteNote(node.noteId) }
                    },
                    MenuItem("📋 Speicherstände anzeigen").apply {
                        setOnAction { onOpenSavestates(node.noteId) }
                    }
                )

                is NavNode.NotebookBranch -> ContextMenu(
                    MenuItem("＋ Neue Notiz hier").apply {
                        setOnAction { onStartNewNote(node.notebookId) }
                    },
                    MenuItem("➕ Neuer Unterordner").apply {
                        setOnAction { onCreateNotebook(node.notebookId) }
                    },
                    SeparatorMenuItem(),
                    MenuItem("🗑 Ordner löschen (Papierkorb)").apply {
                        setOnAction {
                            if (Dialogs.confirm(
                                    title = "Ordner löschen",
                                    header = "Ordner wirklich in den Papierkorb verschieben?",
                                    content = "Der Ordner inkl. Unterordner und Notizen wird in den Papierkorb verschoben."
                                )
                            ) {
                                onTrashNotebookRecursively(node.notebookId)
                            }
                        }
                    }
                )

                is NavNode.RootHeader -> ContextMenu(
                    MenuItem("＋ Neue Notiz im Root").apply { setOnAction { onStartNewNote(null) } },
                    MenuItem("➕ Neuer Ordner").apply { setOnAction { onCreateNotebook(null) } }
                )
            }

            e.consume()
        }
    }

    private fun canMoveFolder(folderId: NotebookId, targetParent: NotebookId?): Boolean {
        if (targetParent == null) return true
        if (targetParent == folderId) return false

        val all = notebookRepo.findAll()
        val parentMap = all.associate { it.id to it.parentId }

        var cur: NotebookId? = targetParent
        while (cur != null) {
            if (cur == folderId) return false
            cur = parentMap[cur]
        }
        return true
    }
}