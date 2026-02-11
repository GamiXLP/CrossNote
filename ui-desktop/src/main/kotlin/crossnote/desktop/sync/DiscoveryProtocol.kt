package crossnote.desktop.sync

object DiscoveryProtocol {
    const val UDP_PORT: Int = 8086

    const val MAGIC_REQUEST = "CROSSNOTE_DISCOVER_V1"
    const val MAGIC_RESPONSE = "CROSSNOTE_HERE_V1"

    // Response format (single line):
    // CROSSNOTE_HERE_V1|<httpPort>|<name>
    fun encodeResponse(httpPort: Int, name: String): String =
        "$MAGIC_RESPONSE|$httpPort|$name"

    data class DiscoveryResponse(val httpPort: Int, val name: String)

    fun decodeResponse(msg: String): DiscoveryResponse? {
        val parts = msg.trim().split("|")
        if (parts.size < 3) return null
        if (parts[0] != MAGIC_RESPONSE) return null
        val port = parts[1].toIntOrNull() ?: return null
        val name = parts.drop(2).joinToString("|").ifBlank { "CrossNote Server" }
        return DiscoveryResponse(port, name)
    }
}
