package crossnote.desktop.presenter

import crossnote.app.note.NoteAppService
import crossnote.desktop.I18n
import crossnote.desktop.util.DialogsExt
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import crossnote.domain.note.TextConstraints
import crossnote.domain.note.ValidationException
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.text.Text

class NoteEditorPresenter(
    private val i18n: I18n,
    private val service: NoteAppService,
    private val titleField: TextField,
    private val contentArea: TextArea,
    private val lastChangeLabel: Label,
    private val savedLabel: Label,
    private val titleCountLabel: Label,
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

        // ✅ feste Breite: damit DE/EN nicht "springen"
        val maxTextDe = "Noch ${TextConstraints.NOTE_TITLE_MAX} Zeichen"
        val maxTextEn = "${TextConstraints.NOTE_TITLE_MAX} characters left"
        val measuredWidth = maxOf(
            Text(maxTextDe).apply { font = titleCountLabel.font }.layoutBounds.width,
            Text(maxTextEn).apply { font = titleCountLabel.font }.layoutBounds.width
        ) + 8.0

        titleCountLabel.minWidth = measuredWidth
        titleCountLabel.prefWidth = measuredWidth
        titleCountLabel.maxWidth = measuredWidth

        // live update
        titleField.textProperty().addListener { _, _, newValue ->
            updateCounter(newValue)
        }

        // ✅ beim Start einmal alles korrekt setzen
        applyI18nTexts()
        updateCounter(titleField.text)
    }

    /**
     * ✅ Wird vom MainController nach Language-Toggle aufgerufen
     * Setzt alle sichtbaren i18n-Texte im Editor sofort neu.
     */
    fun applyI18nTexts() {
        // Prompt
        titleField.promptText = i18n.t("editor.title.max", TextConstraints.NOTE_TITLE_MAX)

        // Counter neu setzen (z.B. "Noch 120 Zeichen" vs. "120 characters left")
        updateCounter(titleField.text)

        // Saved/LastChanged Labels konsistent setzen,
        // ABER: wenn gerade Papierkorb-Notiz offen ist, nicht überschreiben (Countdown)
        if (selectedNoteId == null) {
            savedLabel.text = i18n.t("editor.saved.notSaved")
            lastChangeLabel.text = i18n.t("editor.saved.noDate")
        }
    }

    private fun updateCounter(text: String?) {
        val len = (text ?: "").length
        val remaining = TextConstraints.NOTE_TITLE_MAX - len

        titleCountLabel.text =
            if (remaining <= 0) i18n.t("common.limitReached")
            else i18n.t("common.remainingChars", remaining)
    }

    fun resetEditor() {
        selectedNoteId = null
        titleField.text = ""
        contentArea.text = ""
        lastChangeLabel.text = i18n.t("editor.saved.noDate")
        savedLabel.text = i18n.t("editor.saved.notSaved")
        titleField.requestFocus()
        // Counter updated automatisch über Listener, aber wir setzen ihn sauber:
        updateCounter(titleField.text)
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

        savedLabel.text = if (inTrash) trashCountdownText(noteId) else i18n.t("editor.saved.notSaved")

        updateCounter(titleField.text)
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
                savedLabel.text = i18n.t("editor.saved.savedNew")
            } catch (e: ValidationException) {
                DialogsExt.warn(e.message ?: i18n.t("common.invalidInput"))
                return
            }
        } else {
            try {
                service.updateNote(NoteId(id), title, content)
                savedLabel.text = i18n.t("editor.saved.savedRevision")
            } catch (e: ValidationException) {
                DialogsExt.warn(e.message ?: i18n.t("common.invalidInput"))
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
