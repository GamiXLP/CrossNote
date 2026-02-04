package crossnote.desktop

import crossnote.app.note.NoteAppService
import crossnote.domain.note.Note
import crossnote.domain.note.NoteId
import crossnote.infra.persistence.SqliteDatabase
import crossnote.infra.persistence.SqliteNoteRepository
import crossnote.infra.persistence.SqliteRevisionRepository
import crossnote.infra.persistence.SystemClock
import crossnote.infra.persistence.UuidIdGenerator
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.MouseEvent
import javafx.scene.layout.AnchorPane
import javafx.stage.FileChooser
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Finaler Controller, der eure neue MainView.fxml mit dem Core verbindet:
 * - Notizen-Liste (APnotebooks)
 * - Papierkorb-Liste + Wiederherstellen
 * - Speichern / Neue Notiz
 * - Auto-Purge: Papierkorb > 30 Tage wird beim Start gelöscht
 *
 * Hinweis: "Speicherstände" ist hier als Platzhalter gelassen (UI bleibt bedienbar).
 */
class MainController {

    // --- LEFT: Layer-Panes im StackPane ---
    @FXML lateinit var APnotebooks: AnchorPane
    @FXML lateinit var APtrashcan: AnchorPane
    @FXML lateinit var APsavestate: AnchorPane

    // --- LEFT: Notebooks/Notes ---
    @FXML lateinit var TFnotebook: TextField
    @FXML lateinit var LVnotebook: ListView<NoteListItem>

    // --- LEFT: Trashcan ---
    @FXML lateinit var TFtrashcan: TextField
    @FXML lateinit var LVtrashcan: ListView<NoteListItem>
    @FXML lateinit var BTNrestore: Button

    // --- LEFT: Savestates ---
    @FXML lateinit var LBdataname: Label
    @FXML lateinit var LVsavestate: ListView<String>
    @FXML lateinit var BTNload: Button

    // --- LEFT: Bottom buttons ---
    @FXML lateinit var BTNtrashcan: Button
    @FXML lateinit var BTNsavestate: Button

    // --- TOP ---
    @FXML lateinit var BTNdarkmode: Button

    // --- CENTER ---
    @FXML lateinit var titleField: TextField
    @FXML lateinit var contentArea: TextArea
    @FXML lateinit var BTNsave: Button
    @FXML lateinit var BTNnewnote: Button
    @FXML lateinit var LBlastchange: Label
    @FXML lateinit var LBsaved: Label

    // --- Core / Persistence ---
    private val db = SqliteDatabase(
        Paths.get(System.getProperty("user.home"), ".crossnote", "crossnote.db")
    )

    private val service = NoteAppService(
        repo = SqliteNoteRepository(db),
        revisionRepo = SqliteRevisionRepository(db),
        ids = UuidIdGenerator(),
        clock = SystemClock()
    )

    // --- UI State ---
    private var darkMode = false
    private var currentNoteId: String? = null
    private var isDirty: Boolean = false

    // Listen + Filter
    private val notebookBase: ObservableList<NoteListItem> = FXCollections.observableArrayList()
    private val trashBase: ObservableList<NoteListItem> = FXCollections.observableArrayList()

    private lateinit var notebookFiltered: FilteredList<NoteListItem>
    private lateinit var trashFiltered: FilteredList<NoteListItem>

