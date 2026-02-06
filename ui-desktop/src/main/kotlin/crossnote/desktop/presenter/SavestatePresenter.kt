package crossnote.desktop.presenter

import crossnote.app.note.NoteAppService
import crossnote.domain.note.NoteId
import crossnote.domain.revision.RevisionId
import javafx.collections.FXCollections
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SavestatePresenter(
    private val service: NoteAppService,
    private val listView: ListView<Pair<String, String>>,
    private val dataNameLabel: Label,
    private val titleFieldSetter: (String) -> Unit,
    private val contentAreaSetter: (String) -> Unit,
    private val savedLabelSetter: (String) -> Unit,
    private val lastChangeLabelSetter: (String) -> Unit,
    private val loadButton: Button,
    private val onLoadedRevision: (noteId: String) -> Unit,
) {
    private val items = FXCollections.observableArrayList<Pair<String, String>>()
    private var lastActiveNoteId: String? = null

    fun init() {
        listView.items = items
        listView.setCellFactory {
            object : ListCell<Pair<String, String>>() {
                override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.second
                }
            }
        }

        listView.selectionModel.selectedItemProperty().addListener { _, _, new ->
            if (new != null) {
                dataNameLabel.text = new.second
                val rev = service.getRevision(RevisionId(new.first))
                titleFieldSetter(rev.title)
                contentAreaSetter(rev.content)
                savedLabelSetter("Vorschau (Revision)")
                lastChangeLabelSetter(rev.createdAt.toString())
            }
        }

        loadButton.setOnAction { loadSelectedRevision() }
    }

    fun showFor(noteId: NoteId) {
        lastActiveNoteId = noteId.value
        refresh()
    }

    fun clear() {
        lastActiveNoteId = null
        items.clear()
        dataNameLabel.text = "Keine Notiz ausgewählt"
    }

    fun refresh() {
        val noteId = lastActiveNoteId ?: run {
            clear()
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

        items.setAll(pretty)

        dataNameLabel.text =
            if (revisions.isEmpty()) "Keine Speicherstände vorhanden"
            else "Revisionen für Notiz: $noteId"
    }

    private fun loadSelectedRevision() {
        val noteId = lastActiveNoteId ?: run {
            dataNameLabel.text = "Keine Notiz ausgewählt"
            return
        }

        val selected = listView.selectionModel.selectedItem ?: return
        val revisionId = RevisionId(selected.first)

        service.restoreFromRevision(NoteId(noteId), revisionId)
        onLoadedRevision(noteId)
    }
}
