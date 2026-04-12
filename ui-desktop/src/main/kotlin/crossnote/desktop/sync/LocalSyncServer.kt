package crossnote.desktop.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import crossnote.app.note.NoteAppService
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class LocalSyncServer(
    private val noteAppService: NoteAppService,
    private val onDataChanged: (() -> Unit)? = null
) {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    // simple debounce: max 1 signal per 300ms
    private val lastSignalMs = AtomicLong(0L)

    fun start(port: Int) {
        if (server != null) return

        val s = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
        println("SyncServer listening on 0.0.0.0:$port")

        s.createContext("/ping") { ex ->
            respondText(ex, 200, "ok")
        }

        // GET /notebooks
        s.createContext("/notebooks") { ex ->
            try {
                if (ex.requestMethod.uppercase() != "GET") {
                    respondText(ex, 405, "Method Not Allowed")
                    return@createContext
                }
                respondText(ex, 200, noteAppService.getAllNotebooksAsWire())
            } catch (t: Throwable) {
                respondText(ex, 500, "Server error: ${t.message}")
            }
        }

        // POST /notebooks/push
        s.createContext("/notebooks/push") { ex ->
            try {
                if (ex.requestMethod.uppercase() != "POST") {
                    respondText(ex, 405, "Method Not Allowed")
                    return@createContext
                }

                val body = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val applied = noteAppService.mergeNotebooksFromWire(body)

                if (applied > 0) signalDataChanged()

                respondText(ex, 200, "applied=$applied")
            } catch (t: Throwable) {
                respondText(ex, 500, "Server error: ${t.message}")
            }
        }

        // GET /notes?after=ISO_INSTANT
        s.createContext("/notes") { ex ->
            try {
                if (ex.requestMethod.uppercase() != "GET") {
                    respondText(ex, 405, "Method Not Allowed")
                    return@createContext
                }

                // NoteAppService getAllNotesAsWire currently returns all. 
                // Filtering by 'after' could be added to NoteAppService if needed.
                respondText(ex, 200, noteAppService.getAllNotesAsWire())
            } catch (t: Throwable) {
                respondText(ex, 500, "Server error: ${t.message}")
            }
        }

        // POST /notes/push
        s.createContext("/notes/push") { ex ->
            try {
                if (ex.requestMethod.uppercase() != "POST") {
                    respondText(ex, 405, "Method Not Allowed")
                    return@createContext
                }

                val body = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val applied = noteAppService.mergeNotesFromWire(body)

                if (applied > 0) signalDataChanged()

                respondText(ex, 200, "applied=$applied")
            } catch (t: Throwable) {
                respondText(ex, 500, "Server error: ${t.message}")
            }
        }

        val exec = Executors.newFixedThreadPool(4)
        s.executor = exec

        s.start()
        server = s
        executor = exec
    }

    fun stop() {
        server?.stop(0)
        server = null

        executor?.shutdownNow()
        executor = null
    }

    fun isRunning(): Boolean = server != null

    private fun signalDataChanged() {
        val now = System.currentTimeMillis()
        val last = lastSignalMs.get()
        if (now - last < 300) return
        if (lastSignalMs.compareAndSet(last, now)) {
            try {
                onDataChanged?.invoke()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun respondText(ex: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { os: OutputStream -> os.write(bytes) }
    }
}
