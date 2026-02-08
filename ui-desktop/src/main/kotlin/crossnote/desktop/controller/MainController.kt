package crossnote.desktop.controller

import crossnote.app.note.NoteAppService
import crossnote.desktop.NavNode
import crossnote.desktop.ThemeManager
import crossnote.desktop.TrashNode
import crossnote.desktop.actions.NotebookActions
import crossnote.desktop.cell.NotebookTreeCell
import crossnote.desktop.presenter.NoteEditorPresenter
import crossnote.desktop.presenter.NotebookTreePresenter
import crossnote.desktop.presenter.SavestatePresenter
import crossnote.desktop.presenter.TrashPresenter
import crossnote.desktop.ui.UiStateController
import crossnote.domain.note.NoteId
import crossnote.infra.persistence.SqliteDatabase
import crossnote.infra.persistence.SqliteNoteRepository
import crossnote.infra.persistence.SqliteNotebookRepository
import crossnote.infra.persistence.SqliteRevisionRepository
import crossnote.infra.persistence.SqliteSettingsRepository
import crossnote.infra.persistence.SystemClock
import crossnote.infra.persistence.UuidIdGenerator
import crossnote.app.sync.SyncService
import crossnote.desktop.util.Dialogs
import crossnote.desktop.sync.HttpSyncClient
import crossnote.desktop.sync.LocalSyncServer
import crossnote.desktop.sync.NoteWire
import java.time.Instant
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Dialog
import javafx.scene.control.TextInputDialog
import javafx.scene.layout.GridPane
import javafx.geometry.Insets
import javafx.scene.control.TextField as FxTextField
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.MenuItem
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.layout.AnchorPane
import java.nio.file.Paths

class MainController {

    private companion object {
        const val TEXT_NOT_SAVED = "Nicht gespeichert"
        const val TEXT_NO_DATE = "--"
        const val TEXT_NO_NOTE_SELECTED = "Keine Notiz ausgewählt"
        const val TEXT_TRASH = "Papierkorb"
        const val TEXT_REVISION_RESTORED = "Auf Revision zurückgesetzt"
    }

    // ---------- Persistence / Service ----------
    private val db = SqliteDatabase(Paths.get(System.getProperty("user.home"), ".crossnote", "crossnote.db"))
    private val settingsRepo = SqliteSettingsRepository(db)
    private val notebookRepo = SqliteNotebookRepository(db)
    private val noteRepo = SqliteNoteRepository(db)

    private val service = NoteAppService(
        repo = noteRepo,
        revisionRepo = SqliteRevisionRepository(db),
        ids = UuidIdGenerator(),
        clock = SystemClock()
    )

    private val syncService = SyncService(settingsRepo)
    private val syncClient = HttpSyncClient()
    private val localSyncServer = LocalSyncServer(noteRepo)

    // ---------- Left panes ----------
    @FXML lateinit var APnotebooks: AnchorPane
    @FXML lateinit var APtrashcan: AnchorPane
    @FXML lateinit var APsavestate: AnchorPane

    // ---------- Notebooks ----------
    @FXML lateinit var TFnotebook: TextField
    @FXML lateinit var TVnotebook: TreeView<NavNode>

    // ---------- Trash ----------
    @FXML lateinit var TFtrashcan: TextField
    @FXML lateinit var TVtrashcan: TreeView<TrashNode>

    // ---------- Savestates ----------
    @FXML lateinit var LBdataname: Label
    @FXML lateinit var LVsavestate: ListView<Pair<String, String>>
    @FXML lateinit var BTNload: Button

    // ---------- Buttons ----------
    @FXML lateinit var BTNtrashcan: Button
    @FXML lateinit var BTNsavestate: Button
    @FXML lateinit var BTNdarkmode: Button
    @FXML lateinit var BTNsave: Button
    @FXML lateinit var BTNsync: Button

    // ---------- Editor ----------
    @FXML lateinit var titleField: TextField
    @FXML lateinit var contentArea: TextArea
    @FXML lateinit var LBlastchange: Label
    @FXML lateinit var LBsaved: Label

    // ---------- Roots ----------
    private val treeRoot = TreeItem<NavNode>(NavNode.RootHeader).apply { isExpanded = true }
    private val trashRoot = TreeItem<TrashNode>(TrashNode.Root).apply { isExpanded = true }

