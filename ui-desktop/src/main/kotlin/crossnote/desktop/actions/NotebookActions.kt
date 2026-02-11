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
import javafx.scene.control.TextInputDialog
import javafx.scene.control.TextFormatter
import java.time.Instant
import java.util.UUID

class NotebookActions(
    private val service: NoteAppService,
    private val notebookRepo: SqliteNotebookRepository,
    private val noteRepo: SqliteNoteRepository,
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

        // --- Layout erweitern ---
        val content = dialog.dialogPane.content
        val vbox = javafx.scene.layout.VBox(5.0)
        vbox.children.addAll(content, counterLabel)
        dialog.dialogPane.content = vbox

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