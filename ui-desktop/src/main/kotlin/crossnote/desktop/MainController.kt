package crossnote.desktop

import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.input.MouseEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.BorderPane
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainController {

    // --- LEFT: Layer-Panes im StackPane ---
    @FXML lateinit var APnotebooks: AnchorPane
    @FXML lateinit var APtrashcan: AnchorPane
    @FXML lateinit var APsavestate: AnchorPane

    // --- LEFT: Notebooks/Notes ---
    @FXML lateinit var TFnotebook: TextField
    @FXML lateinit var LVnotebook: ListView<String>

    // --- LEFT: Trashcan ---
    @FXML lateinit var TFtrashcan: TextField
    @FXML lateinit var LVtrashcan: ListView<String>
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

    // Filter-Listen
    private lateinit var notebookFiltered: FilteredList<String>
    private lateinit var trashFiltered: FilteredList<String>

    // Dark mode toggle
    private var darkMode = false

    @FXML
    fun initialize() {
        // Standardansicht links
        showLeftPane(APsavestate) // in deiner FXML ist APsavestate initial visible (kein visible="false")
        // Wenn du stattdessen Notebooks als Default willst:
        // showLeftPane(APnotebooks)

        // Beispiel-Daten (damit du sofort siehst, dass Resize/Filter funktionieren)
        val notebooks = FXCollections.observableArrayList(
            "Privat", "Studium", "Arbeit", "Ideen", "Projekte"
        )
        val trash = FXCollections.observableArrayList(
            "Gelöschte Notiz 1", "Alte Skizze", "Entwurf X"
        )
        val saves = FXCollections.observableArrayList(
            "backup-2026-01-01.json", "backup-2026-01-15.json", "backup-2026-02-01.json"
        )

        notebookFiltered = FilteredList(notebooks) { true }
        trashFiltered = FilteredList(trash) { true }

        LVnotebook.items = notebookFiltered
        LVtrashcan.items = trashFiltered
        LVsavestate.items = saves

        // Filter-Logik
        TFnotebook.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            notebookFiltered.setPredicate { it.lowercase().contains(q) }
        }
        TFtrashcan.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            trashFiltered.setPredicate { it.lowercase().contains(q) }
        }

        // Buttons (onAction per Code – du kannst alternativ im SceneBuilder onAction setzen)
        BTNtrashcan.setOnAction { showLeftPane(APtrashcan) }
        BTNsavestate.setOnAction { showLeftPane(APsavestate) }

        BTNrestore.setOnAction { restoreSelectedTrashItem() }
        BTNload.setOnAction { loadSelectedSavestate() }

        BTNsave.setOnAction { saveNote() }
        BTNnewnote.setOnAction { newNote() }

        // Initiale Statuslabels
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        LBdataname.text = "Dateiname"
    }

    /**
     * Handler aus deiner FXML:
     * <Button ... onMouseClicked="#dasdakj" ... />
     */
    @FXML
    fun dasdakj(event: MouseEvent) {
        // "Dark Mode" per StyleClass toggeln.
        // Wichtig: root hat kein fx:id, daher nehmen wir ein beliebiges Node und gehen über scene.root.
        val root = (event.source as? javafx.scene.Node)?.scene?.root ?: return

        darkMode = !darkMode

        // Du kannst in deiner styles.css dann .dark .root { ... } etc. definieren.
        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
            BTNdarkmode.text = "Light Mode"
        } else {
            root.styleClass.remove("dark")
            BTNdarkmode.text = "Dark Mode"
        }
    }

    private fun showLeftPane(which: AnchorPane) {
        // Alle ausblenden + unmanaged, damit sie keinen Platz beanspruchen
        val panes = listOf(APnotebooks, APtrashcan, APsavestate)
        panes.forEach {
            it.isVisible = false
            it.isManaged = false
        }

        which.isVisible = true
        which.isManaged = true
    }

    private fun restoreSelectedTrashItem() {
        val selected = LVtrashcan.selectionModel.selectedItem ?: return
        // Demo: wiederherstellen -> in notebooks hinzufügen
        (notebookFiltered.source as? MutableList<String>)?.add("Wiederhergestellt: $selected")
        LVtrashcan.items.remove(selected)
    }

    private fun loadSelectedSavestate() {
        val selected = LVsavestate.selectionModel.selectedItem ?: return
        LBdataname.text = selected
        // Hier würdest du später wirklich laden.
        LBsaved.text = "Geladen"
        LBlastchange.text = nowString()
    }

    private fun saveNote() {
        // Demo-Speichern
        LBsaved.text = "Gespeichert"
        LBlastchange.text = nowString()
    }

    private fun newNote() {
        titleField.clear()
        contentArea.clear()
        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        titleField.requestFocus()
    }

    private fun nowString(): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return LocalDateTime.now().format(fmt)
    }
}
