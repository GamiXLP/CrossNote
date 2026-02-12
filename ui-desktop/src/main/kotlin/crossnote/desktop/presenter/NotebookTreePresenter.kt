package crossnote.desktop.presenter

import crossnote.desktop.NavNode
import crossnote.domain.note.NoteId
import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import crossnote.infra.persistence.SqliteNotebookRepository
import crossnote.infra.persistence.SqliteNoteRepository
import javafx.scene.control.TextField
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView

class NotebookTreePresenter(
    private val notebookRepo: SqliteNotebookRepository,
    private val noteRepo: SqliteNoteRepository,
    private val searchField: TextField,
    private val treeView: TreeView<NavNode>,
    private val treeRoot: TreeItem<NavNode>,
    private val onOpenNote: (noteId: String) -> Unit,
    private val onNotebookSelected: (NotebookId?) -> Unit,
    private val onResetEditor: () -> Unit,
) {
    private var notebookSearch: String = ""
    private var expandedBeforeSearch: Set<String> = emptySet()
    private var wasSearching: Boolean = false

    fun init() {
        treeView.isShowRoot = false
        treeView.root = treeRoot

        // Suche
        searchField.textProperty().addListener { _, _, newValue ->
            val newQuery = newValue.trim().lowercase()
            val nowSearching = newQuery.isNotBlank()

            if (nowSearching && !wasSearching) {
                expandedBeforeSearch = collectExpandedFolderIds(treeRoot)
            }

            notebookSearch = newQuery
            refresh()

            if (!nowSearching && wasSearching) {
                applyExpandedFolderIds(treeRoot, expandedBeforeSearch)
            }

            wasSearching = nowSearching
        }

        // Auswahl
        treeView.selectionModel.selectedItemProperty().addListener { _, _, new ->
            // ✅ Bei Multi-Select nichts automatisch öffnen/wechseln
            if (treeView.selectionModel.selectedItems.size != 1) return@addListener

            val node = new?.value ?: return@addListener
            when (node) {
                is NavNode.NoteLeaf -> onOpenNote(node.noteId.value)
                is NavNode.NotebookBranch -> {
                    onNotebookSelected(node.notebookId)
                    onResetEditor()
                }
                is NavNode.RootHeader -> {
                    onNotebookSelected(null)
                    onResetEditor()
                }
            }
        }
    }

    fun refresh() {
        val searching = notebookSearch.isNotBlank()
        val expandedIds = if (searching) expandedBeforeSearch else collectExpandedFolderIds(treeRoot)

        treeRoot.children.clear()

        val rootNotes = noteRepo.listRootNoteSummaries()
            .filter { notebookSearch.isBlank() || it.title.lowercase().contains(notebookSearch) }
            .map { note -> TreeItem<NavNode>(NavNode.NoteLeaf(NoteId(note.id), note.title)) }

        val notebooks = notebookRepo.findAll()
        val folderTree = buildFolderItems(null, notebooks, expandedIds)

        treeRoot.children.setAll(rootNotes + folderTree)
    }

    private fun buildFolderItems(
        parent: NotebookId?,
        notebooks: List<Notebook>,
        expandedIds: Set<String>,
        includeAll: Boolean = false
    ): List<TreeItem<NavNode>> {
        val q = notebookSearch
        val children = notebooks.filter { it.parentId == parent }.sortedBy { it.name.lowercase() }

        return children.mapNotNull { nb ->
            val folderMatches = q.isNotBlank() && nb.name.lowercase().contains(q)
            val includeHere = includeAll || folderMatches
            val searching = q.isNotBlank()

            val folderItem = TreeItem<NavNode>(NavNode.NotebookBranch(nb.id, nb.name)).apply {
                isExpanded = if (searching) true else expandedIds.contains(nb.id.value)
            }

            val notes = noteRepo.listNoteSummariesInNotebook(nb.id)
                .filter { includeHere || q.isBlank() || it.title.lowercase().contains(q) }

            notes.forEach { n ->
                folderItem.children.add(TreeItem(NavNode.NoteLeaf(NoteId(n.id), n.title)))
            }

            folderItem.children.addAll(buildFolderItems(nb.id, notebooks, expandedIds, includeHere))

            val shouldShow = q.isBlank() || includeHere || folderItem.children.isNotEmpty()
            if (shouldShow) folderItem else null
        }
    }

    private fun collectExpandedFolderIds(root: TreeItem<NavNode>): Set<String> {
        val expanded = mutableSetOf<String>()

        fun walk(item: TreeItem<NavNode>) {
            val v = item.value
            if (v is NavNode.NotebookBranch && item.isExpanded) expanded.add(v.notebookId.value)
            item.children.forEach { walk(it) }
        }

        root.children.forEach { walk(it) }
        return expanded
    }

    private fun applyExpandedFolderIds(root: TreeItem<NavNode>, expandedIds: Set<String>) {
        fun walk(item: TreeItem<NavNode>) {
            val v = item.value
            if (v is NavNode.NotebookBranch) item.isExpanded = expandedIds.contains(v.notebookId.value)
            item.children.forEach { walk(it) }
        }
        root.children.forEach { walk(it) }
    }
}