    @FXML
    fun initialize() {
        // 1) Auto-Cleanup: alles > 30 Tage im Papierkorb endgültig löschen
        service.purgeTrashedOlderThan(30)

        // 2) Left pane default: Notizen
        showLeftPane(APnotebooks)

        // 3) CellFactory (damit ListView sauber Titel zeigt)
        LVnotebook.setCellFactory {
            object : ListCell<NoteListItem>() {
                override fun updateItem(item: NoteListItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.title
                }
            }
        }
        LVtrashcan.setCellFactory {
            object : ListCell<NoteListItem>() {
                override fun updateItem(item: NoteListItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.title
                }
            }
        }

        // 4) Filtered Lists einrichten
        notebookFiltered = FilteredList(notebookBase) { true }
        trashFiltered = FilteredList(trashBase) { true }
        LVnotebook.items = notebookFiltered
        LVtrashcan.items = trashFiltered

        TFnotebook.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            notebookFiltered.setPredicate { it.title.lowercase().contains(q) }
        }
        TFtrashcan.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            trashFiltered.setPredicate { it.title.lowercase().contains(q) }
        }

        // 5) Kontextmenü: Papierkorb -> endgültig löschen
        LVtrashcan.contextMenu = ContextMenu().apply {
            val purge = MenuItem("Endgültig löschen").apply {
                setOnAction {
                    val selected = LVtrashcan.selectionModel.selectedItem ?: return@setOnAction
                    confirmAndPurge(selected)
                }
            }
            items.add(purge)
        }

        // 6) Buttons
        BTNtrashcan.setOnAction {
            showLeftPane(APtrashcan)
            refreshTrashList()
        }
        BTNsavestate.setOnAction {
            showLeftPane(APsavestate)
            // bleibt erstmal placeholder
        }

        BTNrestore.setOnAction { restoreSelectedTrashItem() }
        BTNsave.setOnAction { saveNote() }
        BTNnewnote.setOnAction { newNote() }
        BTNload.setOnAction { loadSelectedSavestatePlaceholder() }

        // 7) List selection -> Note laden
        LVnotebook.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) openNote(new)
        }
        LVtrashcan.selectionModel.selectedItemProperty().addListener { _, _, new ->
            // Papierkorb: Note darf angezeigt werden, aber wir lassen Editing zu/aus je nach Wunsch.
            // Für MVP: anzeigen, aber nicht automatisch speichern.
            if (new != null) openTrashNote(new)
        }

        // 8) Dirty Tracking: sobald der User tippt, setzen wir "Nicht gespeichert"
        titleField.textProperty().addListener { _, _, _ -> markDirty() }
        contentArea.textProperty().addListener { _, _, _ -> markDirty() }

        // 9) Initiale Labels + Daten
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        LBdataname.text = "Dateiname"

        // Demo-Savestates (placeholder)
        LVsavestate.items = FXCollections.observableArrayList(
            "backup-2026-02-01.json",
            "backup-2026-01-15.json"
        )

        refreshNotebookList()
    }

    /**
     * Handler aus deiner FXML:
     * <Button ... onMouseClicked="#dasdakj" ... />
     */
    @FXML
    fun dasdakj(event: MouseEvent) {
        val root = (event.source as? Node)?.scene?.root ?: return
        darkMode = !darkMode

        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
            BTNdarkmode.text = "Light Mode"
        } else {
            root.styleClass.remove("dark")
            BTNdarkmode.text = "Dark Mode"
        }
    }

    // ----------------------------
    // Notes Pane (APnotebooks)
    // ----------------------------

    private fun refreshNotebookList() {
        val notes = service.listActiveNotes()
        notebookBase.setAll(notes.map { NoteListItem(it.id, it.title) })
    }

    private fun openNote(item: NoteListItem) {
        // Wenn ungespeichert, warnen (minimal)
        if (isDirty && currentNoteId != item.id) {
            // MVP: keine komplizierte Merge-Logik, nur Hinweis
            // Du kannst hier auch Dialog "Speichern?" machen
        }

        val note = service.getNote(NoteId(item.id))
        currentNoteId = note.id.value

        // Setzen ohne dirty neu auszulösen
        setEditorContent(note)

        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = formatInstant(note.updatedAt)
    }

    private fun newNote() {
        currentNoteId = null
        titleField.clear()
        contentArea.clear()
        isDirty = false
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        titleField.requestFocus()
    }

    private fun saveNote() {
        // Nicht im Papierkorb speichern
        if (APtrashcan.isVisible) return

        val title = titleField.text ?: ""
        val content = contentArea.text ?: ""

        val id = currentNoteId
        if (id == null) {
            val newId = service.createNote(title, content)
            currentNoteId = newId.value
            LBsaved.text = "Gespeichert"
        } else {
            service.updateNote(NoteId(id), title, content)
            LBsaved.text = "Gespeichert"
        }

        isDirty = false
        val refreshed = service.getNote(NoteId(currentNoteId!!))
        LBlastchange.text = formatInstant(refreshed.updatedAt)

        refreshNotebookList()

        // Selektiere gespeicherte Note in der Liste
        selectInList(LVnotebook, currentNoteId!!)
    }

    // ----------------------------
    // Trash Pane (APtrashcan)
    // ----------------------------

    private fun refreshTrashList() {
        val notes = service.listTrashedNotes()
        trashBase.setAll(notes.map { NoteListItem(it.id, it.title) })
    }

    private fun restoreSelectedTrashItem() {
        val selected = LVtrashcan.selectionModel.selectedItem ?: return
        service.restore(NoteId(selected.id))

        // UI refresh
        refreshTrashList()
        refreshNotebookList()
        clearEditorForTrashAction("Wiederhergestellt")
    }

    private fun confirmAndPurge(selected: NoteListItem) {
        val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Endgültig löschen"
            headerText = "Notiz endgültig löschen?"
            contentText = "Diese Notiz wird dauerhaft entfernt und kann nicht wiederhergestellt werden."
            buttonTypes.setAll(ButtonType.CANCEL, ButtonType.OK)
        }

        val result = alert.showAndWait()
        if (result.isPresent && result.get() == ButtonType.OK) {
            service.purgeNotePermanently(NoteId(selected.id))
            refreshTrashList()
            clearEditorForTrashAction("Endgültig gelöscht")
        }
    }

    private fun openTrashNote(item: NoteListItem) {
        val note = service.getNote(NoteId(item.id))
        currentNoteId = note.id.value

        // Anzeigen, aber wir deaktivieren Save/New, solange Trash pane aktiv ist
        setEditorContent(note)
        LBsaved.text = "Papierkorb"
        LBlastchange.text = formatInstant(note.updatedAt)
    }

    private fun clearEditorForTrashAction(status: String) {
        // Editor leeren und Status setzen
        currentNoteId = null
        titleField.clear()
        contentArea.clear()
        isDirty = false
        LBsaved.text = status
        LBlastchange.text = "--"
    }

    // ----------------------------
    // Savestates Pane (APsavestate) - Placeholder
    // ----------------------------

    private fun loadSelectedSavestatePlaceholder() {
        val selected = LVsavestate.selectionModel.selectedItem ?: return
        LBdataname.text = selected
        LBsaved.text = "Geladen (Platzhalter)"
        LBlastchange.text = nowString()
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private fun showLeftPane(which: AnchorPane) {
        val panes = listOf(APnotebooks, APtrashcan, APsavestate)
        panes.forEach {
            it.isVisible = false
            it.isManaged = false
        }

        which.isVisible = true
        which.isManaged = true

        // Modusabhängige Buttons (Papierkorb soll nicht "speichern" können)
        val inTrash = which == APtrashcan
        BTNsave.isDisable = inTrash
        BTNnewnote.isDisable = inTrash
    }

    private fun markDirty() {
        // Wenn gerade keine Note geladen ist und der User tippt, gilt das auch als "dirty"
        // Wir setzen nur, wenn wir nicht sowieso schon "Gespeichert" anzeigen.
        isDirty = true
        if (!APtrashcan.isVisible) {
            LBsaved.text = "Nicht gespeichert"
        }
    }

    private fun setEditorContent(note: Note) {
        // Editor befüllen ohne dirty-Flag dauerhaft zu versauen:
        // Wir setzen kurz isDirty=false, füllen, dann wieder false.
        isDirty = false
        titleField.text = note.title
        contentArea.text = note.content
        isDirty = false
    }

    private fun selectInList(list: ListView<NoteListItem>, id: String) {
        val idx = list.items.indexOfFirst { it.id == id }
        if (idx >= 0) list.selectionModel.select(idx)
    }

    private fun formatInstant(instant: Instant): String {
        val dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return dt.format(fmt)
    }

    private fun nowString(): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return LocalDateTime.now().format(fmt)
    }
}

/**
 * UI-List item: id + title
 * toString() ist nicht nötig, weil wir eine CellFactory verwenden.
 */
data class NoteListItem(val id: String, val title: String)
