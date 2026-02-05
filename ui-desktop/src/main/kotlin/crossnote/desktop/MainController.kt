package crossnote.desktop

import crossnote.app.note.NoteAppService
import crossnote.domain.note.NoteId
import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import crossnote.domain.revision.RevisionId
import crossnote.infra.persistence.*
import javafx.animation.*
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import javafx.scene.shape.SVGPath
import javafx.util.Duration
import javafx.scene.Group
import java.nio.file.Paths
import java.time.Duration as JDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainController {

    // ---------- Persistence / Service ----------
    private val db = SqliteDatabase(Paths.get(System.getProperty("user.home"), ".crossnote", "crossnote.db"))
    private val settingsRepo = SqliteSettingsRepository(db)
    private val service = NoteAppService(
        repo = SqliteNoteRepository(db),
        revisionRepo = SqliteRevisionRepository(db),
        ids = UuidIdGenerator(),
        clock = SystemClock()
    )

    // ---------- Left panes ----------
    @FXML lateinit var APnotebooks: AnchorPane
    @FXML lateinit var APtrashcan: AnchorPane
    @FXML lateinit var APsavestate: AnchorPane

    // ---------- Notebooks/Notes (links) ----------
    @FXML lateinit var TFnotebook: TextField
    @FXML lateinit var LVnotebook: ListView<Pair<String, String>> // (id, title)

    // ---------- Trash (links) ----------
    @FXML lateinit var TFtrashcan: TextField
    @FXML lateinit var LVtrashcan: ListView<Pair<String, String>> // (id, title)
    @FXML lateinit var BTNrestore: Button

    // ---------- Savestates (links) ----------
    @FXML lateinit var LBdataname: Label
    @FXML lateinit var LVsavestate: ListView<Pair<String, String>> // (revisionId, prettyText)
    @FXML lateinit var BTNload: Button

    // ---------- Bottom left buttons ----------
    @FXML lateinit var BTNtrashcan: Button
    @FXML lateinit var BTNsavestate: Button

    // ---------- Top ----------
    @FXML lateinit var BTNdarkmode: Button

    // ---------- Center ----------
    @FXML lateinit var titleField: TextField
    @FXML lateinit var contentArea: TextArea
    @FXML lateinit var BTNsave: Button
    @FXML lateinit var BTNdelete: Button
    @FXML lateinit var BTNnewnote: Button
    @FXML lateinit var LBlastchange: Label
    @FXML lateinit var LBsaved: Label

    // ---------- State ----------
    private val notebookItems = FXCollections.observableArrayList<Pair<String, String>>()
    private val trashItems = FXCollections.observableArrayList<Pair<String, String>>()
    private val savestateItems = FXCollections.observableArrayList<Pair<String, String>>() // (revId, prettyText)

    private lateinit var notebookFiltered: FilteredList<Pair<String, String>>
    private lateinit var trashFiltered: FilteredList<Pair<String, String>>

    private var selectedId: String? = null
    private var lastActiveNoteId: String? = null
    private var darkMode: Boolean = false

    // --- Darkmode Icons ---
    private lateinit var moonGraphic: Node
    private lateinit var sunGraphic: Node

    @FXML
    fun initialize() {
        service.purgeTrashedOlderThan(30)
        showLeftPane(APnotebooks)

        notebookFiltered = FilteredList(notebookItems) { true }
        trashFiltered = FilteredList(trashItems) { true }
        LVnotebook.items = notebookFiltered
        LVtrashcan.items = trashFiltered

        LVnotebook.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }
        LVtrashcan.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        LVsavestate.items = savestateItems
        LVsavestate.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        TFnotebook.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            notebookFiltered.setPredicate { it.second.lowercase().contains(q) }
        }
        TFtrashcan.textProperty().addListener { _, _, newValue ->
            val q = newValue.trim().lowercase()
            trashFiltered.setPredicate { it.second.lowercase().contains(q) }
        }

        LVnotebook.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) openNote(new.first, inTrash = false)
        }
        LVtrashcan.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) openNote(new.first, inTrash = true)
        }

        BTNtrashcan.setOnAction {
            if (APtrashcan.isVisible) {
                showLeftPane(APnotebooks)
                clearEditorAndSelections()
                refreshNotebookList()
            } else {
                showLeftPane(APtrashcan)
                clearEditorAndSelections()
                refreshTrashList()
            }
            setCenterMode()
        }

        BTNsavestate.setOnAction {
            if (APsavestate.isVisible) {
                showLeftPane(APnotebooks)
                clearEditorAndSelections()
                refreshNotebookList()
            } else {
                showLeftPane(APsavestate)
                clearEditorAndSelections()
                refreshSavestates()
            }
            setCenterMode()
        }

        BTNrestore.setOnAction { onRestore() }
        BTNsave.setOnAction { onSave() }
        BTNnewnote.setOnAction { onNew() }
        BTNdelete.setOnAction { onDelete() }

        LVtrashcan.contextMenu = ContextMenu().apply {
            val purgeItem = MenuItem("Endgültig löschen").apply {
                setOnAction { onPurgeSelectedTrash() }
            }
            items.add(purgeItem)
        }

        LVsavestate.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) {
                LBdataname.text = new.second
                val rev = service.getRevision(RevisionId(new.first))
                titleField.text = rev.title
                contentArea.text = rev.content
                LBsaved.text = "Vorschau (Revision)"
                LBlastchange.text = rev.createdAt.toString()
            }
        }

        BTNload.setOnAction { onLoadSavestateRevision() }

        // ---- Build Darkmode Icons + Hover Animations (sobald Scene existiert) ----
        BTNdarkmode.sceneProperty().addListener { _, _, scene ->
            if (scene != null) {
                moonGraphic = buildMoonGraphic()
                sunGraphic = buildSunGraphic()

                darkMode = settingsRepo.getBoolean("darkMode", false)
                applyTheme(withIconTransition = false)

                // Hover Animation für alle Icon-Buttons
                listOf(
                    BTNdarkmode, BTNsave, BTNdelete, BTNnewnote,
                    BTNtrashcan, BTNsavestate, BTNrestore, BTNload
                ).forEach { installHoverAnim(it) }
            }
        }

        LBsaved.text = "Nicht gespeichert"
        LBlastchange.text = "--"
        LBdataname.text = "Keine Notiz ausgewählt"

        refreshNotebookList()
        setCenterMode()
    }

    @FXML
    fun darkmode_on(@Suppress("UNUSED_PARAMETER") event: ActionEvent) {
        darkMode = !darkMode
        applyTheme(withIconTransition = true)
        settingsRepo.setBoolean("darkMode", darkMode)
    }

    private fun applyTheme(withIconTransition: Boolean) {
        val root = BTNdarkmode.scene.root

        if (darkMode) {
            if (!root.styleClass.contains("dark")) root.styleClass.add("dark")
        } else {
            root.styleClass.remove("dark")
        }

        // BTNdarkmode Icon entsprechend setzen
        val newGraphic = if (darkMode) sunGraphic else moonGraphic
        if (withIconTransition) {
            animateIconSwap(BTNdarkmode, newGraphic)
        } else {
            BTNdarkmode.graphic = newGraphic
        }

        // Alle SVGs im UI einfärben (Light=Schwarz, Dark=Weiß)
        val iconColor = if (darkMode) Color.WHITE else Color.BLACK
        recolorAllButtonGraphics(iconColor)
    }

    private fun recolorAllButtonGraphics(color: Color) {
        val buttons = listOf(
            BTNdarkmode, BTNsave, BTNdelete, BTNnewnote,
            BTNtrashcan, BTNsavestate, BTNrestore, BTNload
        )
        buttons.forEach { btn ->
            btn.graphic?.let { recolorNodeRecursive(it, color) }
        }
    }

    private fun recolorNodeRecursive(node: Node, color: Color) {
        when (node) {
            is SVGPath -> {
                // Fill: wenn transparent -> transparent lassen, sonst einfärben
                val f = node.fill
                if (f != null && f != Color.TRANSPARENT) node.fill = color

                // Stroke: wenn vorhanden -> einfärben
                if (node.stroke != null) node.stroke = color
            }
            is Group -> node.children.forEach { recolorNodeRecursive(it, color) }
            else -> {
                // falls du später andere Shapes nutzt
            }
        }
    }

    private fun animateIconSwap(button: Button, newGraphic: Node) {
        val old = button.graphic
        if (old == null) {
            button.graphic = newGraphic
            return
        }

        // OUT (old)
        val fadeOut = FadeTransition(Duration.millis(120.0), old).apply { toValue = 0.0 }
        val scaleOut = ScaleTransition(Duration.millis(120.0), old).apply {
            toX = 0.85; toY = 0.85
        }
        val out = ParallelTransition(fadeOut, scaleOut)

        out.setOnFinished {
            button.graphic = newGraphic
            newGraphic.opacity = 0.0
            newGraphic.scaleX = 0.85
            newGraphic.scaleY = 0.85
            newGraphic.rotate = -25.0

            // IN (new)
            val fadeIn = FadeTransition(Duration.millis(160.0), newGraphic).apply { toValue = 1.0 }
            val scaleIn = ScaleTransition(Duration.millis(160.0), newGraphic).apply {
                toX = 1.0; toY = 1.0
            }
            val rotate = RotateTransition(Duration.millis(220.0), newGraphic).apply {
                toAngle = 0.0
                interpolator = Interpolator.EASE_OUT
            }
            ParallelTransition(fadeIn, scaleIn, rotate).play()
        }

        out.play()
    }

    private fun installHoverAnim(button: Button) {
        button.isFocusTraversable = false

        val inAnim = ScaleTransition(Duration.millis(120.0), button).apply {
            toX = 1.12; toY = 1.12
            interpolator = Interpolator.EASE_OUT
        }
        val outAnim = ScaleTransition(Duration.millis(120.0), button).apply {
            toX = 1.0; toY = 1.0
            interpolator = Interpolator.EASE_OUT
        }

        val fadeIn = FadeTransition(Duration.millis(120.0), button).apply { toValue = 1.0 }
        val fadeOut = FadeTransition(Duration.millis(120.0), button).apply { toValue = 0.88 }

        button.opacity = 0.92

        button.setOnMouseEntered {
            outAnim.stop(); fadeOut.stop()
            ParallelTransition(inAnim, fadeIn).play()
        }
        button.setOnMouseExited {
            inAnim.stop(); fadeIn.stop()
            ParallelTransition(outAnim, fadeOut).play()
        }
    }

    // --- Deine gewünschten SVGs als JavaFX-Graphic (Group aus SVGPath) ---

    private fun buildMoonGraphic(): Node {
        // Aus deinem Mond-SVG: 3 Pfade (1x Moon-Outline, 2x Stars)
        val moonOutline = SVGPath().apply {
            content = "M7 6c0 6.08 4.92 11 11 11c0.53 0 1.05 -0.04 1.56 -0.11c-1.61 2.47 -4.39 4.11 -7.56 4.11c-4.97 0 -9 -4.03 -9 -9c0 -3.17 1.64 -5.95 4.11 -7.56c-0.07 0.51 -0.11 1.03 -0.11 1.56Z"
            fill = Color.TRANSPARENT
            stroke = Color.BLACK
            strokeWidth = 2.0
        }

        val star1 = SVGPath().apply {
            content = "M15.22 6.03l2.53 -1.94l-3.19 -0.09l-1.06 -3l-1.06 3l-3.19 0.09l2.53 1.94l-0.91 3.06l2.63 -1.81l2.63 1.81l-0.91 -3.06Z"
            fill = Color.BLACK
        }

        val star2 = SVGPath().apply {
            content = "M19.61 12.25l1.64 -1.25l-2.06 -0.05l-0.69 -1.95l-0.69 1.95l-2.06 0.05l1.64 1.25l-0.59 1.98l1.7 -1.17l1.7 1.17l-0.59 -1.98Z"
            fill = Color.BLACK
        }

        return Group(moonOutline, star1, star2).apply {
            // angenehme Größe
            scaleX = 0.95
            scaleY = 0.95
        }
    }

    private fun buildSunGraphic(): Node {
        // Aus deinem Sonnen-SVG: der relevante gelbe Pfad (wir färben später schwarz/weiß)
        val sun = SVGPath().apply {
            content =
                "M12 19a1 1 0 0 1 1 1v1a1 1 0 1 1-2 0v-1a1 1 0 0 1 1-1m6.364-2.05l.707.707a1 1 0 0 1-1.414 1.414l-.707-.707a1 1 0 0 1 1.414-1.414m-12.728 0a1 1 0 0 1 1.497 1.32l-.083.094l-.707.707a1 1 0 0 1-1.497-1.32l.083-.094zM12 6a6 6 0 1 1 0 12a6 6 0 0 1 0-12m0 2a4 4 0 1 0 0 8a4 4 0 0 0 0-8m-8 3a1 1 0 0 1 .117 1.993L4 13H3a1 1 0 0 1-.117-1.993L3 11zm17 0a1 1 0 1 1 0 2h-1a1 1 0 1 1 0-2zM4.929 4.929a1 1 0 0 1 1.32-.083l.094.083l.707.707a1 1 0 0 1-1.32 1.497l-.094-.083l-.707-.707a1 1 0 0 1 0-1.414m14.142 0a1 1 0 0 1 0 1.414l-.707.707a1 1 0 1 1-1.414-1.414l.707-.707a1 1 0 0 1 1.414 0M12 2a1 1 0 0 1 1 1v1a1 1 0 1 1-2 0V3a1 1 0 0 1 1-1"
            fill = Color.BLACK
        }

        return Group(sun).apply {
            scaleX = 0.95
            scaleY = 0.95
        }
    }

    // ---------- Rest deiner Logik unverändert ----------

    private fun showLeftPane(which: AnchorPane) {
        val panes = listOf(APnotebooks, APtrashcan, APsavestate)
        panes.forEach {
            it.isVisible = false
            it.isManaged = false
        }
        which.isVisible = true
        which.isManaged = true
    }

    private fun setCenterMode() {
        val inTrash = APtrashcan.isVisible
        val inSavestate = APsavestate.isVisible
        val editorLocked = inTrash || inSavestate

        BTNsave.isDisable = editorLocked
        BTNnewnote.isDisable = editorLocked
        BTNrestore.isDisable = !inTrash
        BTNload.isDisable = !inSavestate
        titleField.isEditable = !editorLocked
        contentArea.isEditable = !editorLocked

        BTNdelete.text = "" // Icon-only (Text optional komplett leer)
    }

    private fun clearEditorAndSelections() {
        selectedId = null
        titleField.text = ""
        contentArea.text = ""
        LBlastchange.text = "--"
        LBsaved.text = "Nicht gespeichert"

        LVnotebook.selectionModel.clearSelection()
        LVtrashcan.selectionModel.clearSelection()
        LVsavestate.selectionModel.clearSelection()
    }

    private fun refreshNotebookList() {
        val summaries = service.listActiveNotes()
        notebookItems.setAll(summaries.map { it.id to it.title })
    }

    private fun refreshTrashList() {
        val summaries = service.listTrashedNotes()
        trashItems.setAll(summaries.map { it.id to it.title })
    }

    private fun refreshSavestates() {
        val noteId = lastActiveNoteId
        if (noteId == null) {
            savestateItems.clear()
            LBdataname.text = "Keine Notiz ausgewählt"
            return
        }

        val revisions = service.listRevisions(NoteId(noteId))
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        val pretty = revisions.mapIndexed { index, r ->
            val instant = runCatching { Instant.parse(r.createdAtIso) }.getOrNull()
            val dateText = if (instant != null) formatter.format(instant) else r.createdAtIso
            val version = index + 1
            r.id to "$dateText – Version $version"
        }

        savestateItems.setAll(pretty)

        LBdataname.text =
            if (revisions.isEmpty()) "Keine Speicherstände vorhanden"
            else "Revisionen für Notiz: $noteId"
    }

    private fun openNote(noteId: String, inTrash: Boolean) {
        selectedId = noteId
        if (!inTrash) lastActiveNoteId = noteId

        val note = service.getNote(NoteId(noteId))
        titleField.text = note.title
        contentArea.text = note.content
        LBlastchange.text = note.updatedAt.toString()

        LBsaved.text = if (inTrash) trashCountdownText(noteId) else "Nicht gespeichert"
    }

    private fun onNew() {
        if (!APnotebooks.isVisible) return
        clearEditorAndSelections()
        titleField.requestFocus()
    }

    private fun onSave() {
        if (!APnotebooks.isVisible) return

        val title = titleField.text ?: ""
        val content = contentArea.text ?: ""

        val id = selectedId
        if (id == null) {
            val newId = service.createNote(title, content)
            selectedId = newId.value
            lastActiveNoteId = newId.value
            LBsaved.text = "Gespeichert (neu)"
        } else {
            service.updateNote(NoteId(id), title, content)
            lastActiveNoteId = id
            LBsaved.text = "Gespeichert (Revision erstellt)"
        }

        val refreshed = service.getNote(NoteId(selectedId!!))
        LBlastchange.text = refreshed.updatedAt.toString()

        refreshNotebookList()
        selectInList(LVnotebook, selectedId!!)
    }

    private fun onDelete() {
        when {
            APsavestate.isVisible -> deleteSelectedSavestate()
            APtrashcan.isVisible -> purgeCurrentTrashNote()
            APnotebooks.isVisible -> moveCurrentNoteToTrash()
        }
    }

    private fun moveCurrentNoteToTrash() {
        val id = selectedId ?: return
        service.moveToTrash(NoteId(id))
        clearEditorAndSelections()
        refreshNotebookList()
    }

    private fun purgeCurrentTrashNote() {
        val id = selectedId ?: return

        val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Endgültig löschen"
            headerText = "Notiz endgültig löschen?"
            contentText = "Diese Notiz wird dauerhaft entfernt und kann nicht wiederhergestellt werden."
            buttonTypes.setAll(ButtonType.CANCEL, ButtonType.OK)
        }
        val result = confirm.showAndWait()
        if (result.isPresent && result.get() == ButtonType.OK) {
            service.purgeNotePermanently(NoteId(id))
            clearEditorAndSelections()
            refreshTrashList()
        }
    }

    private fun onRestore() {
        if (!APtrashcan.isVisible) return
        val id = selectedId ?: return
        service.restore(NoteId(id))

        clearEditorAndSelections()
        refreshTrashList()
        refreshNotebookList()
    }

    private fun onPurgeSelectedTrash() {
        if (!APtrashcan.isVisible) return
        val selected = LVtrashcan.selectionModel.selectedItem ?: return
        selectedId = selected.first
        purgeCurrentTrashNote()
    }

    private fun deleteSelectedSavestate() {
        if (!APsavestate.isVisible) return
        val selected = LVsavestate.selectionModel.selectedItem ?: run {
            LBsaved.text = "Kein Speicherstand ausgewählt"
            return
        }

        val revId = RevisionId(selected.first)

        val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Speicherstand löschen"
            headerText = "Diesen Speicherstand wirklich löschen?"
            contentText = "Der Speicherstand wird dauerhaft entfernt."
            buttonTypes.setAll(ButtonType.CANCEL, ButtonType.OK)
        }
        val result = confirm.showAndWait()
        if (result.isEmpty || result.get() != ButtonType.OK) return

        service.deleteRevision(revId)

        LVsavestate.selectionModel.clearSelection()
        refreshSavestates()

        titleField.text = ""
        contentArea.text = ""
        LBlastchange.text = "--"
        LBsaved.text = "Speicherstand gelöscht"
    }

    private fun onLoadSavestateRevision() {
        if (!APsavestate.isVisible) return

        val noteId = lastActiveNoteId ?: run {
            LBdataname.text = "Keine Notiz ausgewählt"
            return
        }

        val selectedRev = LVsavestate.selectionModel.selectedItem ?: return
        val revisionId = RevisionId(selectedRev.first)

        service.restoreFromRevision(NoteId(noteId), revisionId)

        showLeftPane(APnotebooks)
        clearEditorAndSelections()
        refreshNotebookList()
        setCenterMode()

        openNote(noteId, inTrash = false)
        selectInList(LVnotebook, noteId)

        LBsaved.text = "Auf Revision zurückgesetzt"
    }

    private fun trashCountdownText(noteId: String): String {
        val note = service.getNote(NoteId(noteId))
        val trashedAt = note.trashedAt ?: return "Papierkorb"
        val now = service.clockNowForUi()

        val elapsedDays = JDuration.between(trashedAt, now).toDays()
        val remaining = 30 - elapsedDays

        return when {
            remaining <= 0L -> "Papierkorb: wird heute gelöscht"
            remaining == 1L -> "Papierkorb: 1 Tag übrig"
            else -> "Papierkorb: $remaining Tage übrig"
        }
    }

    private fun selectInList(list: ListView<Pair<String, String>>, id: String) {
        val idx = list.items.indexOfFirst { it.first == id }
        if (idx >= 0) list.selectionModel.select(idx)
    }

    fun close() {
        db.close()
    }
}