    // ---------- Controllers / Presenter ----------
    private lateinit var uiState: UiStateController
    private lateinit var themeManager: ThemeManager
    private lateinit var editor: NoteEditorPresenter
    private lateinit var notebookTreePresenter: NotebookTreePresenter
    private lateinit var savestatePresenter: SavestatePresenter
    private lateinit var trashPresenter: TrashPresenter
    private lateinit var notebookActions: NotebookActions

    // =========================================================
    // Lifecycle
    // =========================================================
    @FXML
    fun initialize() {
        service.purgeTrashedOlderThan(30)

        initUiState()
        initEditor()

        setupTrashTree()
        setupSavestates()
        setupNotebookPresenter()

        setupNotebookActions()
        setupNotebookTreeUi()

        setupButtons()
        setupTheme()

        applyInitialUiState()

        startServerIfEnabled()
    }

    // =========================================================
    // Init
    // =========================================================
    private fun initUiState() {
        uiState = UiStateController(
            notebooksPane = APnotebooks,
            trashPane = APtrashcan,
            savestatePane = APsavestate,
            saveButton = BTNsave,
            loadButton = BTNload,
            backButton = BTNsavestate,
            titleField = titleField,
            contentArea = contentArea
        )
        uiState.showNotebooks()
    }

    private fun initEditor() {
        editor = NoteEditorPresenter(
            service = service,
            titleField = titleField,
            contentArea = contentArea,
            lastChangeLabel = LBlastchange,
            savedLabel = LBsaved,
            trashCountdownText = { id -> trashPresenter.trashCountdownText(id) },
            onAfterSaveOrDelete = { notebookTreePresenter.refresh() }
        )
    }

    private fun applyInitialUiState() {
        LBsaved.text = TEXT_NOT_SAVED
        LBlastchange.text = TEXT_NO_DATE
        LBdataname.text = TEXT_NO_NOTE_SELECTED

        notebookTreePresenter.refresh()
        uiState.applyCenterMode()
    }

