package crossnote.desktop.ui

import javafx.scene.control.Button
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.AnchorPane

class UiStateController(
    private val notebooksPane: AnchorPane,
    private val trashPane: AnchorPane,
    private val savestatePane: AnchorPane,
    private val saveButton: Button,
    private val loadButton: Button,
    private val backButton: Button,
    private val titleField: TextField,
    private val contentArea: TextArea,
) {
    fun showNotebooks() {
        showLeftPane(notebooksPane)
        applyCenterMode()
    }

    fun showTrash() {
        showLeftPane(trashPane)
        applyCenterMode()
    }

    fun showSavestates() {
        showLeftPane(savestatePane)
        applyCenterMode()
    }

    fun isNotebooksVisible(): Boolean = notebooksPane.isVisible
    fun isTrashVisible(): Boolean = trashPane.isVisible
    fun isSavestateVisible(): Boolean = savestatePane.isVisible

    private fun showLeftPane(which: AnchorPane) {
        listOf(notebooksPane, trashPane, savestatePane).forEach {
            it.isVisible = false
            it.isManaged = false
        }
        which.isVisible = true
        which.isManaged = true
    }

    fun applyCenterMode() {
        val locked = trashPane.isVisible || savestatePane.isVisible

        saveButton.isDisable = locked
        loadButton.isDisable = !savestatePane.isVisible

        titleField.isEditable = !locked
        contentArea.isEditable = !locked

        backButton.isVisible = savestatePane.isVisible
        backButton.isManaged = savestatePane.isVisible
    }
}