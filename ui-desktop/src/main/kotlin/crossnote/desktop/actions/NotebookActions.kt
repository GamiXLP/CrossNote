package crossnote.desktop.actions

import crossnote.app.note.NoteAppService
import crossnote.desktop.util.NotebookTreeUtils
import crossnote.domain.note.NoteId
import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import crossnote.domain.note.TextConstraints
import crossnote.domain.note.ValidationException
import crossnote.domain.note.validateNotebookName
import crossnote.infra.persistence.SqliteNoteRepository
import crossnote.infra.persistence.SqliteNotebookRepository
import crossnote.desktop.util.DialogsExt
import crossnote.desktop.ThemeManager
import javafx.scene.control.TextInputDialog
import javafx.scene.control.TextFormatter
import java.time.Instant
import java.util.UUID
import javafx.geometry.Insets
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView

class NotebookActions(
    private val service: NoteAppService,
    private val notebookRepo: SqliteNotebookRepository,
    private val noteRepo: SqliteNoteRepository,
    private val themeManager: ThemeManager,
    private val onAfterChange: () -> Unit,
    private val onAfterTrashChange: () -> Unit,
    private val onClearSelection: () -> Unit,
    private val onSelectedNotebookChanged: (NotebookId?) -> Unit,
) {

    fun createNotebookDialog(parent: NotebookId?) {

        val dialog = TextInputDialog().apply {
            title = "Neuer Ordner"
            headerText = if (parent == null) "Ordner anlegen" else "Unterordner anlegen"
            contentText = "Name:"
        }

        // ✅ wichtig: Darkmode auf Dialog anwenden
        themeManager.register(dialog)

        // ✅ JavaFX-Standard-"?" entfernen
        dialog.graphic = null
        dialog.dialogPane.graphic = null
        dialog.dialogPane.stylesheets.add(
            NotebookActions::class.java.getResource("/styles.css")!!.toExternalForm()
        )
        dialog.dialogPane.styleClass.add("cn-dialog")

// ✅ Custom Header im CrossNote-Style
        val icon = ImageView(
            Image(NotebookActions::class.java.getResourceAsStream("/images/CrossNote_Icon.png"))
        ).apply {
            fitWidth = 18.0
            fitHeight = 18.0
            isPreserveRatio = true
            styleClass.add("cn-dialog-appicon")
        }

        val title = Label(if (parent == null) "Ordner anlegen" else "Unterordner anlegen").apply {
            styleClass.add("cn-dialog-title")
        }

        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }

        val header = HBox(10.0, icon, title, spacer).apply {
            styleClass.add("cn-dialog-header")
        }

        dialog.headerText = null
        dialog.dialogPane.header = header

        // --- Zeichenlimit ---
        dialog.editor.textFormatter = TextFormatter<String> { change: TextFormatter.Change ->
            val newText = change.controlNewText
            if (newText.length <= TextConstraints.NOTEBOOK_NAME_MAX) change else null
        }

        // --- Counter-Label ---
        val counterLabel = javafx.scene.control.Label()
        counterLabel.opacity = 0.7

        fun updateCounter(text: String?) {
            val len = (text ?: "").length
            val remaining = TextConstraints.NOTEBOOK_NAME_MAX - len
            counterLabel.text =
                if (remaining <= 0) "Limit erreicht"
                else "Noch $remaining Zeichen"
        }

        updateCounter(dialog.editor.text)

        dialog.editor.textProperty().addListener { _, _, newValue ->
            updateCounter(newValue)
        }

        // --- Layout (clean & app-like) ---
        val nameLabel = Label("Name:").apply {
            styleClass.add("cn-dialog-field-label")
        }

        val nameField = dialog.editor.apply {
            prefWidth = 320.0
        }

        val row = HBox(10.0, nameLabel, nameField).apply {
            alignment = javafx.geometry.Pos.CENTER_LEFT
        }

        HBox.setHgrow(nameField, Priority.ALWAYS)

        counterLabel.apply {
            opacity = 0.7
            padding = Insets(0.0, 0.0, 4.0, 0.0)
            styleClass.add("cn-dialog-counter")
        }

        val wrapper = VBox(10.0).apply {
            padding = Insets(14.0, 18.0, 10.0, 18.0)
        }

        wrapper.children.setAll(row, counterLabel)
        dialog.dialogPane.content = wrapper

        dialog.dialogPane.buttonTypes.forEach { bt ->
            (dialog.dialogPane.lookupButton(bt) as? javafx.scene.control.Button)?.apply {
                minWidth = 90.0
                padding = Insets(8.0, 14.0, 8.0, 14.0)
            }
        }

        dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT)

        dialog.setOnShown {
            val stage = dialog.dialogPane.scene.window as? javafx.stage.Stage
            stage?.scene?.fill = javafx.scene.paint.Color.TRANSPARENT
        }

        // --- Dialog anzeigen ---
        val result = dialog.showAndWait()
        if (result.isEmpty) return

        val raw = result.get()

        val name = try {
            validateNotebookName(raw)
        } catch (e: ValidationException) {
            DialogsExt.warn(e.message ?: "Ungültiger Ordnername")
            return
        }

        val id = NotebookId(UUID.randomUUID().toString())
        notebookRepo.save(Notebook(id, name, parentId = parent))

        onAfterChange()
    }

    fun trashNotebookRecursively(notebookId: NotebookId) {
        val idsToTrash = NotebookTreeUtils.collectSubtreeIds(notebookRepo, notebookId)
        val now = Instant.now()

        // notes -> trash
        idsToTrash.forEach { nbId ->
            noteRepo.listNoteSummariesInNotebook(nbId).forEach { n ->
                service.moveToTrash(NoteId(n.id))
            }
        }

        // folders -> trash
        idsToTrash.forEach { nbId ->
            notebookRepo.moveToTrash(nbId, now)
        }

        onSelectedNotebookChanged(null)
        onClearSelection()

        onAfterChange()
        onAfterTrashChange()
    }
}