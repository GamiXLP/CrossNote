package de.crossnote.ui.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crossnote.app.note.NoteAppService
import crossnote.app.note.NoteSummaryDto
import crossnote.app.note.NotebookTreeDto
import crossnote.app.note.RevisionSummaryDto
import crossnote.domain.note.*
import crossnote.domain.revision.RevisionId
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
import java.time.Duration
import java.time.Instant

class NotesViewModel : ViewModel() {

    companion object {
        private val noteRepo = InMemoryNoteRepository()
        private val notebookRepo = InMemoryNotebookRepository()
        private val revisionRepo = InMemoryRevisionRepository()
        private val idGenerator = UuidIdGenerator()
        private val clock = SystemClock()
        
        private val noteAppService = NoteAppService(
            repo = noteRepo,
            revisionRepo = revisionRepo,
            ids = idGenerator,
            clock = clock
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
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _expandedNotebookIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedNotebookIds: StateFlow<Set<String>> = _expandedNotebookIds.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        refreshAll()
    }

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
        _currentNote.value = null
        refreshAll()
    }

    fun refreshAll() {
        _notes.value = noteAppService.listActiveNotes()
        _trashedNotes.value = noteAppService.listTrashedNotes()
        _notebookTree.value = noteAppService.listNotebookTree(notebookRepo)
        _trashedNotebookTree.value = noteAppService.listTrashedNotebookTree(notebookRepo)
        
        _currentNote.value?.let { 
            if (it.id.value != "temp-new") {
                _revisions.value = noteAppService.listRevisions(it.id)
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }

    // --- Note Actions ---
    fun startNewNote(notebookId: String? = null) {
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
    }

    fun saveNewNote(title: String, content: String, notebookId: NotebookId?) {
        noteAppService.createNote(title, content, notebookId)
        refreshAll()
        closeNote()
    }

    fun selectNote(id: String) {
        val note = noteAppService.getNote(NoteId(id))
        _currentNote.value = note
        _revisions.value = noteAppService.listRevisions(note.id)
    }

    fun closeNote() {
        _currentNote.value = null
        _revisions.value = emptyList()
        refreshAll()
    }

    fun updateNote(id: String, title: String, content: String) {
        if (id == "temp-new") {
            saveNewNote(title, content, _currentNote.value?.notebookId)
        } else {
            noteAppService.updateNote(NoteId(id), title, content)
            refreshAll()
            closeNote()
        }
    }

    fun moveNoteToTrash(id: String) {
        noteAppService.moveToTrash(NoteId(id))
        closeNote()
    }

    fun restoreNote(id: String) {
        noteAppService.restore(NoteId(id), notebookRepo)
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
        val id = NotebookId(java.util.UUID.randomUUID().toString())
        notebookRepo.save(Notebook(id, name, parentId?.let { NotebookId(it) }))
        refreshAll()
    }

    fun renameNotebook(id: String, newName: String) {
        val notebook = notebookRepo.findById(NotebookId(id)) ?: return
        notebookRepo.save(notebook.copy(name = newName))
        refreshAll()
    }

    fun deleteNotebook(id: String) {
        noteAppService.moveNotebookToTrash(NotebookId(id), notebookRepo)
        refreshAll()
    }
    
    fun restoreNotebook(id: String) {
        noteAppService.restoreNotebook(NotebookId(id), notebookRepo)
        refreshAll()
    }

    fun purgeNotebook(id: String) {
        noteAppService.purgeNotebookPermanently(NotebookId(id), notebookRepo)
        refreshAll()
    }
    
    fun moveNotebook(notebookId: String, newParentId: String?) {
        try {
            noteAppService.moveNotebook(NotebookId(notebookId), newParentId?.let { NotebookId(it) }, notebookRepo)
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

                // 1. Pull Notebooks
                val nbPullReq = Request.Builder().url("http://$host:$port/notebooks").build()
                client.newCall(nbPullReq).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let {
                            noteAppService.mergeNotebooksFromWire(it, notebookRepo)
                        }
                    }
                }

                // 2. Pull Notes
                val nPullReq = Request.Builder().url("http://$host:$port/notes").build()
                client.newCall(nPullReq).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let {
                            noteAppService.mergeNotesFromWire(it)
                        }
                    }
                }

                // 3. Push Notebooks
                val nbPushBody = noteAppService.getAllNotebooksAsWire(notebookRepo)
                val nbPushReq = Request.Builder()
                    .url("http://$host:$port/notebooks/push")
                    .post(nbPushBody.toRequestBody())
                    .build()
                client.newCall(nbPushReq).execute().close()

                // 4. Push Notes
                val nPushBody = noteAppService.getAllNotesAsWire()
                val nPushReq = Request.Builder()
                    .url("http://$host:$port/notes/push")
                    .post(nPushBody.toRequestBody())
                    .build()
                client.newCall(nPushReq).execute().close()

                launch(Dispatchers.Main) {
                    refreshAll()
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
}
