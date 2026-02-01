package crossnote.desktop

import crossnote.app.note.NoteAppService
import crossnote.domain.note.NoteId
import crossnote.infra.persistence.InMemoryNoteRepository
import crossnote.infra.persistence.SystemClock
import crossnote.infra.persistence.UuidIdGenerator
import javafx.application.Application
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage

class DesktopApp : Application() {

    private val service = NoteAppService(
        repo = InMemoryNoteRepository(),
        ids = UuidIdGenerator(),
        clock = SystemClock()
    )

    private val listItems = FXCollections.observableArrayList<Pair<String, String>>() // (id, title)
    private var selectedId: String? = null
    private var showTrash: Boolean = false

    override fun start(stage: Stage) {
        val listView = ListView<Pair<String, String>>(listItems).apply {
            prefWidth = 260.0
            setCellFactory {
                object : ListCell<Pair<String, String>>() {
                    override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = if (empty || item == null) "" else item.second
                    }
                }
            }
        }

        val titleField = TextField().apply { promptText = "Titel" }
        val contentArea = TextArea().apply {
            promptText = "Inhalt"
            isWrapText = true
        }
        val statusLabel = Label("Bereit")

        fun clearEditor() {
            selectedId = null
            titleField.text = ""
            contentArea.text = ""
        }

        fun refreshList() {
            val summaries = if (showTrash) service.listTrashedNotes() else service.listActiveNotes()
            listItems.setAll(summaries.map { it.id to it.title })
        }

        val trashToggle = CheckBox("Papierkorb anzeigen").apply {
            selectedProperty().addListener { _, _, newValue ->
                showTrash = newValue
                clearEditor()
                listView.selectionModel.clearSelection()
                refreshList()
                statusLabel.text = if (showTrash) "Papierkorb" else "Notizen"
            }
        }

        val newButton = Button("Neu").apply {
            setOnAction {
                if (showTrash) return@setOnAction
                clearEditor()
                statusLabel.text = "Neue Notiz"
                titleField.requestFocus()
                listView.selectionModel.clearSelection()
            }
        }

        val saveButton = Button("Speichern").apply {
            setOnAction {
                if (showTrash) return@setOnAction

                val title = titleField.text ?: ""
                val content = contentArea.text ?: ""

                val id = selectedId
                if (id == null) {
                    val newId = service.createNote(title, content)
                    selectedId = newId.value
                    statusLabel.text = "Gespeichert (neu)"
                } else {
                    service.updateNote(NoteId(id), title, content)
                    statusLabel.text = "Gespeichert"
                }

                refreshList()
                val idx = listItems.indexOfFirst { it.first == selectedId }
                if (idx >= 0) listView.selectionModel.select(idx)
            }
        }

        val trashButton = Button("In Papierkorb").apply {
            setOnAction {
                if (showTrash) return@setOnAction
                val id = selectedId ?: return@setOnAction

                service.moveToTrash(NoteId(id))
                statusLabel.text = "In Papierkorb verschoben"
                clearEditor()
                refreshList()
                listView.selectionModel.clearSelection()
            }
        }

        val restoreButton = Button("Wiederherstellen").apply {
            isVisible = false
            isManaged = false
            setOnAction {
                if (!showTrash) return@setOnAction
                val id = selectedId ?: return@setOnAction

                service.restore(NoteId(id))
                statusLabel.text = "Wiederhergestellt"
                clearEditor()
                refreshList()
                listView.selectionModel.clearSelection()
            }
        }

        fun updateModeButtons() {
            restoreButton.isVisible = showTrash
            restoreButton.isManaged = showTrash

            trashButton.isVisible = !showTrash
            trashButton.isManaged = !showTrash

            saveButton.isDisable = showTrash
            newButton.isDisable = showTrash
        }

        trashToggle.selectedProperty().addListener { _, _, _ ->
            updateModeButtons()
        }

        listView.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) {
                selectedId = new.first
                val note = service.getNote(NoteId(new.first))
                titleField.text = note.title
                contentArea.text = note.content
                statusLabel.text = if (showTrash) "Papierkorb: geöffnet" else "Geöffnet"
            }
        }

        // Initial
        updateModeButtons()
        refreshList()

        val buttons = HBox(10.0, newButton, saveButton, trashButton, restoreButton)
        val editor = VBox(10.0, trashToggle, titleField, contentArea, buttons, statusLabel).apply {
            padding = Insets(12.0)
            VBox.setVgrow(contentArea, Priority.ALWAYS)
        }

        val root = BorderPane().apply {
            left = listView
            center = editor
        }

        stage.title = "CrossNote (Papierkorb MVP)"
        stage.scene = Scene(root, 980.0, 680.0)
        stage.show()
    }
}

fun main() {
    Application.launch(DesktopApp::class.java)
}