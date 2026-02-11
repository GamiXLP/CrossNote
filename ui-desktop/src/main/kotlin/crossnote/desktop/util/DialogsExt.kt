package crossnote.desktop.util

import javafx.scene.control.Alert

object DialogsExt {
    fun warn(message: String) {
        Alert(Alert.AlertType.WARNING).apply {
            title = "Warnung"
            headerText = null
            contentText = message
        }.showAndWait()
    }
}
