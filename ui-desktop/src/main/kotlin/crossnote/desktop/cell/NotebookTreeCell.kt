package crossnote.desktop.cell

import crossnote.app.note.NoteAppService
import crossnote.desktop.I18n
import crossnote.desktop.NavNode
import crossnote.desktop.ThemeManager
import crossnote.desktop.util.Dialogs
import crossnote.desktop.util.NotebookTreeUtils
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import crossnote.infra.persistence.SqliteNotebookRepository
import crossnote.desktop.util.NotebookTreeUtils.createNoteIcon
import javafx.beans.value.ChangeListener
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TreeCell
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.StackPane
import javafx.scene.shape.SVGPath

class NotebookTreeCell(
    private val i18n: I18n,
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
    private val onRenameNotebook: (notebookId: NotebookId, currentName: String) -> Unit,
) : TreeCell<NavNode>() {

    private var expandedListener: ChangeListener<Boolean>? = null
    private var listenedTreeItem: javafx.scene.control.TreeItem<NavNode>? = null

    override fun updateItem(item: NavNode?, empty: Boolean) {
        expandedListener?.let { l -> listenedTreeItem?.expandedProperty()?.removeListener(l) }
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
                text = i18n.t("tree.root")
                graphic = null
            }
        }
    }

    init {
        // ---------------- Drag ----------------
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

        // ---------------- Context menu ----------------
        setOnContextMenuRequested { e ->
            if (isEmpty || item == null) return@setOnContextMenuRequested

            val node = item ?: return@setOnContextMenuRequested
            treeView?.selectionModel?.select(index)

            val tv = treeView ?: return@setOnContextMenuRequested
            val selectedValues = tv.selectionModel.selectedItems
                .mapNotNull { it.value }
                .filter { it !is NavNode.RootHeader }

            // Multi-Select Menü (bulk delete)
            if (selectedValues.size >= 2) {
                val folders = selectedValues.filterIsInstance<NavNode.NotebookBranch>()
                val notes = selectedValues.filterIsInstance<NavNode.NoteLeaf>()

                val bulkMenu = ContextMenu(
                    MenuItem(i18n.t("bulk.deleteSelection", selectedValues.size)).apply {
                        graphic = iconTrash()
                        setOnAction {
                            if (folders.isNotEmpty()) {
                                val ok = Dialogs.confirm(
                                    title = i18n.t("bulk.delete.title"),
                                    header = i18n.t("bulk.delete.folders.header", folders.size),
                                    content = i18n.t("bulk.delete.folders.content")
                                )
                                if (!ok) return@setOnAction
                                folders.forEach { f -> onTrashNotebookRecursively(f.notebookId) }
                            } else {
                                val ok = Dialogs.confirm(
                                    title = i18n.t("bulk.delete.title"),
                                    header = i18n.t("bulk.delete.notes.header", notes.size),
                                    content = i18n.t("bulk.delete.notes.content")
                                )
                                if (!ok) return@setOnAction
                                notes.forEach { n -> onDeleteNote(n.noteId) }
                            }
                            tv.selectionModel.clearSelection()
                        }
                    }
                )

                themeManager.register(bulkMenu)
                contextMenu = bulkMenu
                e.consume()
                return@setOnContextMenuRequested
            }

            val menu = when (node) {
                is NavNode.NoteLeaf -> ContextMenu(
                    MenuItem(i18n.t("note.delete")).apply {
                        graphic = iconTrash()
                        setOnAction { onDeleteNote(node.noteId) }
                    },
                    MenuItem(i18n.t("note.savestates")).apply {
                        graphic = iconClock()
                        setOnAction { onOpenSavestates(node.noteId) }
                    }
                )

                is NavNode.NotebookBranch -> ContextMenu(
                    MenuItem(i18n.t("folder.rename")).apply {
                        graphic = iconPencil()
                        setOnAction { onRenameNotebook(node.notebookId, node.name) }
                    },
                    SeparatorMenuItem(),
                    MenuItem(i18n.t("note.newHere")).apply {
                        graphic = iconPlus()
                        setOnAction { onStartNewNote(node.notebookId) }
                    },
                    MenuItem(i18n.t("folder.newSubfolder")).apply {
                        graphic = iconFolderPlus()
                        setOnAction { onCreateNotebook(node.notebookId) }
                    },
                    SeparatorMenuItem(),
                    MenuItem(i18n.t("folder.trash")).apply {
                        graphic = iconTrash()
                        setOnAction {
                            val ok = Dialogs.confirm(
                                title = i18n.t("delete.folder.title"),
                                header = i18n.t("delete.folder.header"),
                                content = i18n.t("delete.folder.content")
                            )
                            if (ok) onTrashNotebookRecursively(node.notebookId)
                        }
                    }
                )

                is NavNode.RootHeader -> ContextMenu(
                    MenuItem(i18n.t("note.newRoot")).apply {
                        graphic = iconPlus()
                        setOnAction { onStartNewNote(null) }
                    },
                    MenuItem(i18n.t("folder.newRoot")).apply {
                        graphic = iconFolderPlus()
                        setOnAction { onCreateNotebook(null) }
                    }
                )

                else -> ContextMenu()
            }

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

    // =========================================================
    // Tiny popup icons (no emoji => no "??")
    // =========================================================
    private fun iconSlot(icon: Node, size: Double = 14.0): Node =
        StackPane(icon).apply {
            minWidth = 22.0
            prefWidth = 22.0
            maxWidth = 22.0
            alignment = Pos.CENTER
            scaleX = size / 14.0
            scaleY = size / 14.0
            opacity = 0.85
        }

    private fun iconPencil(): Node = iconSlot(SVGPath().apply {
        content = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
    })

    private fun iconTrash(): Node = iconSlot(SVGPath().apply {
        content = "M6 7h12l-1 14H7L6 7zm3-3h6l1 2H8l1-2z"
    })

    private fun iconPlus(): Node = iconSlot(SVGPath().apply {
        content = "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5z"
    })

    private fun iconClock(): Node = iconSlot(SVGPath().apply {
        content = "M12 2a10 10 0 1 0 .001 20.001A10 10 0 0 0 12 2zm1 11h-4V7h2v4h2v2z"
    })

    private fun iconFolderPlus(): Node = iconSlot(Group(
        SVGPath().apply { content = "M3 6h6l2 2h10v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6z" },
        SVGPath().apply { content = "M12 10h2v2h2v2h-2v2h-2v-2h-2v-2h2v-2z" }
    ))
}
