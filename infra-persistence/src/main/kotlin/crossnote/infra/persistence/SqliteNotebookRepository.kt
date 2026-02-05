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
            INSERT INTO notebooks(id, name, parent_id, trashed_at)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                parent_id = excluded.parent_id,
                trashed_at = excluded.trashed_at;
            """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, notebook.id.value)
            ps.setString(2, notebook.name)

            val parent = notebook.parentId
            if (parent != null) ps.setString(3, parent.value) else ps.setNull(3, Types.VARCHAR)

            val trashed = notebook.trashedAt
            if (trashed != null) ps.setString(4, trashed.toString()) else ps.setNull(4, Types.VARCHAR)

            ps.executeUpdate()
        }
    }

    override fun findById(id: NotebookId): Notebook? {
        val sql = "SELECT id, name, parent_id, trashed_at FROM notebooks WHERE id = ?;"
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
            SELECT id, name, parent_id, trashed_at
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
    fun findAllIncludingTrashed(): List<Notebook> {
        val sql =
            """
            SELECT id, name, parent_id, trashed_at
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
        val sql = "UPDATE notebooks SET trashed_at = ? WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, now.toString())
            ps.setString(2, id.value)
            ps.executeUpdate()
        }
    }

    fun restoreFromTrash(id: NotebookId) {
        val sql = "UPDATE notebooks SET trashed_at = NULL WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
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
    fun moveNotebook(id: NotebookId, newParent: NotebookId?) {
        val sql = "UPDATE notebooks SET parent_id = ? WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            if (newParent != null) ps.setString(1, newParent.value) else ps.setNull(1, Types.VARCHAR)
            ps.setString(2, id.value)
            ps.executeUpdate()
        }
    }

    override fun delete(id: NotebookId) {
        // ⚠️ Wird bei euch jetzt eigentlich nicht mehr genutzt (weil Papierkorb),
        // aber wir lassen es drin für "hard delete" falls ihr es irgendwann braucht.
        val sql = "DELETE FROM notebooks WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeUpdate()
        }
    }

    private fun mapNotebook(rs: java.sql.ResultSet): Notebook {
        val parentStr = rs.getString("parent_id")
        val trashedStr = rs.getString("trashed_at")

        return Notebook(
            id = NotebookId(rs.getString("id")),
            name = rs.getString("name"),
            parentId = parentStr?.let { NotebookId(it) },
            trashedAt = trashedStr?.let { Instant.parse(it) }
        )
    }

    fun findAllTrashed(): List<Notebook> {
        val sql = """
            SELECT id, name, parent_id, trashed_at
            FROM notebooks
            WHERE trashed_at IS NOT NULL;
        """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs ->
                val result = mutableListOf<Notebook>()
                while (rs.next()) {
                    result.add(
                        Notebook(
                            id = NotebookId(rs.getString("id")),
                            name = rs.getString("name"),
                            parentId = rs.getString("parent_id")?.let { NotebookId(it) },
                            trashedAt = rs.getString("trashed_at")?.let { Instant.parse(it) }
                        )
                    )
                }
                return result
            }
        }
    }
}