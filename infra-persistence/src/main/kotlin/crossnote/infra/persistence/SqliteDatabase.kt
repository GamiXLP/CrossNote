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
                st.execute("PRAGMA foreign_keys = ON;")

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

                if (!columnExists("notebooks", "parent_id")) {
                    st.execute("ALTER TABLE notebooks ADD COLUMN parent_id TEXT NULL;")
                }

                if (!columnExists("notebooks", "trashed_at")) {
                    st.execute("ALTER TABLE notebooks ADD COLUMN trashed_at TEXT NULL;")
                }
                st.execute("CREATE INDEX IF NOT EXISTS idx_notebooks_trashed_at ON notebooks(trashed_at);")

                st.execute("CREATE INDEX IF NOT EXISTS idx_notes_updated_at ON notes(updated_at);")
                st.execute("CREATE INDEX IF NOT EXISTS idx_notes_trashed_at ON notes(trashed_at);")
                st.execute("CREATE INDEX IF NOT EXISTS idx_revisions_note_id_created_at ON revisions(note_id, created_at);")

                // ✅ add column only if missing
                if (!columnExists("notes", "notebook_id")) {
                    st.execute("ALTER TABLE notes ADD COLUMN notebook_id TEXT NULL;")
                }

                st.execute("CREATE INDEX IF NOT EXISTS idx_notebooks_parent_id ON notebooks(parent_id);")
                st.execute("CREATE INDEX IF NOT EXISTS idx_notes_notebook_id ON notes(notebook_id);")
                

            }
        }

        private fun columnExists(table: String, column: String): Boolean {
            connection.prepareStatement("PRAGMA table_info($table);").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        if (rs.getString("name") == column) return true
                    }
                    return false
                }
            }
        }

        override fun close() {
            connection.close()
        }
    }
