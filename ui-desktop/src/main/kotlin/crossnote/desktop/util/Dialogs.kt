package crossnote.desktop.util

import javafx.scene.control.Alert
import javafx.scene.control.ButtonType

object Dialogs {

    fun confirm(title: String, header: String, content: String): Boolean {
        val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
            this.title = title
            headerText = header
            contentText = content
            buttonTypes.setAll(ButtonType.CANCEL, ButtonType.OK)
        }
        val result = confirm.showAndWait()
        return result.isPresent && result.get() == ButtonType.OK
    }

    fun info(title: String, content: String) {
        Alert(Alert.AlertType.INFORMATION).apply {
            this.title = title
            headerText = null
            contentText = content
            buttonTypes.setAll(ButtonType.OK)
        }.showAndWait()
    }

    fun error(title: String, content: String) {
        Alert(Alert.AlertType.ERROR).apply {
            this.title = title
            headerText = null
            contentText = content
            buttonTypes.setAll(ButtonType.OK)
        }.showAndWait()
    }
}