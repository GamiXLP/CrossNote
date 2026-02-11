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

        dialog.editor.textFormatter = TextFormatter<String> { change ->
            val newText = change.controlNewText
            if (newText.length <= TextConstraints.NOTEBOOK_NAME_MAX) change else null
        }
        dialog.editor.promptText = "Max. ${TextConstraints.NOTEBOOK_NAME_MAX} Zeichen"


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