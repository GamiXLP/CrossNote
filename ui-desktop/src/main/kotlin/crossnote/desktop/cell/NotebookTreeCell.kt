package crossnote.desktop.cell

import crossnote.app.note.NoteAppService
import crossnote.desktop.NavNode
import crossnote.desktop.ThemeManager
import crossnote.desktop.util.Dialogs
import crossnote.desktop.util.NotebookTreeUtils
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import crossnote.infra.persistence.SqliteNotebookRepository
import crossnote.desktop.util.NotebookTreeUtils.createNoteIcon
import javafx.beans.value.ChangeListener
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TreeCell
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode

class NotebookTreeCell(
    private val service: NoteAppService,
    private val notebookRepo: SqliteNotebookRepository,
    private val themeManager: ThemeManager,
    private val onRefreshTree: () -> Unit,
    private val onOpenNote: (noteId: String) -> Unit,
    private val onDeleteNote: (noteId: NoteId) -> Unit,
    private val onOpenSavestates: (noteId: NoteId) -> Unit,
    private val onStartNewNote: (targetNotebookId: NotebookId?) -> Unit,
    private val onCreateNotebook: (parent: NotebookId?) -> Unit,
    private val onTrashNotebookRecursively: (notebookId: NotebookId) -> Unit,
) : TreeCell<NavNode>() {

    private var expandedListener: ChangeListener<Boolean>? = null
    private var listenedTreeItem: javafx.scene.control.TreeItem<NavNode>? = null

    override fun updateItem(item: NavNode?, empty: Boolean) {

        expandedListener?.let { l ->
            listenedTreeItem?.expandedProperty()?.removeListener(l)
        }
        expandedListener = null
        listenedTreeItem = null

        super.updateItem(item, empty)

        if (empty || item == null) {
            text = null
            graphic = null
            contextMenu = null
            return
        }

        when (item) {

            is NavNode.NotebookBranch -> {
                text = item.name
                val ti = treeItem ?: return

                fun buildIcon(expanded: Boolean) =
                    if (expanded) NotebookTreeUtils.createOpenFolderIcon()
                    else NotebookTreeUtils.createClosedFolderIcon()

                fun updateIcon() {
                    val icon = buildIcon(ti.isExpanded)

                    icon.setOnMouseClicked { e ->
                        ti.isExpanded = !ti.isExpanded
                        e.consume()
                    }

                    graphic = icon
                }

                updateIcon()

                expandedListener = ChangeListener { _, _, _ -> updateIcon() }
                ti.expandedProperty().addListener(expandedListener)
                listenedTreeItem = ti
            }

            is NavNode.NoteLeaf -> {
                text = item.title
                graphic = createNoteIcon()
            }

            is NavNode.RootHeader -> {
                text = "Root"
                graphic = null
            }
        }
    }

    init {

        setOnDragDetected { e ->
            if (isEmpty || item == null) return@setOnDragDetected

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

        setOnDragOver { e: DragEvent ->
            if (isEmpty || item == null) return@setOnDragOver

            val payload = e.dragboard.string ?: return@setOnDragOver
            val target = item

            if (target is NavNode.NotebookBranch || target is NavNode.RootHeader) {
                e.acceptTransferModes(TransferMode.MOVE)
            }

            e.consume()
        }

        setOnDragDropped { e ->
            if (isEmpty || item == null) {
                e.isDropCompleted = false
                return@setOnDragDropped
            }

            val payload = e.dragboard.string ?: run {
                e.isDropCompleted = false
                return@setOnDragDropped
            }

            val targetParent: NotebookId? = when (val target = item) {
                is NavNode.NotebookBranch -> target.notebookId
                is NavNode.RootHeader -> null
                else -> null
            }

            when {
                payload.startsWith("NOTE:") -> {
                    val noteId = NoteId(payload.removePrefix("NOTE:"))
                    service.moveNoteToNotebook(noteId, targetParent)
                    onRefreshTree()
                    onOpenNote(noteId.value)
                    e.isDropCompleted = true
                }

                payload.startsWith("FOLDER:") -> {
                    val folderId = NotebookId(payload.removePrefix("FOLDER:"))

                    if (!canMoveFolder(folderId, targetParent)) {
                        e.isDropCompleted = false
                        return@setOnDragDropped
                    }

                    notebookRepo.moveNotebook(folderId, targetParent)
                    onRefreshTree()
                    e.isDropCompleted = true
                }

                else -> e.isDropCompleted = false
            }

            e.consume()
        }

        setOnContextMenuRequested { e ->
            if (isEmpty || item == null) return@setOnContextMenuRequested

            val node = item ?: return@setOnContextMenuRequested
            treeView?.selectionModel?.select(index)

            val menu = when (node) {

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
                    MenuItem("＋ Neue Notiz im Root").apply {
                        setOnAction { onStartNewNote(null) }
                    },
                    MenuItem("➕ Neuer Ordner").apply {
                        setOnAction { onCreateNotebook(null) }
                    }
                )
            }

            // ✅ extrem wichtig: damit Toggle sofort wirkt und ohne Refresh stimmt
            themeManager.register(menu)

            contextMenu = menu
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
