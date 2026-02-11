package crossnote.desktop.presenter

import crossnote.app.note.NoteAppService
import crossnote.desktop.util.DialogsExt
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import crossnote.domain.note.TextConstraints
import crossnote.domain.note.ValidationException
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter

class NoteEditorPresenter(
    private val service: NoteAppService,
    private val titleField: TextField,
    private val contentArea: TextArea,
    private val lastChangeLabel: Label,
    private val savedLabel: Label,
    private val titleCountLabel: Label, // ✅ NEU: "noch xx Zeichen"
    private val trashCountdownText: (noteId: String) -> String,
    private val onAfterSaveOrDelete: () -> Unit,
) {
    var selectedNoteId: String? = null
        private set

    var selectedNotebookId: NotebookId? = null

    init {
        titleField.textFormatter = TextFormatter<String> { change: TextFormatter.Change ->
            val newText = change.controlNewText
            if (newText.length <= TextConstraints.NOTE_TITLE_MAX) change else null
        }

        titleField.promptText = "Titel (max. ${TextConstraints.NOTE_TITLE_MAX} Zeichen)"

        fun updateCounter(text: String?) {
            val len = (text ?: "").length
            val remaining = TextConstraints.NOTE_TITLE_MAX - len
            titleCountLabel.text = if (remaining <= 0) "Limit erreicht" else "Noch $remaining Zeichen"
        }

        // initial
        updateCounter(titleField.text)

        // live update
        titleField.textProperty().addListener { _, _, newValue ->
            updateCounter(newValue)
        }
    }

    fun resetEditor() {
        selectedNoteId = null
        titleField.text = ""
        contentArea.text = ""
        lastChangeLabel.text = "--"
        savedLabel.text = "Nicht gespeichert"
        titleField.requestFocus()
        // Counter updatet automatisch über Listener
    }

    fun startNewNote(targetNotebookId: NotebookId?) {
        selectedNotebookId = targetNotebookId
        resetEditor()
    }

    fun openNote(noteId: String, inTrash: Boolean) {
        selectedNoteId = noteId

        val note = service.getNote(NoteId(noteId))
        selectedNotebookId = note.notebookId

        titleField.text = note.title
        contentArea.text = note.content
        lastChangeLabel.text = note.updatedAt.toString()
        savedLabel.text = if (inTrash) trashCountdownText(noteId) else "Nicht gespeichert"
        // Counter updatet automatisch über Listener
    }

    fun saveIfEditable(isEditableContext: Boolean) {
        if (!isEditableContext) return

        val title = titleField.text ?: ""
        val content = contentArea.text ?: ""

        val id = selectedNoteId
        if (id == null) {
            try {
                val newId = service.createNote(title, content, notebookId = selectedNotebookId)
                selectedNoteId = newId.value
                savedLabel.text = "Gespeichert (neu)"
            } catch (e: ValidationException) {
                DialogsExt.warn(e.message ?: "Ungültige Eingabe")
                return
            }
        } else {
            try {
                service.updateNote(NoteId(id), title, content)
                savedLabel.text = "Gespeichert (Revision erstellt)"
            } catch (e: ValidationException) {
                DialogsExt.warn(e.message ?: "Ungültige Eingabe")
                return
            }
        }

        val refreshed = service.getNote(NoteId(selectedNoteId!!))
        lastChangeLabel.text = refreshed.updatedAt.toString()

        onAfterSaveOrDelete()
    }

    fun deleteNote(noteId: NoteId) {
        service.moveToTrash(noteId)
        resetEditor()
        onAfterSaveOrDelete()
    }
}
