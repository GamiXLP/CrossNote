package crossnote.desktop

import crossnote.infra.persistence.SqliteSettingsRepository
import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import javafx.scene.Parent
import javafx.scene.control.Button

class ThemeManager(
    private val settingsRepo: SqliteSettingsRepository,
    private val toggleButton: Button,
) {
    private var darkMode: Boolean = false

    fun bindToSceneRoot() {
        toggleButton.sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                darkMode = settingsRepo.getBoolean("darkMode", false)
                apply(scene.root)
            }
        }
    }

    fun toggle() {
        val scene = toggleButton.scene ?: return
        darkMode = !darkMode
        apply(scene.root)
        settingsRepo.setBoolean("darkMode", darkMode)
    }

    private fun apply(root: Parent) {
        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
            toggleButton.text = "Light Mode"
        } else {
            root.styleClass.remove("dark")
            toggleButton.text = "Dark Mode"
        }
    }
}
