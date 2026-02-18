package crossnote.desktop.actions

import crossnote.app.note.NoteAppService
import crossnote.desktop.I18n
import crossnote.desktop.ThemeManager
import crossnote.desktop.util.DialogsExt
import crossnote.desktop.util.NotebookTreeUtils
import crossnote.domain.note.NoteId
import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import crossnote.domain.note.TextConstraints
import crossnote.domain.note.ValidationException
import crossnote.domain.note.validateNotebookName
import crossnote.infra.persistence.SqliteNoteRepository
import crossnote.infra.persistence.SqliteNotebookRepository
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.TextFormatter
import javafx.scene.control.TextInputDialog
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.time.Instant
import java.util.UUID

class NotebookActions(
    private val i18n: I18n,
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
        val dialog = buildNotebookNameDialog(
            windowTitle = if (parent == null) i18n.t("dialog.folder.create.title") else i18n.t("dialog.subfolder.create.title"),
            headerTitle = if (parent == null) i18n.t("dialog.folder.create.header") else i18n.t("dialog.subfolder.create.header"),
            initialText = ""
        )

        val result = dialog.showAndWait()
        if (result.isEmpty) return

        val raw = result.get()

        val name = try {
            validateNotebookName(raw)
        } catch (e: ValidationException) {
            DialogsExt.warn(e.message ?: i18n.t("common.invalidFolderName"))
            return
        }

        val id = NotebookId(UUID.randomUUID().toString())
        notebookRepo.save(Notebook(id, name, parentId = parent))

        onAfterChange()
    }

    fun renameNotebookDialog(notebookId: NotebookId, currentName: String) {
        val dialog = buildNotebookNameDialog(
            windowTitle = i18n.t("dialog.folder.rename.title"),
            headerTitle = i18n.t("dialog.folder.rename.header"),
            initialText = currentName
        )

        val result = dialog.showAndWait()
        if (result.isEmpty) return

        val raw = result.get()

        val newName = try {
            validateNotebookName(raw)
        } catch (e: ValidationException) {
            DialogsExt.warn(e.message ?: i18n.t("common.invalidFolderName"))
            return
        }

        if (newName == currentName) return

        val existing = try {
            notebookRepo.findById(notebookId)
        } catch (_: Throwable) {
            null
        } ?: notebookRepo.findAll().firstOrNull { it.id == notebookId }

        if (existing == null) {
            DialogsExt.warn(i18n.t("common.folderNotFound"))
            return
        }

        notebookRepo.save(existing.copy(name = newName))
        onAfterChange()
    }

    fun trashNotebookRecursively(notebookId: NotebookId) {
        val idsToTrash = NotebookTreeUtils.collectSubtreeIds(notebookRepo, notebookId)
        val now = Instant.now()

        idsToTrash.forEach { nbId ->
            noteRepo.listNoteSummariesInNotebook(nbId).forEach { n ->
                service.moveToTrash(NoteId(n.id))
            }
        }

        idsToTrash.forEach { nbId ->
            notebookRepo.moveToTrash(nbId, now)
        }

        onSelectedNotebookChanged(null)
        onClearSelection()

        onAfterChange()
        onAfterTrashChange()
    }

    // =========================================================
    // Shared Dialog Builder (Create + Rename)
    // =========================================================
    private fun buildNotebookNameDialog(
        windowTitle: String,
        headerTitle: String,
        initialText: String
    ): TextInputDialog {

        val dialog = TextInputDialog(initialText).apply {
            title = windowTitle
            headerText = null
            contentText = i18n.t("dialog.common.nameLabel")
        }

        themeManager.register(dialog)

        dialog.graphic = null
        dialog.dialogPane.graphic = null

        dialog.dialogPane.stylesheets.add(
            NotebookActions::class.java.getResource("/styles.css")!!.toExternalForm()
        )
        dialog.dialogPane.styleClass.add("cn-dialog")

        val icon = ImageView(
            Image(NotebookActions::class.java.getResourceAsStream("/images/CrossNote_Icon.png"))
        ).apply {
            fitWidth = 18.0
            fitHeight = 18.0
            isPreserveRatio = true
            styleClass.add("cn-dialog-appicon")
        }

        val title = Label(headerTitle).apply {
            styleClass.add("cn-dialog-title")
        }

        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }

        val header = HBox(10.0, icon, title, spacer).apply {
            styleClass.add("cn-dialog-header")
            alignment = Pos.CENTER_LEFT
        }

        dialog.dialogPane.header = header

        dialog.editor.textFormatter = TextFormatter<String> { change: TextFormatter.Change ->
            val newText = change.controlNewText
            if (newText.length <= TextConstraints.NOTEBOOK_NAME_MAX) change else null
        }

        val counterLabel = Label().apply {
            opacity = 0.7
            styleClass.add("cn-dialog-counter")
        }

        fun updateCounter(text: String?) {
            val len = (text ?: "").length
            val remaining = TextConstraints.NOTEBOOK_NAME_MAX - len
            counterLabel.text =
                if (remaining <= 0) i18n.t("common.limitReached")
                else i18n.t("common.remainingChars", remaining)
        }

        updateCounter(dialog.editor.text)
        dialog.editor.textProperty().addListener { _, _, newValue -> updateCounter(newValue) }

        val nameLabel = Label(i18n.t("dialog.common.nameLabel")).apply {
            styleClass.add("cn-dialog-field-label")
        }

        val nameField = dialog.editor.apply { prefWidth = 320.0 }

        val row = HBox(10.0, nameLabel, nameField).apply {
            alignment = Pos.CENTER_LEFT
        }

        HBox.setHgrow(nameField, Priority.ALWAYS)

        counterLabel.padding = Insets(0.0, 0.0, 4.0, 0.0)

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
            dialog.editor.requestFocus()
            dialog.editor.selectAll()
        }

        return dialog
    }
}
