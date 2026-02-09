package crossnote.infra.persistence

import crossnote.domain.settings.SettingsRepository
import java.sql.Connection

class SqliteSettingsRepository(private val db: SqliteDatabase) : SettingsRepository {

    private fun conn(): Connection = db.connection()

    override fun set(key: String, value: String) {
        val sql =
            """
            INSERT INTO settings(key, value)
            VALUES(?, ?)
            ON CONFLICT(key) DO UPDATE SET
                value = excluded.value;
            """.trimIndent()

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    override fun get(key: String): String? {
        val sql = "SELECT value FROM settings WHERE key = ?;"

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, key)

            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.getString("value")
            }
        }
    }

    override fun delete(key: String) {
        val sql = "DELETE FROM settings WHERE key = ?;"

        conn().prepareStatement(sql).use { ps ->
            ps.setString(1, key)
            ps.executeUpdate()
        }
    }
}
