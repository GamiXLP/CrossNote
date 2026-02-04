package crossnote.desktop

import crossnote.app.note.NoteAppService
import crossnote.domain.note.NoteId
import crossnote.domain.revision.RevisionId
import crossnote.infra.persistence.*
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import java.nio.file.Paths
import java.time.Duration

class MainController {

    // ---------- Persistence / Service ----------
    private val db = SqliteDatabase(Paths.get(System.getProperty("user.home"), ".crossnote", "crossnote.db"))
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
    @FXML lateinit var LVnotebook: ListView<Pair<String, String>> // (id, title)

    // ---------- Trash (links) ----------
    @FXML lateinit var TFtrashcan: TextField
    @FXML lateinit var LVtrashcan: ListView<Pair<String, String>> // (id, title)
    @FXML lateinit var BTNrestore: Button

    // ---------- Savestates (links) ----------
    @FXML lateinit var LBdataname: Label
    @FXML lateinit var LVsavestate: ListView<Pair<String, String>> // (revisionId, createdAtIso)
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
    private val notebookItems = FXCollections.observableArrayList<Pair<String, String>>()
    private val trashItems = FXCollections.observableArrayList<Pair<String, String>>()
    private val savestateItems = FXCollections.observableArrayList<Pair<String, String>>() // (revId, createdAt)

    private lateinit var notebookFiltered: FilteredList<Pair<String, String>>
    private lateinit var trashFiltered: FilteredList<Pair<String, String>>

    private var selectedId: String? = null
    private var lastActiveNoteId: String? = null
    private var darkMode: Boolean = false

    @FXML
    fun initialize() {
        // Auto-Purge: alles > 30 Tage im Papierkorb endgültig löschen
        service.purgeTrashedOlderThan(30)

        // Default-Ansicht: Notizen
        showLeftPane(APnotebooks)

        // Filterlisten
        notebookFiltered = FilteredList(notebookItems) { true }
        trashFiltered = FilteredList(trashItems) { true }
        LVnotebook.items = notebookFiltered
        LVtrashcan.items = trashFiltered

        // CellFactory: zeige Titel
        LVnotebook.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }
        LVtrashcan.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        // Savestates list
        LVsavestate.items = savestateItems
        LVsavestate.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        // Suchfelder
        TFnotebook.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            notebookFiltered.setPredicate { it.second.lowercase().contains(q) }
        }
        TFtrashcan.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            trashFiltered.setPredicate { it.second.lowercase().contains(q) }
        }

        // Auswahl -> Note öffnen
        LVnotebook.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) openNote(new.first, inTrash = false)
        }
        LVtrashcan.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) openNote(new.first, inTrash = true)
        }

        // Toggle: Papierkorb <-> Notizen
        BTNtrashcan.setOnAction {
            if (APtrashcan.isVisible) {
                showLeftPane(APnotebooks)
                clearEditorAndSelections()
                refreshNotebookList()
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
                refreshNotebookList()
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

        // ✅ Löschen-Button (neu)
        BTNdelete.setOnAction { onDelete() }

        // Papierkorb Kontextmenü: Endgültig löschen
        LVtrashcan.contextMenu = ContextMenu().apply {
            val purgeItem = MenuItem("Endgültig löschen").apply {
                setOnAction { onPurgeSelectedTrash() }
            }
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
        BTNload.setOnAction { onLoadSavestateRevision() }

        // Initial status
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        LBdataname.text = "Keine Notiz ausgewählt"

        refreshNotebookList()
        setCenterMode()
    }

    @FXML
    fun darkmode_on() {
        val root = BTNdarkmode.scene.root
        darkMode = !darkMode

        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
            BTNdarkmode.text = "Light Mode"
        } else {
            root.styleClass.remove("dark")
            BTNdarkmode.text = "Dark Mode"
        }
    }

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

        // Delete immer möglich, aber Semantik hängt vom Modus ab
        BTNdelete.text = if (inTrash) "Endgültig löschen" else "Löschen"
    }

    private fun clearEditorAndSelections() {
        selectedId = null
        titleField.text = ""
        contentArea.text = ""
        LBlastchange.text = "--"
        LBsaved.text = "Nicht gespeichert"

        LVnotebook.selectionModel.clearSelection()
        LVtrashcan.selectionModel.clearSelection()
        LVsavestate.selectionModel.clearSelection()
    }

    private fun refreshNotebookList() {
        val summaries = service.listActiveNotes()
        notebookItems.setAll(summaries.map { it.id to it.title })
    }

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

        val revisions = service.listRevisions(NoteId(noteId))
        savestateItems.setAll(revisions.map { it.id to it.createdAtIso })

        LBdataname.text = if (revisions.isEmpty()) "Keine Revisionen vorhanden" else "Revisionen für Notiz: $noteId"
    }

    private fun openNote(noteId: String, inTrash: Boolean) {
        selectedId = noteId
        if (!inTrash) lastActiveNoteId = noteId

        val note = service.getNote(NoteId(noteId))
        titleField.text = note.title
        contentArea.text = note.content
        LBlastchange.text = note.updatedAt.toString()

        if (inTrash) {
            LBsaved.text = trashCountdownText(noteId)
        } else {
            LBsaved.text = "Nicht gespeichert"
        }
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

        refreshNotebookList()
        selectInList(LVnotebook, selectedId!!)
    }

    /**
     * ✅ Neuer Delete-Handler:
     * - Notizenansicht: moveToTrash
     * - Papierkorb: purge (endgültig)
     */
    private fun onDelete() {
        val id = selectedId ?: return

        if (APtrashcan.isVisible) {
            // endgültig löschen
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
        } else if (APnotebooks.isVisible) {
            // in Papierkorb verschieben
            service.moveToTrash(NoteId(id))
            clearEditorAndSelections()
            refreshNotebookList()
            // Optional: direkt Trash aktualisieren, falls später reingeschaltet wird
            refreshTrashList()
        } else {
            // in Savestates: nichts löschen
        }
    }

    private fun onRestore() {
        if (!APtrashcan.isVisible) return
        val id = selectedId ?: return
        service.restore(NoteId(id))

        clearEditorAndSelections()
        refreshTrashList()
        refreshNotebookList()
    }

    private fun onPurgeSelectedTrash() {
        if (!APtrashcan.isVisible) return
        val selected = LVtrashcan.selectionModel.selectedItem ?: return
        val id = selected.first

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

    private fun onLoadSavestateRevision() {
        if (!APsavestate.isVisible) return

        val noteId = lastActiveNoteId
        if (noteId == null) {
            LBdataname.text = "Keine Notiz ausgewählt"
            return
        }

        val selectedRev = LVsavestate.selectionModel.selectedItem ?: return
        val revisionId = RevisionId(selectedRev.first)

        service.restoreFromRevision(NoteId(noteId), revisionId)

        showLeftPane(APnotebooks)
        clearEditorAndSelections()
        refreshNotebookList()
        setCenterMode()

        openNote(noteId, inTrash = false)
        selectInList(LVnotebook, noteId)
        LBsaved.text = "Auf Revision zurückgesetzt"
    }

    /**
     * Countdown-Text: wie viele Tage noch im Papierkorb bis Auto-Purge (30 Tage)
     * Anzeige nutzen wir über LBsaved.
     */
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

    private fun selectInList(list: ListView<Pair<String, String>>, id: String) {
        val idx = list.items.indexOfFirst { it.first == id }
        if (idx >= 0) list.selectionModel.select(idx)
    }

    fun close() {
        db.close()
    }
}