    // =========================================================
    // Setup
    // =========================================================
    private fun setupTrashTree() {
        trashPresenter = TrashPresenter(
            service = service,
            notebookRepo = notebookRepo,
            trashTree = TVtrashcan,
            searchField = TFtrashcan,
            trashRoot = trashRoot,
            onOpenNoteInTrash = { id -> editor.openNote(id, true) },
            onTrashNonNoteSelected = {
                editor.resetEditor()
                LBsaved.text = TEXT_TRASH
                LBlastchange.text = TEXT_NO_DATE
            },
            onRefreshNotebooks = { notebookTreePresenter.refresh() }
        )
        trashPresenter.init()

        TVtrashcan.setCellFactory {
            object : TreeCell<TrashNode>() {
                override fun updateItem(item: TrashNode?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) "" else item.displayText()
                    contextMenu = if (empty || item == null) null else trashPresenter.buildContextMenu(item)
                }
            }
        }
    }

    private fun setupSavestates() {
        savestatePresenter = SavestatePresenter(
            service = service,
            listView = LVsavestate,
            dataNameLabel = LBdataname,
            titleFieldSetter = { titleField.text = it },
            contentAreaSetter = { contentArea.text = it },
            savedLabelSetter = { LBsaved.text = it },
            lastChangeLabelSetter = { LBlastchange.text = it },
            loadButton = BTNload,
            onLoadedRevision = { noteId ->
                uiState.showNotebooks()
                clearEditorAndSelections()

                notebookTreePresenter.refresh()
                editor.openNote(noteId, false)

                LBsaved.text = TEXT_REVISION_RESTORED
            }
        )
        savestatePresenter.init()
    }

    private fun setupNotebookPresenter() {
        notebookTreePresenter = NotebookTreePresenter(
            notebookRepo = notebookRepo,
            noteRepo = noteRepo,
            searchField = TFnotebook,
            treeView = TVnotebook,
            treeRoot = treeRoot,
            onOpenNote = { editor.openNote(it, false) },
            onNotebookSelected = { editor.selectedNotebookId = it },
            onResetEditor = { editor.resetEditor() }
        )
        notebookTreePresenter.init()
    }

    private fun setupNotebookActions() {
        notebookActions = NotebookActions(
            service = service,
            notebookRepo = notebookRepo,
            noteRepo = noteRepo,
            onAfterChange = { notebookTreePresenter.refresh() },
            onAfterTrashChange = { trashPresenter.refresh() },
            onClearSelection = { clearEditorAndSelections() },
            onSelectedNotebookChanged = { editor.selectedNotebookId = it }
        )
    }

    private fun setupNotebookTreeUi() {
        TVnotebook.setCellFactory {
            NotebookTreeCell(
                service = service,
                notebookRepo = notebookRepo,
                onRefreshTree = { notebookTreePresenter.refresh() },
                onOpenNote = { editor.openNote(it, false) },
                onDeleteNote = { noteId ->
                    editor.deleteNote(noteId)
                    notebookTreePresenter.refresh()
                    trashPresenter.refresh()
                },
                onOpenSavestates = { openSavestatesFor(it) },
                onStartNewNote = { targetNotebookId ->
                    editor.startNewNote(targetNotebookId)
                    uiState.applyCenterMode()
                },
                onCreateNotebook = { parent -> notebookActions.createNotebookDialog(parent) },
                onTrashNotebookRecursively = { nbId -> notebookActions.trashNotebookRecursively(nbId) }
            )
        }

        TVnotebook.contextMenu = ContextMenu(
            MenuItem("➕ Ordner erstellen").apply {
                setOnAction { notebookActions.createNotebookDialog(null) }
            },
            MenuItem("＋ Notiz im Root erstellen").apply {
                setOnAction {
                    editor.startNewNote(null)
                    uiState.applyCenterMode()
                }
            }
        )
    }

    private fun setupButtons() {
        BTNtrashcan.setOnAction {
            clearEditorAndSelections()

            if (uiState.isTrashVisible()) {
                uiState.showNotebooks()
                notebookTreePresenter.refresh()
            } else {
                uiState.showTrash()
                trashPresenter.refresh()
            }
        }

        BTNsavestate.setOnAction {
            uiState.showNotebooks()
            clearEditorAndSelections()
            notebookTreePresenter.refresh()
        }

        BTNsave.setOnAction { onSave() }

        BTNsync.setOnAction {
            openSyncSettingsDialog()
        }
    }

    private fun setupTheme() {
        themeManager = ThemeManager(settingsRepo, BTNdarkmode)
        themeManager.bindToSceneRoot()
    }

    // =========================================================
    // Actions
    // =========================================================
    @FXML
    fun darkmode_on(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        themeManager.toggle()
    }

    private fun openSavestatesFor(noteId: NoteId) {
        uiState.showSavestates()
        clearEditorAndSelections()
        savestatePresenter.showFor(noteId)
    }

    private fun onSave() {
        editor.saveIfEditable(uiState.isNotebooksVisible())
    }

    // =========================================================
    // UI helpers
    // =========================================================
    private fun clearEditorAndSelections() {
        editor.resetEditor()
        TVnotebook.selectionModel.clearSelection()
        TVtrashcan.selectionModel.clearSelection()
        LVsavestate.selectionModel.clearSelection()
    }

    private fun openSyncSettingsDialog() {
        val cfg = syncService.loadConfig()

        val enabled = CheckBox("Synchronisation aktiv").apply { isSelected = cfg.enabled }
        val serverMode = CheckBox("Diesen Rechner als Server verwenden").apply { isSelected = cfg.serverMode }

        val hostField = FxTextField(cfg.host)
        val portField = FxTextField(cfg.port.toString())

        fun applyServerModeUi() {
            val isServer = serverMode.isSelected
            hostField.isDisable = isServer
            hostField.opacity = if (isServer) 0.6 else 1.0
            // port bleibt aktiv: auch Server braucht Port
        }

        serverMode.selectedProperty().addListener { _, _, _ -> applyServerModeUi() }
        applyServerModeUi()

        val grid = GridPane().apply {
            hgap = 10.0
            vgap = 10.0
            padding = Insets(10.0)

            add(enabled, 0, 0, 2, 1)
            add(serverMode, 0, 1, 2, 1)

            add(javafx.scene.control.Label("Server Host:"), 0, 2)
            add(hostField, 1, 2)

            add(javafx.scene.control.Label("Port:"), 0, 3)
            add(portField, 1, 3)
        }

        val dialog = Dialog<ButtonType>().apply {
            title = "Synchronisation"
            headerText = "Server auswählen oder diesen Rechner als Server nutzen"
            dialogPane.content = grid

            val btnSyncNow = ButtonType("Jetzt synchronisieren", ButtonType.OK.buttonData)
            dialogPane.buttonTypes.addAll(btnSyncNow, ButtonType.APPLY, ButtonType.CANCEL)
        }

        val result = dialog.showAndWait()
        if (result.isEmpty) return

        // Speichern, wenn APPLY oder SyncNow gedrückt wurde
        if (result.get() == ButtonType.APPLY || result.get().text == "Jetzt synchronisieren") {
            val port = portField.text.trim().toIntOrNull()
            if (port == null || port <= 0 || port > 65535) {
                Dialogs.error("Synchronisation", "Ungültiger Port: '${portField.text}'")
                return
            }

            val newCfg = cfg.copy(
                enabled = enabled.isSelected,
                serverMode = serverMode.isSelected,
                host = hostField.text.trim().ifBlank { "localhost" },
                port = port
            )
            syncService.saveConfig(newCfg)
        }

        // SyncNow gedrückt
        if (result.get().text == "Jetzt synchronisieren") {
            doSyncNow()
        }
    }

    private fun doSyncNow() {
        val cfg = syncService.loadConfig()
        if (!cfg.enabled) {
            Dialogs.info("Synchronisation", "Synchronisation ist deaktiviert (siehe Einstellungen).")
            return
        }

        // Wenn dieser Rechner Server ist: nur starten/Status anzeigen (Client-Sync wäre sinnlos gegen sich selbst)
        if (cfg.serverMode) {
            if (!localSyncServer.isRunning()) {
                try {
                    localSyncServer.start(cfg.port)
                } catch (t: Throwable) {
                    Dialogs.error("Synchronisation", "Server konnte nicht gestartet werden: ${t.message}")
                    return
                }
            }
            Dialogs.info("Synchronisation", "Server-Modus aktiv.\nServer läuft auf Port ${cfg.port}.")
            return
        }

        // CLIENT MODE: Pull + Push
        try {
            // 1) PULL
            val pullAfter: Instant? = cfg.lastPulledAt
            val pulledBody = syncClient.pullNotes(cfg.host, cfg.port, pullAfter)
            val pulledNotes = NoteWire.decodeLines(pulledBody)

            var pulledApplied = 0
            for (remote in pulledNotes) {
                val local = noteRepo.findById(remote.id)
                if (local == null || remote.updatedAt.isAfter(local.updatedAt)) {
                    noteRepo.save(remote)
                    pulledApplied++
                }
            }

            val newLastPulledAt = pulledNotes.maxOfOrNull { it.updatedAt } ?: cfg.lastPulledAt

            // 2) PUSH (alle lokalen Notes nach lastPushedAt)
            val pushAfter: Instant? = cfg.lastPushedAt
            val localChanged =
                noteRepo.findAll().filter { note ->
                    pushAfter == null || note.updatedAt.isAfter(pushAfter)
                }

            val pushBody = NoteWire.encodeLines(localChanged)
            val pushResult = syncClient.pushNotes(cfg.host, cfg.port, pushBody)

            val newLastPushedAt = localChanged.maxOfOrNull { it.updatedAt } ?: cfg.lastPushedAt

            // 3) Watermarks speichern
            val updatedCfg = cfg.copy(
                lastPulledAt = newLastPulledAt,
                lastPushedAt = newLastPushedAt
            )
            syncService.saveConfig(updatedCfg)

            // 4) UI refresh
            notebookTreePresenter.refresh()
            trashPresenter.refresh()

            Dialogs.info(
                "Synchronisation",
                "Pull: erhalten=${pulledNotes.size}, übernommen=$pulledApplied\n" +
                    "Push: gesendet=${localChanged.size}, Server: $pushResult"
            )
        } catch (t: Throwable) {
            Dialogs.error("Synchronisation", "Sync fehlgeschlagen: ${t.message}")
        }
    }

    private fun startServerIfEnabled() {
        val cfg = syncService.loadConfig()
        if (cfg.enabled && cfg.serverMode) {
            try {
                localSyncServer.start(cfg.port)
            } catch (t: Throwable) {
                Dialogs.error("Synchronisation", "Server konnte nicht gestartet werden: ${t.message}")
            }
        }
    }

    fun close() {
        localSyncServer.stop()
        db.close()
    }
}
