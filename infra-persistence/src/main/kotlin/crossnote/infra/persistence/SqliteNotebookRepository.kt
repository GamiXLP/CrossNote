package crossnote.infra.persistence

import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import crossnote.domain.note.NotebookRepository
import java.sql.Connection
import java.sql.Types
import java.time.Instant

class SqliteNotebookRepository(private val db: SqliteDatabase) : NotebookRepository {

    private fun conn(): Connection = db.connection()

    override fun save(notebook: Notebook) {
        val sql =
            """
            INSERT INTO notebooks(id, name, parent_id, updated_at, trashed_at)
            VALUES(?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                parent_id = excluded.parent_id,
                updated_at = excluded.updated_at,
                trashed_at = excluded.trashed_at;
            """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, notebook.id.value)
            ps.setString(2, notebook.name)

            val parent = notebook.parentId
            if (parent != null) ps.setString(3, parent.value) else ps.setNull(3, Types.VARCHAR)

            ps.setString(4, notebook.updatedAt.toString())

            val trashed = notebook.trashedAt
            if (trashed != null) ps.setString(5, trashed.toString()) else ps.setNull(5, Types.VARCHAR)

            ps.executeUpdate()
        }
    }

    override fun findById(id: NotebookId): Notebook? {
        val sql = "SELECT id, name, parent_id, updated_at, trashed_at FROM notebooks WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return mapNotebook(rs)
            }
        }
    }

    /**
     * ✅ Nur aktive Ordner (nicht im Papierkorb)
     */
    override fun findAll(): List<Notebook> {
        val sql =
            """
            SELECT id, name, parent_id, updated_at, trashed_at
            FROM notebooks
            WHERE trashed_at IS NULL
            ORDER BY name ASC;
            """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs ->
                val result = mutableListOf<Notebook>()
                while (rs.next()) result.add(mapNotebook(rs))
                return result
            }
        }
    }

    /**
     * ✅ Alle Ordner (inkl. Papierkorb) – benötigt für rekursives Trashen
     */
    override fun findAllIncludingTrashed(): List<Notebook> {
        val sql =
            """
            SELECT id, name, parent_id, updated_at, trashed_at
            FROM notebooks
            ORDER BY name ASC;
            """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs ->
                val result = mutableListOf<Notebook>()
                while (rs.next()) result.add(mapNotebook(rs))
                return result
            }
        }
    }

    /**
     * ✅ Verschiebt Ordner in Papierkorb (setzt trashed_at)
     */
    fun moveToTrash(id: NotebookId, now: Instant) {
        val sql = "UPDATE notebooks SET trashed_at = ?, updated_at = ? WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, now.toString())
            ps.setString(2, now.toString())
            ps.setString(3, id.value)
            ps.executeUpdate()
        }
    }

    fun restoreFromTrash(id: NotebookId, now: Instant) {
        val sql = "UPDATE notebooks SET trashed_at = NULL, updated_at = ? WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, now.toString())
            ps.setString(2, id.value)
            ps.executeUpdate()
        }
    }

    fun isTrashed(id: NotebookId): Boolean {
        val sql = "SELECT trashed_at FROM notebooks WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return false
                return rs.getString("trashed_at") != null
            }
        }
    }

    fun findParentId(id: NotebookId): NotebookId? {
        val sql = "SELECT parent_id FROM notebooks WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.getString("parent_id")?.let { NotebookId(it) }
            }
        }
    }

    /**
     * ✅ Drag & Drop: Ordner verschieben
     */
    fun moveNotebook(id: NotebookId, newParent: NotebookId?, now: Instant) {
        val sql = "UPDATE notebooks SET parent_id = ?, updated_at = ? WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            if (newParent != null) ps.setString(1, newParent.value) else ps.setNull(1, Types.VARCHAR)
            ps.setString(2, now.toString())
            ps.setString(3, id.value)
            ps.executeUpdate()
        }
    }

    override fun delete(id: NotebookId) {
        val sql = "DELETE FROM notebooks WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeUpdate()
        }
    }

    private fun mapNotebook(rs: java.sql.ResultSet): Notebook {
        val parentStr = rs.getString("parent_id")
        val updatedAtStr = rs.getString("updated_at")
        val trashedStr = rs.getString("trashed_at")

        return Notebook(
            id = NotebookId(rs.getString("id")),
            name = rs.getString("name"),
            parentId = parentStr?.let { NotebookId(it) },
            updatedAt = if (updatedAtStr.isNullOrBlank()) Instant.EPOCH else Instant.parse(updatedAtStr),
            trashedAt = trashedStr?.let { Instant.parse(it) }
        )
    }

    fun findAllTrashed(): List<Notebook> {
        val sql = """
            SELECT id, name, parent_id, updated_at, trashed_at
            FROM notebooks
            WHERE trashed_at IS NOT NULL;
        """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs ->
                val result = mutableListOf<Notebook>()
                while (rs.next()) {
                    result.add(mapNotebook(rs))
                }
                return result
            }
        }
    }
}
