package crossnote.infra.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class SqliteDatabase(private val dbPath: Path) : AutoCloseable {

    private val connection: Connection

    init {
        Files.createDirectories(dbPath.parent)
        connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
        connection.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON;")
        }
        migrate()
    }

    fun connection(): Connection = connection

    private fun migrate() {
        connection.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    trashed_at TEXT NULL
                );
                """.trimIndent()
            )

            st.execute(
                """
                CREATE TABLE IF NOT EXISTS revisions (
                    id TEXT PRIMARY KEY,
                    note_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE
                );
                """.trimIndent()
            )

            st.execute(
                """
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                """.trimIndent()
            )

            st.execute(
                """
                CREATE TABLE IF NOT EXISTS notebooks (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                );
                """.trimIndent()
            )

            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_updated_at ON notes(updated_at);")
            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_trashed_at ON notes(trashed_at);")
            st.execute("CREATE INDEX IF NOT EXISTS idx_revisions_note_id_created_at ON revisions(note_id, created_at);")
            st.execute("ALTER TABLE notes ADD COLUMN notebook_id TEXT NULL;")
            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_notebook_id ON notes(notebook_id);")

        }
    }

    override fun close() {
        connection.close()
    }
}
