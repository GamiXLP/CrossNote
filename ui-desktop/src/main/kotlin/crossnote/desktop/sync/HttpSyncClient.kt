package crossnote.desktop.sync

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant

class HttpSyncClient {
    private val client = HttpClient.newHttpClient()

    fun pullNotes(host: String, port: Int, after: Instant?): String {
        val afterParam =
            after?.toString()?.let { URLEncoder.encode(it, "UTF-8") } ?: ""

        val uri =
            if (afterParam.isBlank()) {
                URI("http://$host:$port/notes")
            } else {
                URI("http://$host:$port/notes?after=$afterParam")
            }

        val req = HttpRequest.newBuilder(uri)
            .GET()
            .build()

        val res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (res.statusCode() !in 200..299) {
            error("Pull failed: HTTP ${res.statusCode()} - ${res.body()}")
        }
        return res.body()
    }

    fun pushNotes(host: String, port: Int, body: String): String {
        val uri = URI("http://$host:$port/notes/push")

        val req = HttpRequest.newBuilder(uri)
            .header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (res.statusCode() !in 200..299) {
            error("Push failed: HTTP ${res.statusCode()} - ${res.body()}")
        }
        return res.body()
    }
}