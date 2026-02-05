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
import javafx.collections.transformation.FilteredList
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
    @FXML lateinit var TVnotebook: TreeView<NavNode>  // ✅ TreeView statt ListView

    // ---------- Trash (links) ----------
    @FXML lateinit var TFtrashcan: TextField
    @FXML lateinit var LVtrashcan: ListView<Pair<String, String>>
    @FXML lateinit var BTNrestore: Button

    // ---------- Savestates (links) ----------
    @FXML lateinit var LBdataname: Label
    @FXML lateinit var LVsavestate: ListView<Pair<String, String>>
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
    private val savestateItems = FXCollections.observableArrayList<Pair<String, String>>()

    private lateinit var trashFiltered: FilteredList<Pair<String, String>>

    private var selectedId: String? = null
    private var lastActiveNoteId: String? = null
    private var darkMode: Boolean = false

    // ---- Tree Root ----
    private val treeRoot: TreeItem<NavNode> =
        TreeItem<NavNode>(NavNode.RootHeader).apply { isExpanded = true }
    
    // Suche: wir filtern Tree nicht super fancy, sondern rebuilden bei Eingabe
    private var notebookSearch: String = ""

    @FXML
    fun initialize() {
        service.purgeTrashedOlderThan(30)

        showLeftPane(APnotebooks)

        // ---------- Trash list ----------
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

        // ---------- Savestates list ----------
        LVsavestate.items = savestateItems
        LVsavestate.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        // ---------- TreeView ----------
        TVnotebook.isShowRoot = false
        TVnotebook.root = treeRoot
        TVnotebook.setCellFactory { NotebookTreeCell() }

        // Tree selection -> open note (nur wenn Note)
        TVnotebook.selectionModel.selectedItemProperty().addListener { _, _, new ->
            val node = new?.value ?: return@addListener
            if (node is NavNode.NoteLeaf) {
                openNote(node.noteId.value, inTrash = false)
            }
        }

        // Search fields
        TFnotebook.textProperty().addListener { _, _, newValue ->
            notebookSearch = newValue.trim().lowercase()
            refreshNotebookTree()
        }
        TFtrashcan.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            trashFiltered.setPredicate { it.second.lowercase().contains(q) }
        }

        // Trash selection -> open note
        LVtrashcan.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) openNote(new.first, inTrash = true)
        }

        // Toggle Trash
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

        // Toggle Savestates
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

        // Actions
        BTNrestore.setOnAction { onRestore() }
        BTNsave.setOnAction { onSave() }
        BTNnewnote.setOnAction { onNew() }
        BTNdelete.setOnAction { onDelete() }

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

        // Theme load when scene exists
        BTNdarkmode.sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                darkMode = settingsRepo.getBoolean("darkMode", false)
                applyTheme()
            }
        }

        // Context menu Tree: new folder
        TVnotebook.contextMenu = ContextMenu().apply {
            items.add(MenuItem("➕ Neuer Ordner").apply {
                setOnAction { createNotebookDialog() }
            })
        }

        // Initial
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        LBdataname.text = "Keine Notiz ausgewählt"

        refreshNotebookTree()
        setCenterMode()
    }

    // ---------- Theme ----------
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

    // ---------- Left Pane ----------
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
        titleField.text = ""
        contentArea.text = ""
        LBlastchange.text = "--"
        LBsaved.text = "Nicht gespeichert"

        TVnotebook.selectionModel.clearSelection()
        LVtrashcan.selectionModel.clearSelection()
        LVsavestate.selectionModel.clearSelection()
    }

    // ---------- Tree Build ----------
    private fun refreshNotebookTree() {
        // Pull data from service
        val treeDto = service.listNotebookTree(notebookRepo)

        treeRoot.children.clear()

        // Root notes (oben), alphabetisch
        val rootNotes = treeDto.rootNotes
            .filter { notebookSearch.isBlank() || it.title.lowercase().contains(notebookSearch) }
            .map { note ->
                TreeItem<NavNode>(NavNode.NoteLeaf(NoteId(note.id), note.title))
            }

        // Folders after, alphabetisch
        val folderNodes = treeDto.notebooks.mapNotNull { nb ->
            val notes = nb.notes
                .filter { notebookSearch.isBlank() || it.title.lowercase().contains(notebookSearch) }

            // Wenn gesucht wird: Ordner ohne Treffer ausblenden
            if (notebookSearch.isNotBlank() && notes.isEmpty() && !nb.name.lowercase().contains(notebookSearch)) {
                return@mapNotNull null
            }

            val folderItem = TreeItem<NavNode>(NavNode.NotebookBranch(NotebookId(nb.id), nb.name)).apply {
                isExpanded = true
            }

            notes.forEach { n ->
                folderItem.children.add(TreeItem<NavNode>(NavNode.NoteLeaf(NoteId(n.id), n.title)))
            }

            folderItem
        }

        treeRoot.children.setAll(rootNotes + folderNodes)
    }

    // ---------- Create Notebook ----------
    private fun createNotebookDialog() {
        val dialog = TextInputDialog().apply {
            title = "Neuer Ordner"
            headerText = "Ordner anlegen"
            contentText = "Name:"
        }
        val result = dialog.showAndWait()
        if (result.isEmpty) return

        val name = result.get().trim()
        if (name.isEmpty()) return

        val id = NotebookId(UUID.randomUUID().toString())
        notebookRepo.save(Notebook(id, name))
        refreshNotebookTree()
    }

    // ---------- Open / New / Save ----------
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
        clearEditorAndSelections()
        titleField.requestFocus()
    }

    private fun onSave() {
        if (!APnotebooks.isVisible) return

        val title = titleField.text ?: ""
        val content = contentArea.text ?: ""

        val id = selectedId
        if (id == null) {
            val newId = service.createNote(title, content, notebookId = null) // Root by default
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
    }

    // ---------- Delete / Trash ----------
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

    private fun refreshTrashList() {
        val summaries = service.listTrashedNotes()
        trashItems.setAll(summaries.map { it.id to it.title })
    }

    // ---------- Savestates ----------
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
        LBdataname.text = if (revisions.isEmpty()) "Keine Speicherstände vorhanden" else "Revisionen für Notiz: $noteId"
    }

    private fun deleteSelectedSavestate() {
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

        LVsavestate.selectionModel.clearSelection()
        refreshSavestates()

        titleField.text = ""
        contentArea.text = ""
        LBlastchange.text = "--"
        LBsaved.text = "Speicherstand gelöscht"
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

    // ---------- Trash countdown ----------
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

    // =========================================================
    // ✅ Tree Cell + Drag & Drop (VS Code style)
    // =========================================================

    private inner class NotebookTreeCell : TreeCell<NavNode>() {

        override fun updateItem(item: NavNode?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) "" else item.displayText()
        }

        init {
            // Drag start: only notes
            setOnDragDetected { e ->
                val node = item
                if (node is NavNode.NoteLeaf) {
                    val db: Dragboard = startDragAndDrop(TransferMode.MOVE)
                    val content = ClipboardContent()
                    content.putString(node.noteId.value)
                    db.setContent(content)
                    e.consume()
                }
            }

            // Drag over: accept on folders OR on root (tree background/root cell)
            setOnDragOver { e: DragEvent ->
                val dragId = e.dragboard.string
                if (dragId.isNullOrBlank()) return@setOnDragOver

                val target = item
                val accept = when (target) {
                    is NavNode.NotebookBranch -> true
                    is NavNode.RootHeader -> true
                    else -> false
                }

                if (accept) {
                    e.acceptTransferModes(TransferMode.MOVE)
                }
                e.consume()
            }

            // Drop: move note
            setOnDragDropped { e ->
                val dragId = e.dragboard.string
                if (dragId.isNullOrBlank()) {
                    e.isDropCompleted = false
                    return@setOnDragDropped
                }

                val target = item
                val noteId = NoteId(dragId)

                when (target) {
                    is NavNode.NotebookBranch -> {
                        service.moveNoteToNotebook(noteId, target.notebookId)
                        e.isDropCompleted = true
                        refreshNotebookTree()
                        openNote(noteId.value, inTrash = false)
                    }
                    is NavNode.RootHeader -> {
                        service.moveNoteToNotebook(noteId, null) // back to root
                        e.isDropCompleted = true
                        refreshNotebookTree()
                        openNote(noteId.value, inTrash = false)
                    }
                    else -> e.isDropCompleted = false
                }
                e.consume()
            }
        }
    }

    // ---------- Tree Node Types ----------
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