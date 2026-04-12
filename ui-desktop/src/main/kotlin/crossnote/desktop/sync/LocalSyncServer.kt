package crossnote.desktop.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import crossnote.domain.note.Note
import crossnote.domain.note.NoteRepository
import crossnote.domain.note.Notebook
import crossnote.infra.persistence.SqliteNotebookRepository
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class LocalSyncServer(
    private val notebookRepo: SqliteNotebookRepository,
    private val noteRepo: NoteRepository,
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
                val notebooks = notebookRepo.findAllIncludingTrashed()
                respondText(ex, 200, NotebookWire.encodeLines(notebooks))
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
                val incoming = NotebookWire.decodeLines(body)

                var applied = 0
                for (remote in incoming) {
                    if (applyNotebook(remote)) applied++
                }

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

                val after = queryParam(ex.requestURI.rawQuery ?: "", "after")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Instant.parse(it) }

                val notes = noteRepo.findAll()
                val filtered =
                    if (after == null) notes
                    else notes.filter { it.updatedAt.isAfter(after) }

                respondText(ex, 200, NoteWire.encodeLines(filtered))
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
                val incoming = NoteWire.decodeLines(body)

                var applied = 0
                for (remote in incoming) {
                    if (applyIfNewer(remote)) applied++
                }

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

    /**
     * Last-Write-Wins anhand updatedAt.
     */
    private fun applyIfNewer(remote: Note): Boolean {
        val local = noteRepo.findById(remote.id)

        if (local == null) {
            noteRepo.save(remote)
            return true
        }

        val shouldApply =
            remote.updatedAt.isAfter(local.updatedAt) ||
                    (remote.updatedAt == local.updatedAt &&
                            (remote.title != local.title ||
                                    remote.content != local.content ||
                                    remote.trashedAt != local.trashedAt))

        if (shouldApply) {
            noteRepo.save(remote)
            return true
        }
        return false
    }

    private fun applyNotebook(remote: Notebook): Boolean {
        val local = notebookRepo.findById(remote.id)

        if (local == null) {
            notebookRepo.save(remote)
            return true
        }

        val shouldApply =
            local.name != remote.name ||
                    local.parentId != remote.parentId ||
                    local.trashedAt != remote.trashedAt

        if (shouldApply) {
            notebookRepo.save(remote)
            return true
        }

        return false
    }

    private fun respondText(ex: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { os: OutputStream -> os.write(bytes) }
    }

    private fun queryParam(rawQuery: String, key: String): String? {
        val pairs = rawQuery.split("&").filter { it.isNotBlank() }
        for (p in pairs) {
            val idx = p.indexOf("=")
            val k = if (idx >= 0) p.substring(0, idx) else p
            val v = if (idx >= 0) p.substring(idx + 1) else ""
            if (k == key) return URLDecoder.decode(v, "UTF-8")
        }
        return null
    }
}
