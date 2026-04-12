package de.crossnote.ui.android

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import crossnote.app.note.NoteAppService
import crossnote.app.note.NoteSummaryDto
import crossnote.app.note.NotebookTreeDto
import crossnote.app.note.RevisionSummaryDto
import crossnote.domain.note.*
import crossnote.domain.revision.Revision
import crossnote.domain.revision.RevisionId
import crossnote.domain.revision.RevisionRepository
import crossnote.domain.settings.SettingsRepository
import crossnote.domain.settings.getBoolean
import crossnote.domain.settings.setBoolean
import crossnote.infra.persistence.*
import de.crossnote.ui.android.data.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val db: SqliteDatabase
    private val noteRepo: NoteRepository
    private val notebookRepo: NotebookRepository
    private val revisionRepo: RevisionRepository
    private val settingsRepo: SettingsRepository
    private val noteAppService: NoteAppService

    private var currentSessionRevisionId: String? = null

    init {
        // Ensure SQLDroid driver is loaded
        try {
            Class.forName("org.sqldroid.SQLDroidDriver")
        } catch (e: ClassNotFoundException) {
            // Might be on desktop or not yet synced
        }

        val dbPath = application.getDatabasePath("crossnote.db").toPath()
        db = SqliteDatabase(dbPath)
        noteRepo = SqliteNoteRepository(db)
        notebookRepo = SqliteNotebookRepository(db)
        revisionRepo = SqliteRevisionRepository(db)
        settingsRepo = SqliteSettingsRepository(db)
        
        noteAppService = NoteAppService(
            repo = noteRepo,
            notebookRepo = notebookRepo,
            revisionRepo = revisionRepo,
            ids = UuidIdGenerator(),
            clock = SystemClock()
        )
    }

    private val _currentScreen = MutableStateFlow(Screen.Notes)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _notes = MutableStateFlow<List<NoteSummaryDto>>(emptyList())
    val notes: StateFlow<List<NoteSummaryDto>> = _notes.asStateFlow()

    private val _trashedNotes = MutableStateFlow<List<NoteSummaryDto>>(emptyList())
    val trashedNotes: StateFlow<List<NoteSummaryDto>> = _trashedNotes.asStateFlow()
    
    private val _trashedNotebookTree = MutableStateFlow<NotebookTreeDto?>(null)
    val trashedNotebookTree: StateFlow<NotebookTreeDto?> = _trashedNotebookTree.asStateFlow()

    private val _notebookTree = MutableStateFlow<NotebookTreeDto?>(null)
    val notebookTree: StateFlow<NotebookTreeDto?> = _notebookTree.asStateFlow()

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    private val _revisions = MutableStateFlow<List<RevisionSummaryDto>>(emptyList())
    val revisions: StateFlow<List<RevisionSummaryDto>> = _revisions.asStateFlow()

    private val _previewRevision = MutableStateFlow<Revision?>(null)
    val previewRevision: StateFlow<Revision?> = _previewRevision.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    private val _expandedNotebookIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedNotebookIds: StateFlow<Set<String>> = _expandedNotebookIds.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    init {
        val isSystemInDarkMode = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        _isDarkMode.value = settingsRepo.getBoolean("dark_mode", isSystemInDarkMode)
        refreshAll()
    }

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
        _currentNote.value = null
        currentSessionRevisionId = null
        refreshAll()
    }

    fun refreshAll() {
        _notes.value = noteAppService.listActiveNotes()
        _trashedNotes.value = noteAppService.listTrashedNotes()
        _notebookTree.value = noteAppService.listNotebookTree()
        _trashedNotebookTree.value = noteAppService.listTrashedNotebookTree()
        
        _currentNote.value?.let { current ->
            if (current.id.value != "temp-new") {
                // Fetch updated version from DB (e.g. after sync)
                _currentNote.value = noteAppService.getNote(current.id)
                _revisions.value = noteAppService.listRevisions(current.id)
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }

    fun clearInfo() {
        _infoMessage.value = null
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        settingsRepo.setBoolean("dark_mode", enabled)
    }

    // --- Note Actions ---
    fun startNewNote(notebookId: String? = null) {
        currentSessionRevisionId = null
        val id = NoteId("temp-new")
        _currentNote.value = Note(
            id = id,
            notebookId = notebookId?.let { NotebookId(it) },
            title = "",
            content = "",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            trashedAt = null
        )
        _revisions.value = emptyList()
        _previewRevision.value = null
    }

    fun saveNewNote(title: String, content: String, notebookId: NotebookId?) {
        val newId = noteAppService.createNote(title, content, notebookId)
        _currentNote.value = noteAppService.getNote(newId)
        refreshAll()
    }

    fun selectNote(id: String) {
        currentSessionRevisionId = null
        val note = noteAppService.getNote(NoteId(id))
        _currentNote.value = note
        _revisions.value = noteAppService.listRevisions(note.id)
        _previewRevision.value = null
    }

    fun closeNote() {
        _currentNote.value = null
        _revisions.value = emptyList()
        _previewRevision.value = null
        currentSessionRevisionId = null
        refreshAll()
    }

    fun updateNote(id: String, title: String, content: String) {
        if (id == "temp-new") {
            saveNewNote(title, content, _currentNote.value?.notebookId)
            closeNote()
        } else {
            noteAppService.updateNote(NoteId(id), title, content, currentSessionRevisionId)
            refreshAll()
            closeNote()
        }
    }

    fun persistNote(id: String, title: String, content: String) {
        if (id == "temp-new") {
            // Auto-save creates the note if it has content/title
            if (title.isNotBlank() || content.isNotBlank()) {
                saveNewNote(title, content, _currentNote.value?.notebookId)
            }
        } else {
            currentSessionRevisionId = noteAppService.updateNote(NoteId(id), title, content, currentSessionRevisionId)
            refreshAll()
        }
    }

    fun moveNoteToTrash(id: String) {
        noteAppService.moveToTrash(NoteId(id))
        closeNote()
    }

    fun restoreNote(id: String) {
        noteAppService.restore(NoteId(id))
        refreshAll()
    }

    fun purgeNote(id: String) {
        noteAppService.purgeNotePermanently(NoteId(id))
        refreshAll()
    }
    
    fun moveNote(noteId: String, notebookId: String?) {
        try {
            noteAppService.moveNoteToNotebook(NoteId(noteId), notebookId?.let { NotebookId(it) })
            refreshAll()
        } catch (e: Exception) {
            _errorMessage.value = e.message
        }
    }

    // --- Notebook Actions ---
    fun createNotebook(name: String, parentId: String? = null) {
        noteAppService.createNotebook(name, parentId?.let { NotebookId(it) })
        refreshAll()
    }

    fun renameNotebook(id: String, newName: String) {
        val notebook = notebookRepo.findById(NotebookId(id)) ?: return
        notebookRepo.save(notebook.copy(name = newName, updatedAt = Instant.now()))
        refreshAll()
    }

    fun deleteNotebook(id: String) {
        noteAppService.moveNotebookToTrash(NotebookId(id))
        refreshAll()
    }
    
    fun restoreNotebook(id: String) {
        noteAppService.restoreNotebook(NotebookId(id))
        refreshAll()
    }

    fun purgeNotebook(id: String) {
        noteAppService.purgeNotebookPermanently(NotebookId(id))
        refreshAll()
    }
    
    fun moveNotebook(notebookId: String, newParentId: String?) {
        try {
            noteAppService.moveNotebook(NotebookId(notebookId), newParentId?.let { NotebookId(it) })
            refreshAll()
        } catch (e: Exception) {
            _errorMessage.value = e.message
        }
    }

    fun toggleNotebookExpanded(id: String) {
        val current = _expandedNotebookIds.value
        if (current.contains(id)) {
            _expandedNotebookIds.value = current - id
        } else {
            _expandedNotebookIds.value = current + id
        }
    }

    // --- Revision Actions ---
    fun selectRevisionForPreview(revisionId: String) {
        _previewRevision.value = noteAppService.getRevision(RevisionId(revisionId))
    }

    fun clearPreview() {
        _previewRevision.value = null
    }

    fun restoreRevision(noteId: String, revisionId: String) {
        noteAppService.restoreFromRevision(NoteId(noteId), RevisionId(revisionId))
        selectNote(noteId)
        refreshAll()
    }

    // --- Sync Actions ---
    fun syncWithServer(host: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()

                var nbApplied = 0
                var nApplied = 0
                var nbPushResult = ""
                var nPushResult = ""

                // 1. Pull Notebooks
                val nbPullReq = Request.Builder().url("http://$host:$port/notebooks").build()
                client.newCall(nbPullReq).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let {
                            nbApplied = noteAppService.mergeNotebooksFromWire(it)
                        }
                    } else {
                         throw Exception("Pull Notebooks failed: ${response.code}")
                    }
                }

                // 2. Pull Notes
                val nPullReq = Request.Builder().url("http://$host:$port/notes").build()
                client.newCall(nPullReq).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let {
                            nApplied = noteAppService.mergeNotesFromWire(it)
                        }
                    } else {
                         throw Exception("Pull Notes failed: ${response.code}")
                    }
                }

                // 3. Push Notebooks
                val nbPushBody = noteAppService.getAllNotebooksAsWire()
                val nbPushReq = Request.Builder()
                    .url("http://$host:$port/notebooks/push")
                    .post(nbPushBody.toRequestBody())
                    .build()
                client.newCall(nbPushReq).execute().use { response ->
                    nbPushResult = if (response.isSuccessful) response.body?.string() ?: "ok" else "Error: ${response.code}"
                }

                // 4. Push Notes
                val nPushBody = noteAppService.getAllNotesAsWire()
                val nPushReq = Request.Builder()
                    .url("http://$host:$port/notes/push")
                    .post(nPushBody.toRequestBody())
                    .build()
                client.newCall(nPushReq).execute().use { response ->
                    nPushResult = if (response.isSuccessful) response.body?.string() ?: "ok" else "Error: ${response.code}"
                }

                launch(Dispatchers.Main) {
                    refreshAll()
                    _infoMessage.value = "Synchronisation erfolgreich\n\n" +
                            "Notebooks Pull: übernommen=$nbApplied\n" +
                            "Notebooks Push: Server: $nbPushResult\n\n" +
                            "Notes Pull: übernommen=$nApplied\n" +
                            "Notes Push: Server: $nPushResult"
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _errorMessage.value = "Sync failed: ${e.message}"
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        db.close()
    }
}
