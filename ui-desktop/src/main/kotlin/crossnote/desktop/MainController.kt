package crossnote.desktop

import crossnote.app.note.NoteAppService
import crossnote.domain.note.NoteId
import crossnote.domain.revision.RevisionId
import crossnote.infra.persistence.*
import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean

import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.MouseEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.geometry.Insets
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.event.ActionEvent
import java.nio.file.Paths

/**
 * MainController passend zu eurer neuen MainView.fxml
 * - Notizen (links) + Suche
 * - Papierkorb (links) + Suche + Wiederherstellen + Endgültig löschen (Context Menü)
 * - Speicherstände (links): zeigt Revisionen der zuletzt geöffneten Notiz
 * - Toggle-Navigation: Papierkorb/Speicherstände erneut klicken -> zurück zur Notizen-Ansicht
 * - Dark Mode Toggle (FXML: onMouseClicked="#dasdakj")
 */
class MainController {

    // ---------- Persistence / Service ----------
    private val db = SqliteDatabase(Paths.get(System.getProperty("user.home"), ".crossnote", "crossnote.db"))
    private val settingsRepo = SqliteSettingsRepository(db)
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
    private var lastActiveNoteId: String? = null   // <- wichtig für Revisionen/Speicherstände
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

        // Savestates (Revisionen) CellFactory: zeige Datum/Uhrzeit
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

        // Papierkorb Kontextmenü: Endgültig löschen
        LVtrashcan.contextMenu = ContextMenu().apply {
            val purgeItem = MenuItem("Endgültig löschen").apply {
                setOnAction { onPurgeSelectedTrash() }
            }
            items.add(purgeItem)
        }

        LVsavestate.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) {
                // new = (revisionId, createdAtIso)
                LBdataname.text = new.second

                // Revision laden und rechts anzeigen (Preview)
                val rev = service.getRevision(RevisionId(new.first))

                titleField.text = rev.title
                contentArea.text = rev.content

                // Optional: Status/Datum passend setzen
                LBsaved.text = "Vorschau (Revision)"
                LBlastchange.text = rev.createdAt.toString()
            }
        }

        // Speicherstände: Laden = Revision wiederherstellen
        BTNload.setOnAction { onLoadSavestateRevision() }

        // Initial status
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        LBdataname.text = "Keine Notiz ausgewählt"

        // Initial load
        refreshNotebookList()
        setCenterMode()

        BTNdarkmode.sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                darkMode = settingsRepo.getBoolean("darkMode", false)
                applyTheme()
            }
        }

    }


    @FXML
    fun darkmode_on(event: ActionEvent) {

        darkMode = !darkMode

        applyTheme()

        settingsRepo.setBoolean("darkMode", darkMode)
    }


    // ----- Left Pane Switch -----
    private fun showLeftPane(which: AnchorPane) {
        val panes = listOf(APnotebooks, APtrashcan, APsavestate)
        panes.forEach {
            it.isVisible = false
            it.isManaged = false
        }
        which.isVisible = true
        which.isManaged = true
    }

    // ----- Center Mode (disable save/new when trash visible OR savestate visible) -----
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

    // ----- Data refresh -----
    private fun refreshNotebookList() {
        val summaries = service.listActiveNotes()
        notebookItems.setAll(summaries.map { it.id to it.title })
    }

    private fun refreshTrashList() {
        val summaries = service.listTrashedNotes()
        trashItems.setAll(summaries.map { it.id to it.title })
    }

    /**
     * Speicherstände = Revisionen der zuletzt geöffneten Notiz.
     */
    private fun refreshSavestates() {
        val noteId = lastActiveNoteId
        if (noteId == null) {
            savestateItems.clear()
            LBdataname.text = "Keine Notiz ausgewählt"
            return
        }

        val revisions = service.listRevisions(NoteId(noteId))
        savestateItems.setAll(revisions.map { it.id to it.createdAtIso })

        LBdataname.text = if (revisions.isEmpty()) {
            "Keine Revisionen vorhanden"
        } else {
            "Revisionen für Notiz: $noteId"
        }
    }

    // ----- Open / New / Save -----
    private fun openNote(noteId: String, inTrash: Boolean) {
        selectedId = noteId

        // Nur aktive Notizen setzen wir als "letzte aktive Note" für Revisionen
        if (!inTrash) lastActiveNoteId = noteId

        val note = service.getNote(NoteId(noteId))
        titleField.text = note.title
        contentArea.text = note.content
        LBlastchange.text = note.updatedAt.toString()
        LBsaved.text = if (inTrash) "Papierkorb" else "Nicht gespeichert"
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

    // ----- Trash actions -----
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

    // ----- Savestates actions -----
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

        // Danach zurück zur Notizenansicht + Note laden
        showLeftPane(APnotebooks)
        clearEditorAndSelections()
        refreshNotebookList()
        setCenterMode()

        // Note öffnen, damit man direkt Ergebnis sieht
        openNote(noteId, inTrash = false)
        selectInList(LVnotebook, noteId)

        LBsaved.text = "Auf Revision zurückgesetzt"
    }

    // ----- (Optional) Revisionen Dialog (nicht direkt in neuer FXML verknüpft) -----
    @Suppress("unused")
    private fun openRevisionsForCurrentNote() {
        if (!APnotebooks.isVisible) return
        val id = selectedId ?: return
        val stage = LVnotebook.scene.window as Stage
        openRevisionsDialog(stage, NoteId(id)) {
            val refreshed = service.getNote(NoteId(id))
            titleField.text = refreshed.title
            contentArea.text = refreshed.content
            refreshNotebookList()
            LBsaved.text = "Auf Revision zurückgesetzt"
            LBlastchange.text = refreshed.updatedAt.toString()
        }
    }

    private fun openRevisionsDialog(owner: Stage, noteId: NoteId, onRestored: () -> Unit) {
        val revisions = service.listRevisions(noteId)
        val items = FXCollections.observableArrayList(revisions.map { it.id to it.createdAtIso })

        val listView = ListView<Pair<String, String>>(items).apply {
            setCellFactory {
                object : ListCell<Pair<String, String>>() {
                    override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = if (empty || item == null) "" else item.second
                    }
                }
            }
        }

        val restoreButton = Button("Auf Revision zurücksetzen")
        val infoLabel = Label("Wähle eine Revision aus")

        restoreButton.setOnAction {
            val selected = listView.selectionModel.selectedItem ?: return@setOnAction
            val revId = RevisionId(selected.first)
            service.restoreFromRevision(noteId, revId)
            onRestored()
            infoLabel.text = "Wiederhergestellt ✅"
        }

        val root = VBox(10.0, Label("Revisionen (neueste zuerst)"), listView, restoreButton, infoLabel).apply {
            padding = Insets(12.0)
            VBox.setVgrow(listView, Priority.ALWAYS)
        }

        val dialog = Stage().apply {
            initOwner(owner)
            initModality(Modality.WINDOW_MODAL)
            title = "Revisionen"
            scene = javafx.scene.Scene(root, 520.0, 520.0)
        }
        dialog.show()
    }

    private fun applyTheme() {
        val root = BTNdarkmode.scene.root

        if (darkMode) {
            if (!root.styleClass.contains("dark")) {
                root.styleClass.add("dark")
            }
            BTNdarkmode.text = "Light Mode"
        } else {
            root.styleClass.remove("dark")
            BTNdarkmode.text = "Dark Mode"
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
