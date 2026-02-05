package crossnote.infra.persistence

import crossnote.domain.note.Notebook
import crossnote.domain.note.NotebookId
import crossnote.domain.note.NotebookRepository
import java.sql.Connection

class SqliteNotebookRepository(private val db: SqliteDatabase) : NotebookRepository {

    private fun conn(): Connection = db.connection()

    override fun save(notebook: Notebook) {
        val sql =
            """
            INSERT INTO notebooks(id, name, parent_id)
            VALUES(?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name;
                parent_id = excluded.parent_id;
            """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, notebook.id.value)
            ps.setString(2, notebook.name)
            ps.setString(3, notebook.parentId?.value)
            ps.executeUpdate()
        }
    }

    override fun findById(id: NotebookId): Notebook? {
        val sql = "SELECT id, name, parent_id FROM notebooks WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return Notebook(
                    id = NotebookId(rs.getString("id")),
                    name = rs.getString("name"),
                    parentId = rs.getString("parent_id")?.let { NotebookId(it) }
                )
            }
        }
    }

    override fun findAll(): List<Notebook> {
        val sql = "SELECT id, name, parent_id FROM notebooks ORDER BY name ASC;"
        conn().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs ->
                val result = mutableListOf<Notebook>()
                while (rs.next()) {
                    result.add(
                        Notebook(
                            id = NotebookId(rs.getString("id")),
                            name = rs.getString("name"),
                            parentId = rs.getString("parent_id")?.let { NotebookId(it) }
                        )
                    )
                }
                return result
            }
        }
    }

    override fun delete(id: NotebookId) {
        val sql = "DELETE FROM notebooks WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeUpdate()
        }
    }

    fun moveNotebook(id: NotebookId, newParentId: NotebookId?) {
        val sql = "UPDATE notebooks SET parent_id = ? WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, newParentId?.value)
            ps.setString(2, id.value)
            ps.executeUpdate()
        }
    }
}