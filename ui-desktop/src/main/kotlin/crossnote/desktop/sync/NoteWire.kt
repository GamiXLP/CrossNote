package crossnote.desktop.sync

import crossnote.domain.note.Note
import crossnote.domain.note.NoteId
import crossnote.domain.note.NotebookId
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/**
 * Einfaches Wire-Format ohne JSON:
 * Eine Note = eine Zeile, Felder getrennt durch TAB:
 *
 * id \t notebookIdOrEmpty \t titleBase64 \t contentBase64 \t createdAtIso \t updatedAtIso \t trashedAtIsoOrEmpty
 */
object NoteWire {
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun encodeLine(note: Note): String {
        val nb = note.notebookId?.value ?: ""
        val title = b64.encodeToString(note.title.toByteArray(StandardCharsets.UTF_8))
        val content = b64.encodeToString(note.content.toByteArray(StandardCharsets.UTF_8))
        val trashed = note.trashedAt?.toString() ?: ""

        return listOf(
            note.id.value,
            nb,
            title,
            content,
            note.createdAt.toString(),
            note.updatedAt.toString(),
            trashed
        ).joinToString("\t")
    }

    fun decodeLine(line: String): Note {
        val parts = line.split("\t")
        require(parts.size >= 7) { "Invalid note line. Expected 7 fields, got ${parts.size}." }

        val id = NoteId(parts[0])
        val notebookId = parts[1].takeIf { it.isNotBlank() }?.let { NotebookId(it) }

        val title = String(b64d.decode(parts[2]), StandardCharsets.UTF_8)
        val content = String(b64d.decode(parts[3]), StandardCharsets.UTF_8)

        val createdAt = Instant.parse(parts[4])
        val updatedAt = Instant.parse(parts[5])
        val trashedAt = parts[6].takeIf { it.isNotBlank() }?.let { Instant.parse(it) }

        return Note(
            id = id,
            notebookId = notebookId,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            trashedAt = trashedAt
        )
    }

    fun encodeLines(notes: List<Note>): String =
        notes.joinToString(separator = "\n") { encodeLine(it) }

    fun decodeLines(body: String): List<Note> =
        body.lineSequence()
            .filter { it.isNotBlank() }
            .map { decodeLine(it) }
            .toList()
}