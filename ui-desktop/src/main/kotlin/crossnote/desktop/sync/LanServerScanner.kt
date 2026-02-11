package crossnote.desktop.sync

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class LanServerScanner {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(250))
        .build()

    /**
     * Scannt das lokale Subnetz (basierend auf der primären IPv4 + Prefix).
     * Findet Server anhand GET /ping == "ok".
     *
     * - port: HTTP-Port eures Sync-Servers (z.B. 8085)
     * - totalTimeoutMs: wie lange insgesamt gesucht wird (grob)
     * - perHostTimeoutMs: timeout pro Host-Request
     * - maxConcurrency: wie viele parallele Requests
     */
    fun scanForServers(
        port: Int,
        totalTimeoutMs: Int = 2500,
        perHostTimeoutMs: Int = 250,
        maxConcurrency: Int = 64
    ): List<FoundServer> {
        val subnet = getPrimarySubnetV4() ?: return emptyList()

        // Kandidaten auflisten (bei riesigen Netzen limitieren wir hart, sonst dauert’s ewig)
        val candidates = subnet.enumerateHosts(maxHosts = 512)

        val exec = Executors.newFixedThreadPool(minOf(maxConcurrency, 64))
        val sem = Semaphore(maxConcurrency)

        try {
            val deadline = System.currentTimeMillis() + totalTimeoutMs
            val futures = mutableListOf<CompletableFuture<FoundServer?>>()

            for (ip in candidates) {
                if (System.currentTimeMillis() > deadline) break

                sem.acquire()

                val f = CompletableFuture.supplyAsync({
                    try {
                        if (ping(ip.hostAddress, port, perHostTimeoutMs)) {
                            FoundServer(
                                host = ip.hostAddress,
                                httpPort = port,
                                name = "CrossNote Server"
                            )
                        } else {
                            null
                        }
                    } catch (_: Throwable) {
                        null
                    } finally {
                        sem.release()
                    }
                }, exec)

                futures.add(f)
            }

            // Ergebnisse einsammeln
            val found = LinkedHashMap<String, FoundServer>()
            for (f in futures) {
                val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
                val server = try {
                    f.get(Duration.ofMillis(remaining).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: Throwable) {
                    null
                }
                if (server != null) found.putIfAbsent(server.host, server)
            }

            return found.values.toList()
        } finally {
            exec.shutdownNow()
        }
    }

    private fun ping(host: String, port: Int, timeoutMs: Int): Boolean {
        val uri = URI("http://$host:$port/ping")
        val req = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(timeoutMs.toLong()))
            .GET()
            .build()

        val res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (res.statusCode() !in 200..299) return false
        return res.body().trim().equals("ok", ignoreCase = true)
    }

    /**
     * Versucht eine sinnvolle “primäre” IPv4 + Prefix zu finden.
     * Nimmt die erste aktive, nicht-loopback IPv4 mit Prefix.
     */
    private fun getPrimarySubnetV4(): SubnetV4? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (ni in interfaces) {
            try {
                if (!ni.isUp || ni.isLoopback) continue

                for (ia in ni.interfaceAddresses) {
                    val addr = ia.address ?: continue
                    if (addr !is Inet4Address) continue

                    val prefix = ia.networkPrefixLength.toInt()
                    // manche Adapter liefern komische Werte, clampen
                    val safePrefix = prefix.coerceIn(8, 30)

                    return SubnetV4(addr, safePrefix)
                }
            } catch (_: Throwable) {
                // ignore
            }
        }
        return null
    }

    private data class SubnetV4(val ip: Inet4Address, val prefix: Int) {
        fun enumerateHosts(maxHosts: Int): List<InetAddress> {
            val mask = prefixToMask(prefix)
            val ipInt = ipv4ToInt(ip)
            val network = ipInt and mask
            val broadcast = network or mask.inv()

            val start = network + 1
            val end = broadcast - 1
            if (end <= start) return emptyList()

            val total = (end - start + 1)
            val limit = minOf(total, maxHosts)

            val result = ArrayList<InetAddress>(limit)
            var cur = start
            repeat(limit) {
                result.add(intToIpv4(cur))
                cur++
            }
            return result
        }

        private fun prefixToMask(prefix: Int): Int {
            // z.B. /24 => 0xFFFFFF00
            return if (prefix == 0) 0 else (-1 shl (32 - prefix))
        }

        private fun ipv4ToInt(addr: Inet4Address): Int {
            val b = addr.address
            return (b[0].toInt() and 0xFF shl 24) or
                    (b[1].toInt() and 0xFF shl 16) or
                    (b[2].toInt() and 0xFF shl 8) or
                    (b[3].toInt() and 0xFF)
        }

        private fun intToIpv4(v: Int): InetAddress {
            val b0 = (v ushr 24) and 0xFF
            val b1 = (v ushr 16) and 0xFF
            val b2 = (v ushr 8) and 0xFF
            val b3 = v and 0xFF
            val bytes = byteArrayOf(b0.toByte(), b1.toByte(), b2.toByte(), b3.toByte())
            return InetAddress.getByAddress(bytes)
        }
    }
}
