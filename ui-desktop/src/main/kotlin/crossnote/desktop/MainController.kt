package crossnote.desktop

import crossnote.app.note.NoteAppService
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import crossnote.domain.note.NotebookRepository
import crossnote.domain.revision.RevisionId
import crossnote.infra.persistence.*
import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.VBox
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainController {

    // ---------- Persistence / Service ----------
    private val db = SqliteDatabase(Paths.get(System.getProperty("user.home"), ".crossnote", "crossnote.db"))
    private val settingsRepo = SqliteSettingsRepository(db)

    private val notebookRepo: NotebookRepository = SqliteNotebookRepository(db)

    private val service = NoteAppService(
        repo = SqliteNoteRepository(db),
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
    @FXML lateinit var LVnotebook: ListView<Pair<String, String>> // wird zur Laufzeit ersetzt

    // ---------- Trash (links) ----------
    @FXML lateinit var TFtrashcan: TextField
    @FXML lateinit var LVtrashcan: ListView<Pair<String, String>> // (id, title)
    @FXML lateinit var BTNrestore: Button

    // ---------- Savestates (links) ----------
    @FXML lateinit var LBdataname: Label
    @FXML lateinit var LVsavestate: ListView<Pair<String, String>> // (revisionId, prettyText)
    @FXML lateinit var BTNload: Button

    // ---------- Bottom left buttons ----------
    @FXML lateinit var BTNtrashcan: Button
    @FXML lateinit var BTNsavestate: Button

    // ---------- Top ----------
    @FXML lateinit var BTNdarkmode: Button

    // ---------- Center ----------
    @FXML lateinit var titleField: TextField
    @FXML lateinit var contentArea: TextArea
    @FXML lateinit var BTNsave: Button
    @FXML lateinit var BTNdelete: Button
    @FXML lateinit var BTNnewnote: Button
    @FXML lateinit var LBlastchange: Label
    @FXML lateinit var LBsaved: Label

    // ---------- State ----------
    private val trashItems = FXCollections.observableArrayList<Pair<String, String>>()
    private val savestateItems = FXCollections.observableArrayList<Pair<String, String>>() // (revId, prettyText)

    private lateinit var trashFiltered: FilteredList<Pair<String, String>>

    private var selectedId: String? = null
    private var lastActiveNoteId: String? = null
    private var darkMode: Boolean = false

    // ---------- TreeView (ersetzt LVnotebook) ----------
    private lateinit var notebookTree: TreeView<TreeNode>
    private lateinit var notebookTreeRoot: TreeItem<TreeNode>

    // Merkt, ob aktuell eine Note oder ein Ordner selektiert ist
    private var selectedTreeNode: TreeNode? = null

    // ---------------- Tree Model ----------------
    private sealed interface TreeNode {
        data class RootNote(val noteId: String, val title: String) : TreeNode
        data class Folder(val notebookId: String, val name: String) : TreeNode
        data class FolderNote(val noteId: String, val title: String, val notebookId: String) : TreeNode
        object Workspace : TreeNode
    }

    @FXML
    fun initialize() {
        // Auto-Purge: alles > 30 Tage im Papierkorb endgültig löschen
        service.purgeTrashedOlderThan(30)

        // Default-Ansicht: Notizen
        showLeftPane(APnotebooks)

        // --- Trash / Savestate Lists ---
        trashFiltered = FilteredList(trashItems) { true }
        LVtrashcan.items = trashFiltered

        LVtrashcan.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        LVsavestate.items = savestateItems
        LVsavestate.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        // Suchfeld Trash
        TFtrashcan.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            trashFiltered.setPredicate { it.second.lowercase().contains(q) }
        }

        // Auswahl -> Trash Note öffnen
        LVtrashcan.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) openNote(new.first, inTrash = true)
        }

        // Toggle: Papierkorb <-> Notizen
        BTNtrashcan.setOnAction {
            if (APtrashcan.isVisible) {
                showLeftPane(APnotebooks)
                clearEditorAndSelections()
                refreshNotebookTree()
            } else {
                showLeftPane(APtrashcan)
                clearEditorAndSelections()
                refreshTrashList()
            }
            setCenterMode()
        }

        // Toggle: Speicherstände <-> Notizen
        BTNsavestate.setOnAction {
            if (APsavestate.isVisible) {
                showLeftPane(APnotebooks)
                clearEditorAndSelections()
                refreshNotebookTree()
            } else {
                showLeftPane(APsavestate)
                clearEditorAndSelections()
                refreshSavestates()
            }
            setCenterMode()
        }

        // Aktionen
        BTNrestore.setOnAction { onRestore() }
        BTNsave.setOnAction { onSave() }
        BTNnewnote.setOnAction { onNew() }
        BTNdelete.setOnAction { onDelete() }

        // Papierkorb Kontextmenü: Endgültig löschen
        LVtrashcan.contextMenu = ContextMenu().apply {
            val purgeItem = MenuItem("Endgültig löschen").apply { setOnAction { onPurgeSelectedTrash() } }
            items.add(purgeItem)
        }

        // Speicherstände: Auswahl -> Preview rechts
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

        // Speicherstände: Laden = Revision wiederherstellen
        BTNload.setOnAction { onLoadSavestateRevision() }

        // Theme laden, sobald Scene da ist
        BTNdarkmode.sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                darkMode = settingsRepo.getBoolean("darkMode", false)
                applyTheme()
            }
        }

        // --- TreeView bauen und anstelle ListView einhängen ---
        setupNotebookTreeView()

        // Suchfeld Notizen: filtert Tree
        TFnotebook.textProperty().addListener { _, _, _ ->
            refreshNotebookTree()
        }

        // Initial status
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        LBdataname.text = "Keine Notiz ausgewählt"

        // Initial load
        refreshNotebookTree()
        setCenterMode()
    }

    // ---------------- Theme ----------------

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

    // ---------------- Left Pane Switch ----------------

    private fun showLeftPane(which: AnchorPane) {
        val panes = listOf(APnotebooks, APtrashcan, APsavestate)
        panes.forEach {
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
        BTNnewnote.isDisable = editorLocked
        BTNrestore.isDisable = !inTrash
        BTNload.isDisable = !inSavestate
        titleField.isEditable = !editorLocked
        contentArea.isEditable = !editorLocked

        BTNdelete.text = when {
            inTrash -> "Endgültig löschen"
            inSavestate -> "Speicherstand löschen"
            else -> "Löschen"
        }
    }

    private fun clearEditorAndSelections() {
        selectedId = null
        selectedTreeNode = null
        titleField.text = ""
        contentArea.text = ""
        LBlastchange.text = "--"
        LBsaved.text = "Nicht gespeichert"

        LVtrashcan.selectionModel.clearSelection()
        LVsavestate.selectionModel.clearSelection()

        if (this::notebookTree.isInitialized) {
            notebookTree.selectionModel.clearSelection()
        }
    }

    // ---------------- TreeView Setup/Refresh ----------------

    private fun setupNotebookTreeView() {
        notebookTreeRoot = TreeItem<TreeNode>(TreeNode.Workspace).apply { isExpanded = true }
        notebookTree = TreeView(notebookTreeRoot).apply {
            isShowRoot = false
            styleClass.addAll(LVnotebook.styleClass) // reuse styles from FXML ListView if any
        }

        // CellFactory: zeigt passende Texte
        notebookTree.setCellFactory {
            object : TreeCell<TreeNode>() {
                override fun updateItem(item: TreeNode?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else when (item) {
                        is TreeNode.RootNote -> item.title
                        is TreeNode.Folder -> "📁 ${item.name}"
                        is TreeNode.FolderNote -> item.title
                        TreeNode.Workspace -> ""
                    }
                }
            }
        }

        // Selection -> Note öffnen (Folder klickt nur auf/zu)
        notebookTree.selectionModel.selectedItemProperty().addListener { _, _, newItem ->
            val node = newItem?.value
            selectedTreeNode = node

            when (node) {
                is TreeNode.RootNote -> openNote(node.noteId, inTrash = false)
                is TreeNode.FolderNote -> openNote(node.noteId, inTrash = false)
                is TreeNode.Folder -> {
                    // Folder: Editor leeren (optional), nur expand/collapse
                    selectedId = null
                    titleField.text = ""
                    contentArea.text = ""
                    LBlastchange.text = "--"
                    LBsaved.text = "Ordner ausgewählt"
                    // toggle expand
                    newItem.isExpanded = !newItem.isExpanded
                }
                else -> {}
            }
        }

        // Ersetze LVnotebook in der VBox durch TreeView
        val parent = LVnotebook.parent
        if (parent is VBox) {
            val idx = parent.children.indexOf(LVnotebook)
            if (idx >= 0) {
                notebookTree.prefWidth = LVnotebook.prefWidth
                notebookTree.prefHeight = LVnotebook.prefHeight
                VBox.setVgrow(notebookTree, VBox.getVgrow(LVnotebook))
                parent.children[idx] = notebookTree
            } else {
                parent.children.add(notebookTree)
            }
        }
    }

    private fun refreshNotebookTree() {
        // Query aus Suchfeld
        val q = TFnotebook.text?.trim()?.lowercase().orEmpty()

        val allNotes = service.listActiveNotes() // enthält nur id+title, aber keine notebookId
        // -> Wir müssen Notes inkl notebookId aus DB holen, um root vs folder zu unterscheiden.
        val notesFull = (SqliteNoteRepository(db)).findAll().filter { !it.isTrashed() }

        // Root notes alphabetisch
        val rootNotes = notesFull
            .filter { it.notebookId == null }
            .sortedBy { it.title.lowercase() }
            .filter { it.title.lowercase().contains(q) || q.isBlank() }
            .map { TreeItem<TreeNode>(TreeNode.RootNote(it.id.value, it.title.ifBlank { "(Ohne Titel)" })) }

        // Folders alphabetisch
        val folders = notebookRepo.findAll()
            .sortedBy { it.name.lowercase() }
            .mapNotNull { folder ->
                val folderNotes = notesFull
                    .filter { it.notebookId == folder.id }
                    .sortedBy { it.title.lowercase() }
                    .map { note ->
                        TreeItem<TreeNode>(TreeNode.FolderNote(note.id.value, note.title.ifBlank { "(Ohne Titel)" }, folder.id.value))
                    }

                // Filter: Folder sichtbar, wenn Folder-Name matcht ODER eine Note matcht
                val folderNameMatches = folder.name.lowercase().contains(q)
                val filteredNotes = if (q.isBlank() || folderNameMatches) {
                    folderNotes
                } else {
                    folderNotes.filter { (it.value as TreeNode.FolderNote).title.lowercase().contains(q) }
                }

                // Wenn weder Foldername noch Notes matchen -> null
                if (q.isNotBlank() && !folderNameMatches && filteredNotes.isEmpty()) {
                    null
                } else {
                    TreeItem<TreeNode>(TreeNode.Folder(folder.id.value, folder.name)).apply {
                        children.setAll(filteredNotes)
                        isExpanded = true
                    }
                }
            }

        notebookTreeRoot.children.setAll(rootNotes + folders)

        // Selection beibehalten, wenn möglich
        val id = selectedId
        if (id != null) {
            selectTreeNoteById(id)
        }
    }

    private fun selectTreeNoteById(noteId: String) {
        fun dfs(item: TreeItem<TreeNode>): TreeItem<TreeNode>? {
            val v = item.value
            if (v is TreeNode.RootNote && v.noteId == noteId) return item
            if (v is TreeNode.FolderNote && v.noteId == noteId) return item
            for (c in item.children) {
                val found = dfs(c)
                if (found != null) return found
            }
            return null
        }

        for (top in notebookTreeRoot.children) {
            val found = dfs(top)
            if (found != null) {
                notebookTree.selectionModel.select(found)
                break
            }
        }
    }

    // ---------------- Trash / Savestates refresh ----------------

    private fun refreshTrashList() {
        val summaries = service.listTrashedNotes()
        trashItems.setAll(summaries.map { it.id to it.title })
    }

    private fun refreshSavestates() {
        val noteId = lastActiveNoteId
        if (noteId == null) {
            savestateItems.clear()
            LBdataname.text = "Keine Notiz ausgewählt"
            return
        }

        val revisions = service.listRevisions(NoteId(noteId)) // neueste zuerst
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
            if (revisions.isEmpty()) "Keine Speicherstände vorhanden"
            else "Revisionen für Notiz: $noteId"
    }

    // ---------------- Open / New / Save / Delete ----------------

    private fun openNote(noteId: String, inTrash: Boolean) {
        selectedId = noteId
        if (!inTrash) lastActiveNoteId = noteId

        val note = service.getNote(NoteId(noteId))
        titleField.text = note.title
        contentArea.text = note.content
        LBlastchange.text = note.updatedAt.toString()

        LBsaved.text = if (inTrash) trashCountdownText(noteId) else "Nicht gespeichert"
    }

    private fun onNew() {
        if (!APnotebooks.isVisible) return

        // Wenn Folder selektiert ist: neue Note in Folder, sonst Root
        val folderId: String? = (selectedTreeNode as? TreeNode.Folder)?.notebookId

        clearEditorAndSelections()
        titleField.requestFocus()

        // Merken: selectedTreeNode bleibt Folder? -> wir setzen es wieder
        if (folderId != null) {
            // Folder selektiert lassen (nur UI)
            // (Note wird erst beim Save erzeugt; Zuordnung machen wir später sauber im Service)
        }
    }

    private fun onSave() {
        if (!APnotebooks.isVisible) return

        val title = titleField.text ?: ""
        val content = contentArea.text ?: ""

        val id = selectedId
        if (id == null) {
            // NOTE: createNote() setzt aktuell notebookId noch nicht (muss im nächsten Schritt erweitert werden)
            val newId = service.createNote(title, content)
            selectedId = newId.value
            lastActiveNoteId = newId.value
            LBsaved.text = "Gespeichert (neu)"
        } else {
            service.updateNote(NoteId(id), title, content)
            lastActiveNoteId = id
            LBsaved.text = "Gespeichert (Revision erstellt)"
        }

        val refreshed = service.getNote(NoteId(selectedId!!))
        LBlastchange.text = refreshed.updatedAt.toString()

        refreshNotebookTree()
        selectTreeNoteById(selectedId!!)
    }

    private fun onDelete() {
        when {
            APsavestate.isVisible -> deleteSelectedSavestate()
            APtrashcan.isVisible -> purgeCurrentTrashNote()
            APnotebooks.isVisible -> moveCurrentNoteToTrash()
        }
    }

    private fun moveCurrentNoteToTrash() {
        val id = selectedId ?: return
        service.moveToTrash(NoteId(id))
        clearEditorAndSelections()
        refreshNotebookTree()
    }

    private fun purgeCurrentTrashNote() {
        val id = selectedId ?: return

        val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Endgültig löschen"
            headerText = "Notiz endgültig löschen?"
            contentText = "Diese Notiz wird dauerhaft entfernt und kann nicht wiederhergestellt werden."
            buttonTypes.setAll(ButtonType.CANCEL, ButtonType.OK)
        }
        val result = confirm.showAndWait()
        if (result.isPresent && result.get() == ButtonType.OK) {
            service.purgeNotePermanently(NoteId(id))
            clearEditorAndSelections()
            refreshTrashList()
        }
    }

    private fun onRestore() {
        if (!APtrashcan.isVisible) return
        val id = selectedId ?: return
        service.restore(NoteId(id))

        clearEditorAndSelections()
        refreshTrashList()
        refreshNotebookTree()
    }

    private fun onPurgeSelectedTrash() {
        if (!APtrashcan.isVisible) return
        val selected = LVtrashcan.selectionModel.selectedItem ?: return
        selectedId = selected.first
        purgeCurrentTrashNote()
    }

    private fun deleteSelectedSavestate() {
        if (!APsavestate.isVisible) return
        val selected = LVsavestate.selectionModel.selectedItem ?: run {
            LBsaved.text = "Kein Speicherstand ausgewählt"
            return
        }

        val revId = RevisionId(selected.first)

        val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Speicherstand löschen"
            headerText = "Diesen Speicherstand wirklich löschen?"
            contentText = "Der Speicherstand wird dauerhaft entfernt."
            buttonTypes.setAll(ButtonType.CANCEL, ButtonType.OK)
        }
        val result = confirm.showAndWait()
        if (result.isEmpty || result.get() != ButtonType.OK) return

        service.deleteRevision(revId)

        // UI refresh
        LVsavestate.selectionModel.clearSelection()
        refreshSavestates()

        titleField.text = ""
        contentArea.text = ""
        LBlastchange.text = "--"
        LBsaved.text = "Speicherstand gelöscht"
    }

    private fun onLoadSavestateRevision() {
        if (!APsavestate.isVisible) return

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
        selectTreeNoteById(noteId)

        LBsaved.text = "Auf Revision zurückgesetzt"
    }

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

    fun close() {
        db.close()
    }
}