package crossnote.desktop

import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.*

class MainController {

    @FXML lateinit var notesList: ListView<String>
    @FXML lateinit var titleField: TextField
    @FXML lateinit var contentArea: TextArea
    @FXML lateinit var trashToggle: CheckBox
    @FXML lateinit var statusLabel: Label

    @FXML lateinit var newButton: Button
    @FXML lateinit var saveButton: Button
    @FXML lateinit var trashButton: Button
    @FXML lateinit var restoreButton: Button
    @FXML lateinit var purgeButton: Button
    @FXML lateinit var revisionsButton: Button

    private val items = FXCollections.observableArrayList<String>()

    @FXML
    fun initialize() {
        notesList.items = items
        contentArea.isWrapText = true
        statusLabel.text = "Bereit"
    }
}
