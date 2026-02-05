package crossnote.desktop

import crossnote.app.note.NoteAppService
import crossnote.domain.note.NoteId
import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import crossnote.domain.revision.RevisionId
import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import crossnote.infra.persistence.*
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.AnchorPane
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainController {

    // ---------- Persistence / Service ----------
    private val db = SqliteDatabase(Paths.get(System.getProperty("user.home"), ".crossnote", "crossnote.db"))
    private val settingsRepo = SqliteSettingsRepository(db)
    private val notebookRepo = SqliteNotebookRepository(db)
    private val noteRepo = SqliteNoteRepository(db)

    private val service = NoteAppService(
        repo = noteRepo,
        revisionRepo = SqliteRevisionRepository(db),
        ids = UuidIdGenerator(),
        clock = SystemClock()
    )

    // ---------- Left panes ----------
    @FXML lateinit var APnotebooks: AnchorPane
    @FXML lateinit var APtrashcan: AnchorPane
    @FXML lateinit var APsavestate: AnchorPane

    // ---------- Notebooks/Notes (links) ----------
    @FXML lateinit var TFnotebook: TextField
    @FXML lateinit var TVnotebook: TreeView<NavNode>

    // ---------- Trash (links) ----------
    @FXML lateinit var TFtrashcan: TextField
    @FXML lateinit var TVtrashcan: TreeView<TrashNode>

    // ---------- Savestates (links) ----------
    @FXML lateinit var LBdataname: Label
    @FXML lateinit var LVsavestate: ListView<Pair<String, String>>
    @FXML lateinit var BTNload: Button

    // ---------- Bottom left buttons ----------
    @FXML lateinit var BTNtrashcan: Button
    @FXML lateinit var BTNsavestate: Button // "Zurück"

    // ---------- Top ----------
    @FXML lateinit var BTNdarkmode: Button

    // ---------- Center ----------
    @FXML lateinit var titleField: TextField
    @FXML lateinit var contentArea: TextArea
    @FXML lateinit var BTNsave: Button
    @FXML lateinit var LBlastchange: Label
    @FXML lateinit var LBsaved: Label

    // ---------- State ----------
    private val trashRoot: TreeItem<TrashNode> =
        TreeItem<TrashNode>(TrashNode.Root).apply { isExpanded = true }
    private val savestateItems = FXCollections.observableArrayList<Pair<String, String>>()

    private val treeRoot: TreeItem<NavNode> =
        TreeItem<NavNode>(NavNode.RootHeader).apply { isExpanded = true }

    private var selectedNoteId: String? = null
    private var lastActiveNoteId: String? = null
    private var selectedNotebookId: NotebookId? = null

    private var darkMode = false

    private var notebookSearch: String = ""
    private var expandedBeforeSearch: Set<String> = emptySet()
    private var wasSearching = false

    // =========================================================
    // Lifecycle
    // =========================================================
    @FXML
    fun initialize() {
        service.purgeTrashedOlderThan(30)

        showLeftPane(APnotebooks)

        setupTrashTree()
        setupSavestatesList()
        setupNotebookTree()
        setupSearch()
        setupButtons()
        setupThemeLoading()

        // Initial UI state
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        LBdataname.text = "Keine Notiz ausgewählt"

        refreshNotebookTree()
        setCenterMode()
    }

    // =========================================================
    // Setup
    // =========================================================
    private fun setupTrashTree() {
        TVtrashcan.isShowRoot = false
        TVtrashcan.root = trashRoot

        TVtrashcan.setCellFactory {
            object : TreeCell<TrashNode>() {
                override fun updateItem(item: TrashNode?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.displayText()
                    contextMenu = if (empty || item == null) null else buildTrashContextMenu(item)

                    // UX: Rechtsklick selektiert Item
                    setOnContextMenuRequested {
                        treeView?.selectionModel?.select(index)
                    }
                }
            }
        }

        // Auswahl im Trash: nur Notizen öffnen
        TVtrashcan.selectionModel.selectedItemProperty().addListener { _, _, new ->
            val node = new?.value ?: return@addListener
            if (node is TrashNode.NoteLeaf) {
                openNote(node.noteId.value, inTrash = true)
            } else {
                resetEditor()
                LBsaved.text = "Papierkorb"
                LBlastchange.text = "--"
            }
        }
    }

    private fun setupSavestatesList() {
        LVsavestate.items = savestateItems
        LVsavestate.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        // Savestate preview right
        LVsavestate.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) {
                LBdataname.text = new.second
                val rev = service.getRevision(RevisionId(new.first))
                titleField.text = rev.title
                contentArea.text = rev.content
                LBsaved.text = "Vorschau (Revision)"
                LBlastchange.text = rev.createdAt.toString()
            }
        }

        BTNload.setOnAction { onLoadSavestateRevision() }
    }

    private fun setupNotebookTree() {
        TVnotebook.isShowRoot = false
        TVnotebook.root = treeRoot
        TVnotebook.setCellFactory { NotebookTreeCell() }

        // Default: Rechtsklick auf leere Fläche / Root
        TVnotebook.contextMenu = ContextMenu(
            MenuItem("➕ Ordner erstellen").apply { setOnAction { createNotebookDialog(null) } },
            MenuItem("＋ Notiz im Root erstellen").apply { setOnAction { startNewNote(null) } }
        )

        // Tree selection -> open note / set target folder
        TVnotebook.selectionModel.selectedItemProperty().addListener { _, _, new ->
            val node = new?.value ?: return@addListener
            when (node) {
                is NavNode.NoteLeaf -> openNote(node.noteId.value, inTrash = false)
                is NavNode.NotebookBranch -> {
                    selectedNotebookId = node.notebookId
                    resetEditor()
                }
                is NavNode.RootHeader -> {
                    selectedNotebookId = null
                    resetEditor()
                }
            }
        }
    }

    private fun setupSearch() {
        TFnotebook.textProperty().addListener { _, _, newValue ->
            val newQuery = newValue.trim().lowercase()
            val nowSearching = newQuery.isNotBlank()

            if (nowSearching && !wasSearching) {
                expandedBeforeSearch = collectExpandedFolderIds(treeRoot)
            }

            notebookSearch = newQuery
            refreshNotebookTree()

            if (!nowSearching && wasSearching) {
                applyExpandedFolderIds(treeRoot, expandedBeforeSearch)
            }

            wasSearching = nowSearching
        }

        TFtrashcan.textProperty().addListener { _, _, _ ->
            refreshTrashTree()
        }
    }

    private fun setupButtons() {
        // Toggle Trash
        BTNtrashcan.setOnAction {
            if (APtrashcan.isVisible) {
                showLeftPane(APnotebooks)
                clearEditorAndSelections()
                refreshNotebookTree()
            } else {
                showLeftPane(APtrashcan)
                clearEditorAndSelections()
                refreshTrashTree()
            }
            setCenterMode()
        }

        // Zurück (Savestates)
        BTNsavestate.setOnAction {
            showLeftPane(APnotebooks)
            clearEditorAndSelections()
            refreshNotebookTree()
            setCenterMode()
        }

        // Actions
        BTNsave.setOnAction { onSave() }
    }

    private fun setupThemeLoading() {
        BTNdarkmode.sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                darkMode = settingsRepo.getBoolean("darkMode", false)
                applyTheme()
            }
        }
    }

    // =========================================================
    // Theme
    // =========================================================
    @FXML
    fun darkmode_on(@Suppress("UNUSED_PARAMETER") event: ActionEvent) {
        darkMode = !darkMode
        applyTheme()
        settingsRepo.setBoolean("darkMode", darkMode)
    }

    private fun applyTheme() {
        val root = BTNdarkmode.scene.root
        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
            BTNdarkmode.text = "Light Mode"
        } else {
            root.styleClass.remove("dark")
            BTNdarkmode.text = "Dark Mode"
        }
    }

    // =========================================================
    // Pane / Center state
    // =========================================================
    private fun showLeftPane(which: AnchorPane) {
        listOf(APnotebooks, APtrashcan, APsavestate).forEach {
            it.isVisible = false
            it.isManaged = false
        }
        which.isVisible = true
        which.isManaged = true
    }

    private fun setCenterMode() {
        val inTrash = APtrashcan.isVisible
        val inSavestate = APsavestate.isVisible
        val editorLocked = inTrash || inSavestate

        BTNsave.isDisable = editorLocked
        BTNload.isDisable = !inSavestate

        titleField.isEditable = !editorLocked
        contentArea.isEditable = !editorLocked

        // "Zurück" nur im Savestate-Tab sichtbar
        BTNsavestate.isVisible = inSavestate
        BTNsavestate.isManaged = inSavestate
    }

    private fun resetEditor() {
        selectedNoteId = null
        titleField.text = ""
        contentArea.text = ""
        LBlastchange.text = "--"
        LBsaved.text = "Nicht gespeichert"
        titleField.requestFocus()
    }

    private fun clearEditorAndSelections() {
        resetEditor()
        TVnotebook.selectionModel.clearSelection()
        TVtrashcan.selectionModel.clearSelection()
        LVsavestate.selectionModel.clearSelection()
    }

    // =========================================================
    // Notebooks / Notes
    // =========================================================
    private fun startNewNote(targetNotebookId: NotebookId?) {
        if (!APnotebooks.isVisible) showLeftPane(APnotebooks)

        selectedNotebookId = targetNotebookId
        resetEditor()
        setCenterMode()
    }

    private fun openSavestatesFor(noteId: NoteId) {
        lastActiveNoteId = noteId.value
        showLeftPane(APsavestate)
        clearEditorAndSelections()
        refreshSavestates()
        setCenterMode()
    }

    private fun deleteNoteFromNotebooks(noteId: NoteId) {
        service.moveToTrash(noteId)
        clearEditorAndSelections()
        refreshNotebookTree()
    }

    private fun openNote(noteId: String, inTrash: Boolean) {
        selectedNoteId = noteId
        if (!inTrash) lastActiveNoteId = noteId

        val note = service.getNote(NoteId(noteId))
        selectedNotebookId = note.notebookId

        titleField.text = note.title
        contentArea.text = note.content
        LBlastchange.text = note.updatedAt.toString()
        LBsaved.text = if (inTrash) trashCountdownText(noteId) else "Nicht gespeichert"
    }

    private fun onSave() {
        if (!APnotebooks.isVisible) return

        val title = titleField.text ?: ""
        val content = contentArea.text ?: ""

        val id = selectedNoteId
        if (id == null) {
            val newId = service.createNote(title, content, notebookId = selectedNotebookId)
            selectedNoteId = newId.value
            lastActiveNoteId = newId.value
            LBsaved.text = "Gespeichert (neu)"
        } else {
            service.updateNote(NoteId(id), title, content)
            lastActiveNoteId = id
            LBsaved.text = "Gespeichert (Revision erstellt)"
        }

        val refreshed = service.getNote(NoteId(selectedNoteId!!))
        LBlastchange.text = refreshed.updatedAt.toString()

        refreshNotebookTree()
    }

    // =========================================================
    // Tree building (Notebooks)
    // =========================================================
    private fun refreshNotebookTree() {
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

    // =========================================================
    // Notebook create / trash / restore / purge
    // =========================================================
    private fun createNotebookDialog(parent: NotebookId?) {
        val dialog = TextInputDialog().apply {
            title = "Neuer Ordner"
            headerText = if (parent == null) "Ordner anlegen" else "Unterordner anlegen"
            contentText = "Name:"
        }
        val result = dialog.showAndWait()
        if (result.isEmpty) return

        val name = result.get().trim()
        if (name.isEmpty()) return

        val id = NotebookId(UUID.randomUUID().toString())
        notebookRepo.save(Notebook(id, name, parentId = parent))
        refreshNotebookTree()
    }

    private fun trashNotebookRecursively(notebookId: NotebookId) {
        val idsToTrash = collectSubtreeIds(notebookId)

        val now = Instant.now()

        // notes -> trash
        idsToTrash.forEach { nbId ->
            noteRepo.listNoteSummariesInNotebook(nbId).forEach { n ->
                service.moveToTrash(NoteId(n.id))
            }
        }

        // folders -> trash
        idsToTrash.forEach { nbId ->
            notebookRepo.moveToTrash(nbId, now)
        }

        selectedNotebookId = null
        clearEditorAndSelections()
        refreshNotebookTree()
        refreshTrashTree()
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

        clearEditorAndSelections()
        refreshTrashTree()
        refreshNotebookTree()
    }

    private fun restoreNotebookChainIfNeeded(nbId: NotebookId?) {
        var cur = nbId
        while (cur != null && notebookRepo.isTrashed(cur)) {
            notebookRepo.restoreFromTrash(cur)
            cur = notebookRepo.findParentId(cur)
        }
    }

    private fun purgeTrashedNotebookRecursively(rootId: NotebookId) {
        if (!confirm(
                title = "Ordner endgültig löschen",
                header = "Ordner endgültig löschen?",
                content = "Der Ordner inkl. Unterordner und Notizen wird dauerhaft entfernt und kann nicht wiederhergestellt werden."
            )
        ) return

        val idsToDelete = collectSubtreeIds(rootId).toSet()

        // notes permanently delete (only trashed notes in these folders)
        service.listTrashedNotes().forEach { summary ->
            val noteId = NoteId(summary.id)
            val full = service.getNote(noteId)
            val nbId = full.notebookId
            if (nbId != null && idsToDelete.contains(nbId)) {
                service.purgeNotePermanently(noteId)
            }
        }

        // folders bottom-up
        idsToDelete.toList().reversed().forEach { nbId ->
            notebookRepo.delete(nbId)
        }

        clearEditorAndSelections()
        refreshTrashTree()
        refreshNotebookTree()
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

    // =========================================================
    // Trash Tree
    // =========================================================
    private fun refreshTrashTree() {
        val query = TFtrashcan.text?.trim()?.lowercase().orEmpty()
        trashRoot.children.clear()

        val trashedNotebooks = notebookRepo.findAllTrashed()
        val trashedIds = trashedNotebooks.map { it.id }.toSet()

        // Parent nur dann verwenden, wenn er auch getrasht ist, sonst auf null "hochziehen"
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

    private fun buildTrashContextMenu(node: TrashNode): ContextMenu =
        when (node) {
            is TrashNode.NoteLeaf -> ContextMenu(
                MenuItem("↺ Wiederherstellen").apply {
                    setOnAction {
                        restoreTrashedNote(node.noteId.value)
                        clearEditorAndSelections()
                        refreshTrashTree()
                        refreshNotebookTree()
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
                MenuItem("🗑 Endgültig löschen (inkl. Inhalt)").apply {
                    setOnAction { purgeTrashedNotebookRecursively(node.notebookId) }
                }
            )

            else -> ContextMenu()
        }

    private fun restoreTrashedNote(id: String) {
        val noteId = NoteId(id)
        val before = service.getNote(noteId)
        val originalNotebookId = before.notebookId

        restoreNotebookChainIfNeeded(originalNotebookId)
        service.restore(noteId)
        service.moveNoteToNotebook(noteId, originalNotebookId)
    }

    private fun purgeTrashedNote(noteId: NoteId) {
        if (!confirm(
                title = "Endgültig löschen",
                header = "Notiz endgültig löschen?",
                content = "Diese Notiz wird dauerhaft entfernt und kann nicht wiederhergestellt werden."
            )
        ) return

        service.purgeNotePermanently(noteId)
        clearEditorAndSelections()
        refreshTrashTree()
    }

    // =========================================================
    // Savestates
    // =========================================================
    private fun refreshSavestates() {
        val noteId = lastActiveNoteId
        if (noteId == null) {
            savestateItems.clear()
            LBdataname.text = "Keine Notiz ausgewählt"
            return
        }

        val revisions = service.listRevisions(NoteId(noteId))
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        val pretty = revisions.mapIndexed { index, r ->
            val instant = runCatching { Instant.parse(r.createdAtIso) }.getOrNull()
            val dateText = if (instant != null) formatter.format(instant) else r.createdAtIso
            val version = index + 1
            r.id to "$dateText – Version $version"
        }

        savestateItems.setAll(pretty)
        LBdataname.text =
            if (revisions.isEmpty()) "Keine Speicherstände vorhanden" else "Revisionen für Notiz: $noteId"
    }

    private fun onLoadSavestateRevision() {
        val noteId = lastActiveNoteId ?: run {
            LBdataname.text = "Keine Notiz ausgewählt"
            return
        }

        val selectedRev = LVsavestate.selectionModel.selectedItem ?: return
        val revisionId = RevisionId(selectedRev.first)

        service.restoreFromRevision(NoteId(noteId), revisionId)

        showLeftPane(APnotebooks)
        clearEditorAndSelections()
        refreshNotebookTree()
        setCenterMode()

        openNote(noteId, inTrash = false)
        LBsaved.text = "Auf Revision zurückgesetzt"
    }

    // =========================================================
    // Helpers
    // =========================================================
    private fun trashCountdownText(noteId: String): String {
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

    private fun confirm(title: String, header: String, content: String): Boolean {
        val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
            this.title = title
            headerText = header
            contentText = content
            buttonTypes.setAll(ButtonType.CANCEL, ButtonType.OK)
        }
        val result = confirm.showAndWait()
        return result.isPresent && result.get() == ButtonType.OK
    }

    // =========================================================
    // Drag & Drop + ContextMenus (Notebook tree)
    // =========================================================
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

    private inner class NotebookTreeCell : TreeCell<NavNode>() {

        override fun updateItem(item: NavNode?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) "" else item.displayText()
            if (empty) contextMenu = null
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
                        refreshNotebookTree()
                        openNote(noteId.value, inTrash = false)
                    }

                    payload.startsWith("FOLDER:") -> {
                        val folderId = NotebookId(payload.removePrefix("FOLDER:"))

                        if (!canMoveFolder(folderId, targetParent)) {
                            e.isDropCompleted = false
                            return@setOnDragDropped
                        }

                        notebookRepo.moveNotebook(folderId, targetParent)
                        e.isDropCompleted = true
                        refreshNotebookTree()
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
                            setOnAction { deleteNoteFromNotebooks(node.noteId) }
                        },
                        MenuItem("📋 Speicherstände anzeigen").apply {
                            setOnAction { openSavestatesFor(node.noteId) }
                        }
                    )

                    is NavNode.NotebookBranch -> ContextMenu(
                        MenuItem("＋ Neue Notiz hier").apply {
                            setOnAction { startNewNote(node.notebookId) }
                        },
                        MenuItem("➕ Neuer Unterordner").apply {
                            setOnAction { createNotebookDialog(node.notebookId) }
                        },
                        SeparatorMenuItem(),
                        MenuItem("🗑 Ordner löschen (Papierkorb)").apply {
                            setOnAction {
                                if (confirm(
                                        title = "Ordner löschen",
                                        header = "Ordner wirklich in den Papierkorb verschieben?",
                                        content = "Der Ordner inkl. Unterordner und Notizen wird in den Papierkorb verschoben."
                                    )
                                ) {
                                    trashNotebookRecursively(node.notebookId)
                                }
                            }
                        }
                    )

                    is NavNode.RootHeader -> ContextMenu(
                        MenuItem("＋ Neue Notiz im Root").apply { setOnAction { startNewNote(null) } },
                        MenuItem("➕ Neuer Ordner").apply { setOnAction { createNotebookDialog(null) } }
                    )
                }

                e.consume()
            }
        }
    }

    // =========================================================
    // Node Types
    // =========================================================
    sealed class TrashNode {
        object Root : TrashNode()
        data class FolderBranch(val notebookId: NotebookId, val name: String) : TrashNode()
        data class NoteLeaf(val noteId: NoteId, val title: String) : TrashNode()

        fun displayText(): String = when (this) {
            Root -> "Papierkorb"
            is FolderBranch -> "📁 $name"
            is NoteLeaf -> "📝 $title"
        }
    }

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

    fun close() {
        db.close()
    }
}
