package crossnote.desktop.sync

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * UDP Discovery Server:
 * - Lauscht im LAN auf DISCOVERY_PORT
 * - Wenn ein Client "CROSSNOTE_DISCOVER_V1" broadcastet, antwortet der Server direkt zurück:
 *   "CROSSNOTE_HERE_V1|<name>|<httpPort>"
 */
class UdpDiscoveryServer(
    private val httpPort: () -> Int,
    private val serverName: () -> String = { DEFAULT_NAME }
) {
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null

    fun isRunning(): Boolean = socket != null

    fun start() {
        if (socket != null) return

        val s = DatagramSocket(DISCOVERY_PORT, InetAddress.getByName("0.0.0.0")).apply {
            broadcast = true
        }
        socket = s

        worker = thread(name = "UdpDiscoveryServer", isDaemon = true) {
            val buf = ByteArray(1024)
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    s.receive(packet)

                    val msg = packet.data
                        .copyOfRange(0, packet.length)
                        .toString(StandardCharsets.UTF_8)
                        .trim()

                    if (msg == DISCOVER_MSG) {
                        val name = serverName().ifBlank { DEFAULT_NAME }
                        val port = httpPort()

                        val response = "$HERE_MSG|$name|$port"
                        val out = response.toByteArray(StandardCharsets.UTF_8)

                        val reply = DatagramPacket(
                            out,
                            out.size,
                            packet.address,   // direkt an Sender zurück
                            packet.port
                        )
                        s.send(reply)
                    }
                } catch (e: SocketException) {
                    // Socket wurde geschlossen -> normal beim stop()
                    break
                } catch (_: Throwable) {
                    // ignorieren, Discovery soll robust sein
                }
            }
        }
    }

    fun stop() {
        try {
            socket?.close()
        } catch (_: Throwable) {
        } finally {
            socket = null
        }

        try {
            worker?.interrupt()
        } catch (_: Throwable) {
        } finally {
            worker = null
        }
    }

    companion object {
        const val DISCOVERY_PORT: Int = 45877

        const val DISCOVER_MSG: String = "CROSSNOTE_DISCOVER_V1"
        const val HERE_MSG: String = "CROSSNOTE_HERE_V1"

        const val DEFAULT_NAME: String = "CrossNote"
    }
}
