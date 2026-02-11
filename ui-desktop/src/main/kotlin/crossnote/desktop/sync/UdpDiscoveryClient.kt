package crossnote.desktop.sync

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets

data class FoundServer(
    val host: String,     // IP des Servers
    val httpPort: Int,    // euer Sync HTTP Port (z.B. 8085)
    val name: String
)

class UdpDiscoveryClient {

    /**
     * Broadcastet im LAN und wartet kurz auf Antworten.
     * timeoutMs: Gesamtzeit, die wir lauschen (z.B. 600-1500ms)
     */
    fun discover(timeoutMs: Int = 1200): List<FoundServer> {
        val reqBytes = DiscoveryProtocol.MAGIC_REQUEST.toByteArray(StandardCharsets.UTF_8)

        // Broadcast-Ziele ermitteln: interface-spezifische Broadcasts sind viel zuverlässiger als 255.255.255.255
        val targets = collectBroadcastTargets().toMutableSet()
        // Fallback trotzdem versuchen
        targets.add(InetAddress.getByName("255.255.255.255"))

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.reuseAddress = true
            socket.soTimeout = 200

            // Senden: pro Target 2x erhöht Erfolgsquote
            for (addr in targets) {
                try {
                    val reqPacket = DatagramPacket(reqBytes, reqBytes.size, addr, DiscoveryProtocol.UDP_PORT)
                    socket.send(reqPacket)
                    socket.send(reqPacket)
                } catch (_: Throwable) {
                    // ignorieren, manche Targets sind nicht sendbar
                }
            }

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
                    found.putIfAbsent(
                        host,
                        FoundServer(host = host, httpPort = decoded.httpPort, name = decoded.name)
                    )
                } catch (_: java.net.SocketTimeoutException) {
                    // keep looping until deadline
                }
            }

            return found.values.toList()
        }
    }

    private fun collectBroadcastTargets(): Set<InetAddress> {
        val result = mutableSetOf<InetAddress>()

        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptySet()
        for (ni in interfaces) {
            try {
                if (!ni.isUp || ni.isLoopback) continue
                // manche virtuelle Adapter sind "up", aber nutzlos – ist ok, wir filtern über broadcast != null
                for (ia in ni.interfaceAddresses) {
                    val bcast = ia.broadcast ?: continue
                    // Nur IPv4 ist hier sinnvoll
                    if (bcast.address.size == 4) {
                        result.add(bcast)
                    }
                }
            } catch (_: Throwable) {
                // ignore
            }
        }

        return result
    }
}
