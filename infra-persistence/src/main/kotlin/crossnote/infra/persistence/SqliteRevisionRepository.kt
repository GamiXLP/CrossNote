package crossnote.infra.persistence

import crossnote.domain.note.NoteId
import crossnote.domain.revision.Revision
import crossnote.domain.revision.RevisionId
import crossnote.domain.revision.RevisionRepository
import java.sql.Connection
import java.time.Instant

class SqliteRevisionRepository(private val db: SqliteDatabase) : RevisionRepository {

    private fun conn(): Connection = db.connection()

    override fun save(revision: Revision) {
        val sql =
            """
            INSERT INTO revisions(id, note_id, title, content, created_at)
            VALUES(?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                note_id = excluded.note_id,
                title = excluded.title,
                content = excluded.content,
                created_at = excluded.created_at;
            """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, revision.id.value)
            ps.setString(2, revision.noteId.value)
            ps.setString(3, revision.title)
            ps.setString(4, revision.content)
            ps.setString(5, revision.createdAt.toString())
            ps.executeUpdate()
        }
    }

    override fun findById(id: RevisionId): Revision? {
        val sql = "SELECT id, note_id, title, content, created_at FROM revisions WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return mapRevision(rs)
            }
        }
    }

    override fun findByNoteId(noteId: NoteId): List<Revision> {
        val sql = "SELECT id, note_id, title, content, created_at FROM revisions WHERE note_id = ? ORDER BY created_at DESC;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, noteId.value)
            ps.executeQuery().use { rs ->
                val result = mutableListOf<Revision>()
                while (rs.next()) {
                    result.add(mapRevision(rs))
                }
                return result
            }
        }
    }

    private fun mapRevision(rs: java.sql.ResultSet): Revision {
        val id = RevisionId(rs.getString("id"))
        val noteId = NoteId(rs.getString("note_id"))
        val title = rs.getString("title")
        val content = rs.getString("content")
        val createdAt = Instant.parse(rs.getString("created_at"))

        return Revision(
            id = id,
            noteId = noteId,
            title = title,
            content = content,
            createdAt = createdAt
        )
    }

    override fun deleteById(id: RevisionId) {
        val sql = "DELETE FROM revisions WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            val rows = ps.executeUpdate()
            println("deleteById(${id.value}) -> rows=$rows")
        }
    }

    override fun deleteByNoteId(noteId: NoteId) {
        val sql = "DELETE FROM revisions WHERE note_id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, noteId.value)
            ps.executeUpdate()
        }
    }
}
