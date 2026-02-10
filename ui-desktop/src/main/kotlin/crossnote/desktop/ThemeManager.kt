package crossnote.desktop

import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import crossnote.infra.persistence.SqliteSettingsRepository
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Dialog
import java.util.WeakHashMap

class ThemeManager(
    private val settingsRepo: SqliteSettingsRepository,
    private val toggleButton: Button,
) {
    private var darkMode: Boolean = settingsRepo.getBoolean("darkMode", false)

    /**
     * WeakHashMap: sobald eine Scene nicht mehr referenziert wird (Fenster zu),
     * fliegt sie automatisch raus -> keine Leaks.
     */
    private val scenes: MutableMap<Scene, Unit> = WeakHashMap()

    private val globalStylesheetUrl: String =
        ThemeManager::class.java.getResource("/styles.css")!!.toExternalForm()

    fun bindToSceneRoot() {
        updateToggleText()

        // Sobald der Toggle-Button in einer Scene hängt, registrieren wir die Main-Scene
        toggleButton.sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                register(scene)
            }
        }
    }

    /**
     * Für normale Stages/Fenster: Scene registrieren + sofort Theme anwenden.
     */
    fun register(scene: Scene) {
        ensureStylesheet(scene)

        scenes[scene] = Unit
        apply(scene.root)

        // Falls root später gewechselt wird: Theme neu anwenden
        scene.rootProperty().addListener { _, _, newRoot ->
            if (newRoot != null) apply(newRoot)
        }
    }

    /**
     * Für Dialoge/Alerts: deren DialogPane hat eine eigene Scene, die oft erst kurz vor show() existiert.
     */
    fun register(dialog: Dialog<*>) {
        val pane = dialog.dialogPane

        // Wenn Scene schon existiert: direkt registrieren
        pane.scene?.let { register(it) }

        // Sonst: sobald sie gesetzt wird, registrieren
        pane.sceneProperty().addListener { _, _, newScene ->
            if (newScene != null) {
                register(newScene)
            }
        }
    }

    fun toggle() {
        darkMode = !darkMode
        settingsRepo.setBoolean("darkMode", darkMode)

        // Alle registrierten Scenes updaten
        scenes.keys.forEach { scene ->
            apply(scene.root)
        }

        updateToggleText()
    }

    private fun ensureStylesheet(scene: Scene) {
        val stylesheets = scene.stylesheets
        if (!stylesheets.contains(globalStylesheetUrl)) {
            stylesheets.add(globalStylesheetUrl)
        }
    }

    private fun updateToggleText() {
        toggleButton.text = if (darkMode) "Light Mode" else "Dark Mode"
    }

    private fun apply(root: Parent) {
        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
        } else {
            root.styleClass.remove("dark")
        }
    }
}
