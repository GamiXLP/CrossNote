package crossnote.desktop.sync

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

data class FoundServer(
    val host: String,     // IP des Servers
    val httpPort: Int,    // euer Sync HTTP Port (z.B. 8085)
    val name: String
)

class UdpDiscoveryClient {

    /**
     * Broadcastet im LAN und wartet kurz auf Antworten.
     * timeoutMs: Gesamtzeit, die wir lauschen (z.B. 600-1200ms)
     */
    fun discover(timeoutMs: Int = 900): List<FoundServer> {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 200

            val reqBytes = DiscoveryProtocol.MAGIC_REQUEST.toByteArray(StandardCharsets.UTF_8)
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val reqPacket = DatagramPacket(reqBytes, reqBytes.size, broadcastAddr, DiscoveryProtocol.UDP_PORT)

            // 2x senden erhöht Erfolgsquote in manchen WLANs
            socket.send(reqPacket)
            socket.send(reqPacket)

            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(512)

            val found = linkedMapOf<String, FoundServer>() // dedupe by host
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)

                    val msg = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                    val decoded = DiscoveryProtocol.decodeResponse(msg) ?: continue

                    val host = packet.address.hostAddress
                    found.putIfAbsent(host, FoundServer(host = host, httpPort = decoded.httpPort, name = decoded.name))
                } catch (_: java.net.SocketTimeoutException) {
                    // keep looping until deadline
                }
            }

            return found.values.toList()
        }
    }
}
