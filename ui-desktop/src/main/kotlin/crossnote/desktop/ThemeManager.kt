package crossnote.desktop

import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import crossnote.infra.persistence.SqliteSettingsRepository
import javafx.event.EventHandler
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
            if (scene != null) register(scene)
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
     * ContextMenu ist ein Popup mit eigener Scene + eigenem Root.
     * WICHTIG: Dark-Klasse muss auf menu.scene.root, sonst bleibt der Hintergrund Modena-weiß.
     */
    fun register(menu: ContextMenu) {
        contextMenus[menu] = Unit

        val previous = menu.onShowing
        menu.onShowing = EventHandler { ev ->
            previous?.handle(ev)

            // Popup-Scene existiert erst beim Anzeigen
            val popupScene = menu.scene ?: return@EventHandler
            ensureStylesheet(popupScene)

            applyToContextMenuPopup(menu)
        }
    }

    private fun applyToContextMenuPopup(menu: ContextMenu) {
        // 1) Menü selbst (zur Sicherheit)
        if (darkMode) {
            if (!menu.styleClass.contains("dark")) menu.styleClass.add("dark")
        } else {
            menu.styleClass.remove("dark")
        }

        // 2) Popup Root
        val root = menu.scene?.root as? Parent ?: return
        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
        } else {
            root.styleClass.remove("dark")
        }

        // 3) **WICHTIG**: alle Nodes, die JavaFX als ".context-menu" rendert, ebenfalls togglen
        // (damit egal welcher Container den Background trägt)
        val contextMenuNodes = root.lookupAll(".context-menu")
        for (n in contextMenuNodes) {
            val p = n as? Parent ?: continue
            if (darkMode) {
                if (!p.styleClass.contains("dark")) p.styleClass.add("dark")
            } else {
                p.styleClass.remove("dark")
            }
        }
    }

    fun toggle() {
        darkMode = !darkMode
        settingsRepo.setBoolean("darkMode", darkMode)

        scenes.keys.forEach { scene ->
            applyToRoot(scene.root)
        }

        // ✅ alle bekannten ContextMenus ebenfalls aktualisieren
        contextMenus.keys.forEach { menu ->
            menu.scene?.let { ensureStylesheet(it) }
            applyToPopupRoot(menu)
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

    private fun applyToPopupRoot(menu: ContextMenu) {
        val popupRoot = menu.scene?.root as? Parent ?: return

        if (darkMode) {
            if (!popupRoot.styleClass.contains("dark")) popupRoot.styleClass.add("dark")
        } else {
            popupRoot.styleClass.remove("dark")
        }
    }
}
