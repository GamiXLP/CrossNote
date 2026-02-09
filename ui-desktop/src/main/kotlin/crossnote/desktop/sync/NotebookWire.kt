package crossnote.desktop.sync

import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

object NotebookWire {
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun encodeLine(nb: Notebook): String {
        val name = b64.encodeToString(nb.name.toByteArray(StandardCharsets.UTF_8))
        val parent = nb.parentId?.value ?: ""
        val trashed = nb.trashedAt?.toString() ?: ""
        return "${nb.id.value}\t$name\t$parent\t$trashed"
    }

    fun decodeLine(line: String): Notebook {
        val parts = line.split("\t")
        require(parts.size >= 4) { "Invalid notebook line. Expected 4 fields, got ${parts.size}." }

        val id = NotebookId(parts[0])
        val name = String(b64d.decode(parts[1]), StandardCharsets.UTF_8)
        val parentId = parts[2].ifBlank { null }?.let { NotebookId(it) }
        val trashedAt = parts[3].ifBlank { null }?.let { Instant.parse(it) }

        return Notebook(
            id = id,
            name = name,
            parentId = parentId,
            trashedAt = trashedAt
        )
    }

    fun encodeLines(nbs: List<Notebook>): String =
        nbs.joinToString("\n") { encodeLine(it) }

    fun decodeLines(body: String): List<Notebook> =
        body.lineSequence()
            .filter { it.isNotBlank() }
            .map { decodeLine(it) }
            .toList()
}