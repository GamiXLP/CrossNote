package crossnote.infra.persistence

import crossnote.domain.note.Note
import crossnote.domain.note.NoteId
import crossnote.domain.note.NoteRepository
import java.sql.Connection
import java.time.Instant

class SqliteNoteRepository(private val db: SqliteDatabase) : NoteRepository {

    private fun conn(): Connection = db.connection()

    override fun save(note: Note) {
        val sql =
            """
            INSERT INTO notes(id, title, content, created_at, updated_at, trashed_at)
            VALUES(?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                content = excluded.content,
                created_at = excluded.created_at,
                updated_at = excluded.updated_at,
                trashed_at = excluded.trashed_at;
            """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, note.id.value)
            ps.setString(2, note.title)
            ps.setString(3, note.content)
            ps.setString(4, note.createdAt.toString())
            ps.setString(5, note.updatedAt.toString())
            ps.setString(6, note.trashedAt?.toString())
            ps.executeUpdate()
        }
    }

    override fun findById(id: NoteId): Note? {
        val sql = "SELECT id, title, content, created_at, updated_at, trashed_at FROM notes WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return mapNote(rs)
            }
        }
    }

    override fun findAll(): List<Note> {
        val sql = "SELECT id, title, content, created_at, updated_at, trashed_at FROM notes;"
        conn().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs ->
                val result = mutableListOf<Note>()
                while (rs.next()) {
                    result.add(mapNote(rs))
                }
                return result
            }
        }
    }

    private fun mapNote(rs: java.sql.ResultSet): Note {
        val id = NoteId(rs.getString("id"))
        val title = rs.getString("title")
        val content = rs.getString("content")
        val createdAt = Instant.parse(rs.getString("created_at"))
        val updatedAt = Instant.parse(rs.getString("updated_at"))
        val trashedAtStr = rs.getString("trashed_at")
        val trashedAt = trashedAtStr?.let { Instant.parse(it) }

        return Note(
            id = id,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            trashedAt = trashedAt
        )
    }
}
