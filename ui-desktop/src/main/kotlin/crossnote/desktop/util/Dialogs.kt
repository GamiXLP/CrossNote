package crossnote.desktop.util

import crossnote.desktop.ThemeManager
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType

object Dialogs {

    /**
     * Wird einmal beim App-Start gesetzt (z.B. in MainController.initialize()).
     * Dann werden alle Alerts automatisch im aktuellen Theme angezeigt.
     */
    private var themeManager: ThemeManager? = null

    fun init(themeManager: ThemeManager) {
        this.themeManager = themeManager
    }

    private fun applyTheme(alert: Alert) {
        // Alert ist ein Dialog -> ThemeManager kann Dialogs registrieren
        themeManager?.register(alert)
    }

    fun confirm(title: String, header: String, content: String): Boolean {
        val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
            this.title = title
            headerText = header
            contentText = content
            buttonTypes.setAll(ButtonType.CANCEL, ButtonType.OK)
        }

        applyTheme(confirm)

        val result = confirm.showAndWait()
        return result.isPresent && result.get() == ButtonType.OK
    }

    fun info(title: String, content: String) {
        val alert = Alert(Alert.AlertType.INFORMATION).apply {
            this.title = title
            headerText = null
            contentText = content
            buttonTypes.setAll(ButtonType.OK)
        }

        applyTheme(alert)

        alert.showAndWait()
    }

    fun error(title: String, content: String) {
        val alert = Alert(Alert.AlertType.ERROR).apply {
            this.title = title
            headerText = null
            contentText = content
            buttonTypes.setAll(ButtonType.OK)
        }

        applyTheme(alert)

        alert.showAndWait()
    }
}
