package crossnote.desktop

import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import crossnote.infra.persistence.SqliteSettingsRepository
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ContextMenu
import javafx.scene.control.Dialog
import java.util.WeakHashMap

class ThemeManager(
    private val settingsRepo: SqliteSettingsRepository,
    private val toggleButton: Button,
) {
    private var darkMode: Boolean = settingsRepo.getBoolean("darkMode", false)

    private val scenes: MutableMap<Scene, Unit> = WeakHashMap()
    private val contextMenus: MutableMap<ContextMenu, Unit> = WeakHashMap()

    private val globalStylesheetUrl: String =
        ThemeManager::class.java.getResource("/styles.css")!!.toExternalForm()

    fun bindToSceneRoot() {
        updateToggleText()

        toggleButton.sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                register(scene)
            }
        }
    }

    fun register(scene: Scene) {
        ensureStylesheet(scene)
        scenes[scene] = Unit
        applyToRoot(scene.root)

        scene.rootProperty().addListener { _, _, newRoot ->
            if (newRoot != null) applyToRoot(newRoot)
        }
    }

    fun register(dialog: Dialog<*>) {
        val pane = dialog.dialogPane
        pane.scene?.let { register(it) }

        pane.sceneProperty().addListener { _, _, newScene ->
            if (newScene != null) register(newScene)
        }
    }

    /**
     * ✅ ContextMenu: apply Theme auf den *echten* ".context-menu" Node im Popup,
     * nicht blind auf scene.root.
     */
    fun register(menu: ContextMenu) {
        contextMenus[menu] = Unit

        fun applyMenuThemeNow() {
            applyThemeToContextMenu(menu)
        }

        // wenn Scene schon existiert
        applyMenuThemeNow()

        // beim Öffnen: hier existiert die Popup-Scene garantiert
        menu.setOnShowing {
            applyMenuThemeNow()
        }
    }

    fun toggle() {
        darkMode = !darkMode
        settingsRepo.setBoolean("darkMode", darkMode)

        // normale Scenes
        scenes.keys.forEach { scene ->
            applyToRoot(scene.root)
        }

        // ContextMenus (wenn offen oder schon initialisiert)
        contextMenus.keys.forEach { menu ->
            applyThemeToContextMenu(menu)
        }

        updateToggleText()
    }

    // ----------------------------
    // Internals
    // ----------------------------

    private fun ensureStylesheet(scene: Scene) {
        val stylesheets = scene.stylesheets
        if (!stylesheets.contains(globalStylesheetUrl)) {
            stylesheets.add(globalStylesheetUrl)
        }
    }

    private fun updateToggleText() {
        toggleButton.text = if (darkMode) "Light Mode" else "Dark Mode"
    }

    private fun applyToRoot(root: Parent) {
        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
        } else {
            root.styleClass.remove("dark")
        }
    }

    /**
     * ✅ Wichtig: ContextMenu-Popup hat eigene Scene + eigenes Skin-Root.
     * Wir suchen den Node ".context-menu" und togglen dort "dark".
     */
    private fun applyThemeToContextMenu(menu: ContextMenu) {
        val scene = menu.scene ?: return
        ensureStylesheet(scene)

        val popupRoot = scene.root as? Parent ?: return

        // der Node, auf den dein CSS ".context-menu.dark" zielt:
        val cmNode = popupRoot.lookup(".context-menu") as? Parent
        val target = cmNode ?: popupRoot

        applyToRoot(target)
    }
}
