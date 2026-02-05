package crossnote.infra.persistence

import crossnote.domain.note.*
import java.sql.Connection

class SqliteNotebookRepository(private val db: SqliteDatabase) : NotebookRepository {

    private fun conn(): Connection = db.connection()

    override fun save(notebook: Notebook) {
        val sql = """
            INSERT INTO notebooks(id, name)
            VALUES(?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name;
        """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, notebook.id.value)
            ps.setString(2, notebook.name)
            ps.executeUpdate()
        }
    }

    override fun findAll(): List<Notebook> {
        val sql = "SELECT id, name FROM notebooks ORDER BY name;"
        conn().prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs ->
                val result = mutableListOf<Notebook>()
                while (rs.next()) {
                    result.add(
                        Notebook(
                            id = NotebookId(rs.getString("id")),
                            name = rs.getString("name")
                        )
                    )
                }
                return result
            }
        }
    }

    override fun findById(id: NotebookId): Notebook? {
        val sql = "SELECT id, name FROM notebooks WHERE id = ?;"
        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, id.value)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return Notebook(
                    id = NotebookId(rs.getString("id")),
                    name = rs.getString("name")
                )
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
}