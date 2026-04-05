package crossnote.infra.persistence

import java.nio.file.Files
import java.sql.ResultSet
import java.sql.Statement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqliteDatabaseTest {

    @Test
    fun `database creates file and connection`() {
        val dir = Files.createTempDirectory("crossnote-db-test")
        val dbPath = dir.resolve("test.db")

        SqliteDatabase(dbPath).use { db ->
            val connection = db.connection()

            assertTrue(Files.exists(dbPath))
            assertNotNull(connection)
            assertTrue(!connection.isClosed)
        }
    }

    @Test
    fun `database can be opened twice on same file`() {
        val dir = Files.createTempDirectory("crossnote-db-reopen")
        val dbPath = dir.resolve("test.db")

        SqliteDatabase(dbPath).use { db ->
            assertTrue(!db.connection().isClosed)
        }

        SqliteDatabase(dbPath).use { db ->
            assertTrue(!db.connection().isClosed)
        }
    }

    @Test
    fun `database creates all required tables`() {
        val dir = Files.createTempDirectory("crossnote-db-schema")
        val dbPath = dir.resolve("test.db")

        SqliteDatabase(dbPath).use { db ->
            val conn = db.connection()
            val names = mutableListOf<String>()

            val statement: Statement = conn.createStatement()
            statement.use { st ->
                val resultSet: ResultSet = st.executeQuery(
                    """
                    SELECT name FROM sqlite_master
                    WHERE type = 'table'
                      AND name IN ('notes', 'revisions', 'settings', 'notebooks')
                    ORDER BY name;
                    """.trimIndent()
                )

                resultSet.use { rs ->
                    while (rs.next()) {
                        names.add(rs.getString("name"))
                    }
                }
            }

            assertEquals(
                listOf("notebooks", "notes", "revisions", "settings"),
                names
            )
        }
    }
}