package crossnote.desktop.controller

import crossnote.app.note.NoteAppService
import crossnote.app.sync.SyncService
import crossnote.desktop.NavNode
import crossnote.desktop.ThemeManager
import crossnote.desktop.TrashNode
import crossnote.desktop.actions.NotebookActions
import crossnote.desktop.cell.NotebookTreeCell
import crossnote.desktop.presenter.NoteEditorPresenter
import crossnote.desktop.presenter.NotebookTreePresenter
import crossnote.desktop.presenter.SavestatePresenter
import crossnote.desktop.presenter.TrashPresenter
import crossnote.desktop.sync.HttpSyncClient
import crossnote.desktop.sync.LocalSyncServer
import crossnote.desktop.sync.NoteWire
import crossnote.desktop.sync.NotebookWire
import crossnote.desktop.sync.UdpDiscoveryClient
import crossnote.desktop.sync.UdpDiscoveryServer
import crossnote.desktop.ui.UiStateController
import crossnote.desktop.util.Dialogs
import crossnote.desktop.util.NotebookTreeUtils
import crossnote.domain.note.NoteId
import crossnote.infra.persistence.SqliteDatabase
import crossnote.infra.persistence.SqliteNoteRepository
import crossnote.infra.persistence.SqliteNotebookRepository
import crossnote.infra.persistence.SqliteRevisionRepository
import crossnote.infra.persistence.SqliteSettingsRepository
import crossnote.infra.persistence.SystemClock
import crossnote.infra.persistence.UuidIdGenerator
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ContextMenu
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.MenuItem
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import java.nio.file.Paths
import javafx.scene.control.TextField as FxTextField

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

    // --------- LAN Discovery (UDP) ----------
    private val discoveryClient = UdpDiscoveryClient()
    private val discoveryServer = UdpDiscoveryServer(
        httpPort = { syncService.loadConfig().port },
        serverName = { "CrossNote (${System.getProperty("user.name")})" }
    )

    private val localSyncServer: LocalSyncServer by lazy {
        LocalSyncServer(
            notebookRepo = notebookRepo,
            noteRepo = noteRepo,
            onDataChanged = {
                // kommt aus HTTP-Worker-Thread -> UI-Thread erzwingen
                Platform.runLater {
                    if (::notebookTreePresenter.isInitialized) {
                        notebookTreePresenter.refresh()
                    }
                    if (::trashPresenter.isInitialized) {
                        trashPresenter.refresh()
                    }
                }
            }
        )
    }

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
    @FXML lateinit var BTNemptyTrash: Button

    // ---------- Editor ----------
    @FXML lateinit var titleField: TextField
    @FXML lateinit var contentArea: TextArea
    @FXML lateinit var LBlastchange: Label
    @FXML lateinit var LBsaved: Label
    @FXML lateinit var LBtitleCount: Label

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
            titleCountLabel = LBtitleCount,
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

                private var expandedListener: javafx.beans.value.ChangeListener<Boolean>? = null
                private var listenedTreeItem: TreeItem<TrashNode>? = null

                override fun updateItem(item: TrashNode?, empty: Boolean) {
                    // Wichtig: erst cleanup vom alten TreeItem
                    expandedListener?.let { l ->
                        listenedTreeItem?.expandedProperty()?.removeListener(l)
                    }
                    expandedListener = null
                    listenedTreeItem = null

                    super.updateItem(item, empty)

                    if (empty || item == null) {
                        text = ""
                        graphic = null
                        contextMenu = null
                        return
                    }

                    text = item.displayText()
                    contextMenu = trashPresenter.buildContextMenu(item)

                    when (item) {
                        is TrashNode.FolderBranch -> {
                            val ti = treeItem ?: return

                            fun buildIcon(expanded: Boolean) =
                                if (expanded) NotebookTreeUtils.createOpenFolderIcon()
                                else NotebookTreeUtils.createClosedFolderIcon()

                            fun updateIcon() {
                                val icon = buildIcon(ti.isExpanded)

                                // ✅ GENAU WIE IM NOTEBOOK TREE: Klick auf Icon toggelt Expand
                                icon.setOnMouseClicked { e ->
                                    ti.isExpanded = !ti.isExpanded
                                    e.consume()
                                }

                                icon.opacity = 0.70
                                graphic = icon
                            }

                            updateIcon()

                            val l = javafx.beans.value.ChangeListener<Boolean> { _, _, _ ->
                                updateIcon()
                            }
                            expandedListener = l
                            ti.expandedProperty().addListener(l)
                            listenedTreeItem = ti
                        }

                        is TrashNode.NoteLeaf -> {
                            graphic = NotebookTreeUtils.createNoteIcon()
                            graphic?.opacity = 0.75
                        }

                        TrashNode.Root -> {
                            graphic = null
                        }
                    }
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
                BTNemptyTrash.isVisible = false
                BTNemptyTrash.isManaged = false
                notebookTreePresenter.refresh()
            } else {
                uiState.showTrash()
                BTNemptyTrash.isVisible = true
                BTNemptyTrash.isManaged = true
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

        BTNemptyTrash.setOnAction {
            trashPresenter.purgeAllPermanently()
            clearEditorAndSelections()
        }
    }

    private fun setupTheme() {
        themeManager = ThemeManager(settingsRepo, BTNdarkmode)
        themeManager.bindToSceneRoot()
        Dialogs.init(themeManager)
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

            add(Label("Server Host:"), 0, 2)
            add(hostField, 1, 2)

            add(Label("Port:"), 0, 3)
            add(portField, 1, 3)
        }

        // --- Custom Footer Buttons (volle Breite, alle gleich breit) ---
        val btnFindServer = Button("Server finden")
        val btnApply = Button("Apply")
        val btnCancel = Button("Cancel")
        val btnSyncNow = Button("Sync now")

        // Default-/Cancel-Behavior wie Dialog-Buttons
        btnSyncNow.isDefaultButton = true
        btnCancel.isCancelButton = true

        val footer = HBox(12.0, btnFindServer, btnApply, btnCancel, btnSyncNow).apply {
            alignment = Pos.CENTER
            padding = Insets(12.0, 16.0, 0.0, 16.0)

            // Buttons sollen die ganze Zeile füllen und gleich breit sein
            children.forEach { node ->
                if (node is Button) {
                    node.minWidth = 0.0
                    node.maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(node, Priority.ALWAYS)
                }
            }
        }

        // Content = Grid + Footer untereinander
        val content = javafx.scene.layout.VBox(10.0, grid, footer).apply {
            padding = Insets(0.0)
        }

        val dialog = Dialog<ButtonType>().apply {
            title = "Synchronisation"
            headerText = "Server auswählen oder diesen Rechner als Server nutzen"
            dialogPane.content = content

            // WICHTIG: ButtonBar komplett deaktivieren, sonst mischt der Skin wieder rein.
            dialogPane.buttonTypes.clear()
            dialogPane.padding = Insets(0.0)
        }

        dialog.setOnShown {
            // Native ButtonBar ausblenden (falls sie noch Platz reserviert)
            val nativeButtonBar = dialog.dialogPane.lookup(".button-bar")
            nativeButtonBar?.isManaged = false
            nativeButtonBar?.isVisible = false

            // X schließen: am echten Window (Stage) hängen
            val window = dialog.dialogPane.scene.window
            window.setOnCloseRequest {
                dialog.setResult(ButtonType.CANCEL)
                // wichtig: Dialog wirklich schließen
                dialog.close()
            }
        }

        themeManager.register(dialog)

        fun validateAndSave(): Boolean {
            val port = portField.text.trim().toIntOrNull()
            if (port == null || port <= 0 || port > 65535) {
                Dialogs.error("Synchronisation", "Ungültiger Port: '${portField.text}'")
                return false
            }

            val newCfg = cfg.copy(
                enabled = enabled.isSelected,
                serverMode = serverMode.isSelected,
                host = hostField.text.trim().ifBlank { "localhost" },
                port = port
            )
            syncService.saveConfig(newCfg)
            return true
        }

        btnFindServer.setOnAction {
            btnFindServer.isDisable = true
            val oldText = btnFindServer.text
            btnFindServer.text = "Suche..."

            Thread {
                try {
                    val servers = discoveryClient.discover(timeoutMs = 900)
                    Platform.runLater {
                        if (servers.isEmpty()) {
                            Dialogs.info(
                                "Server finden",
                                "Kein Server im Netzwerk gefunden.\n" +
                                        "Stelle sicher, dass der Server-Modus aktiv ist und ihr im gleichen WLAN/LAN seid."
                            )
                        } else {
                            val s = servers.first()
                            hostField.text = s.host
                            portField.text = s.httpPort.toString()
                            Dialogs.info("Server gefunden", "Gefunden: ${s.name}\n${s.host}:${s.httpPort}")
                        }
                    }
                } catch (t: Throwable) {
                    Platform.runLater {
                        Dialogs.error("Server finden", "Fehler bei der Suche: ${t.message}")
                    }
                } finally {
                    Platform.runLater {
                        btnFindServer.isDisable = false
                        btnFindServer.text = oldText
                    }
                }
            }.start()
        }

        btnApply.setOnAction {
            if (validateAndSave()) {
                dialog.setResult(ButtonType.APPLY)
                dialog.close()
            }
        }

        btnCancel.setOnAction {
            dialog.setResult(ButtonType.CANCEL)
            dialog.close()
        }

        btnSyncNow.setOnAction {
            if (validateAndSave()) {
                dialog.setResult(ButtonType.OK)
                dialog.close()
            }
        }

        val result = dialog.showAndWait()
        if (result.isEmpty) return

        when (result.get()) {
            ButtonType.OK -> doSyncNow()
            // APPLY speichert nur, CANCEL macht nichts
            else -> {}
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
                    if (!discoveryServer.isRunning()) {
                        discoveryServer.start()
                    }
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
            // 0) NOTEBOOKS zuerst (sonst "verschwinden" Notes im Tree)
            val nbBody = syncClient.pullNotebooks(cfg.host, cfg.port)
            val remoteNbs = NotebookWire.decodeLines(nbBody)

            var nbApplied = 0
            for (remote in remoteNbs) {
                val local = notebookRepo.findById(remote.id)
                val shouldApply =
                    local == null ||
                            local.name != remote.name ||
                            local.parentId != remote.parentId ||
                            local.trashedAt != remote.trashedAt

                if (shouldApply) {
                    notebookRepo.save(remote)
                    nbApplied++
                }
            }

            // Optional: Notebooks auch pushen (Full Sync)
            val localNbs = notebookRepo.findAll()
            val nbPushBody = NotebookWire.encodeLines(localNbs)
            val nbPushResult = syncClient.pushNotebooks(cfg.host, cfg.port, nbPushBody)

            // 1) PULL
            val pulledBody = syncClient.pullNotes(cfg.host, cfg.port, after = null)
            val pulledNotes = NoteWire.decodeLines(pulledBody)

            var pulledApplied = 0
            for (remote in pulledNotes) {
                val local = noteRepo.findById(remote.id)
                val shouldApply =
                    local == null ||
                            remote.updatedAt.isAfter(local.updatedAt) ||
                            (remote.updatedAt == local.updatedAt &&
                                    (remote.title != local.title ||
                                            remote.content != local.content ||
                                            remote.trashedAt != local.trashedAt))

                if (shouldApply) {
                    noteRepo.save(remote)
                    pulledApplied++
                }
            }

            // 2) PUSH (alle lokalen Notes)
            val localChanged = noteRepo.findAll()
            val pushBody = NoteWire.encodeLines(localChanged)
            val pushResult = syncClient.pushNotes(cfg.host, cfg.port, pushBody)

            // 4) UI refresh
            notebookTreePresenter.refresh()
            trashPresenter.refresh()

            Dialogs.info(
                "Synchronisation",
                "Notebooks Pull: erhalten=${remoteNbs.size}, übernommen=$nbApplied\n" +
                        "Notebooks Push: gesendet=${localNbs.size}, Server: $nbPushResult\n\n" +
                        "Notes Pull: erhalten=${pulledNotes.size}, übernommen=$pulledApplied\n" +
                        "Notes Push: gesendet=${localChanged.size}, Server: $pushResult"
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
                if (!discoveryServer.isRunning()) {
                    discoveryServer.start()
                }
            } catch (t: Throwable) {
                Dialogs.error("Synchronisation", "Server konnte nicht gestartet werden: ${t.message}")
            }
        }
    }

    fun close() {
        discoveryServer.stop()
        localSyncServer.stop()
        db.close()
    }
}
