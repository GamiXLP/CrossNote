package crossnote.desktop.controller

import crossnote.app.note.NoteAppService
import crossnote.app.sync.SyncService
import crossnote.desktop.I18n
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
import crossnote.desktop.sync.LanServerScanner
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
import javafx.event.EventHandler
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
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TextInputControl
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.input.Clipboard
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import java.nio.file.Paths
import javafx.scene.control.TextField as FxTextField

class MainController {

    // ---------- Persistence / Service ----------
    private val db = SqliteDatabase(Paths.get(System.getProperty("user.home"), ".crossnote", "crossnote.db"))
    private val settingsRepo = SqliteSettingsRepository(db)
    private val notebookRepo = SqliteNotebookRepository(db)
    private val noteRepo = SqliteNoteRepository(db)

    private lateinit var i18n: I18n

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

    // --------- LAN Discovery Fallback (Subnet Scan) ----------
    private val lanScanner = LanServerScanner()

    private val localSyncServer: LocalSyncServer by lazy {
        LocalSyncServer(
            notebookRepo = notebookRepo,
            noteRepo = noteRepo,
            onDataChanged = {
                Platform.runLater {
                    if (::notebookTreePresenter.isInitialized) notebookTreePresenter.refresh()
                    if (::trashPresenter.isInitialized) trashPresenter.refresh()
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
    @FXML lateinit var BTNlanguage: Button
    @FXML lateinit var BTNsave: Button
    @FXML lateinit var BTNsync: Button
    @FXML lateinit var BTNemptyTrash: Button

    // ---------- I18n labels (from FXML) ----------
    @FXML lateinit var LBnotesTitle: Label
    @FXML lateinit var LBtrashTitle: Label
    @FXML lateinit var LBsavestatesTitle: Label
    @FXML lateinit var LBfilenameTitle: Label
    @FXML lateinit var LBlastChangedStatic: Label

    // ---------- Language icon nodes ----------
    @FXML lateinit var LANG_DE: StackPane
    @FXML lateinit var LANG_EN: StackPane

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

    // i18n-dependent menu
    private var notebookRootMenu: ContextMenu? = null

    // =========================================================
    // Lifecycle
    // =========================================================
    @FXML
    fun initialize() {
        service.purgeTrashedOlderThan(30)

        initUiState()
        setupThemeAndI18n()
        initEditor()

        setupTrashTree()
        setupSavestates()
        setupNotebookPresenter()

        setupNotebookActions()
        setupNotebookTreeUi()

        rebuildEditorContextMenus()
        setupButtons()

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
            i18n = i18n,
            service = service,
            titleField = titleField,
            contentArea = contentArea,
            lastChangeLabel = LBlastchange,
            savedLabel = LBsaved,
            titleCountLabel = LBtitleCount,
            trashCountdownText = { id -> trashPresenter.trashCountdownText(id) },
            onAfterSaveOrDelete = { notebookTreePresenter.refresh() }
        )

        // ✅ wichtig: nach Konstruktion einmal i18n-Texte sauber setzen
        editor.applyI18nTexts()
    }

    private fun applyInitialUiState() {
        LBsaved.text = i18n.t("editor.saved.notSaved")
        LBlastchange.text = i18n.t("editor.saved.noDate")
        LBdataname.text = i18n.t("editor.saved.noNoteSelected")

        notebookTreePresenter.refresh()
        uiState.applyCenterMode()
    }

    // =========================================================
    // Setup
    // =========================================================
    private fun setupThemeAndI18n() {
        i18n = I18n(settingsRepo)

        themeManager = ThemeManager(settingsRepo, BTNdarkmode, i18n)
        themeManager.bindToSceneRoot()
        Dialogs.init(themeManager)

        applyI18n()
        updateLanguageIcon()
    }

    private fun applyI18n() {
        // Button text optional – wenn du nur Flagge willst, kannst du hier auch "" setzen
        BTNlanguage.text = ""
        BTNlanguage.contentDisplay = javafx.scene.control.ContentDisplay.GRAPHIC_ONLY

        LBnotesTitle.text = i18n.t("sidebar.notes")
        LBtrashTitle.text = i18n.t("sidebar.trash")
        LBsavestatesTitle.text = i18n.t("sidebar.savestates")
        LBfilenameTitle.text = i18n.t("sidebar.filename")

        TFnotebook.promptText = i18n.t("search.placeholder")
        TFtrashcan.promptText = i18n.t("search.placeholder")
        titleField.promptText = i18n.t("editor.title.placeholder")
        contentArea.promptText = i18n.t("editor.content.placeholder")
        LBlastChangedStatic.text = i18n.t("editor.lastChangedLabel")

        rebuildNotebookRootMenu()
        rebuildEditorContextMenus()
    }

    private fun setupTrashTree() {
        trashPresenter = TrashPresenter(
            service = service,
            notebookRepo = notebookRepo,
            themeManager = themeManager,
            trashTree = TVtrashcan,
            searchField = TFtrashcan,
            trashRoot = trashRoot,
            onOpenNoteInTrash = { id -> editor.openNote(id, true) },
            onTrashNonNoteSelected = {
                editor.resetEditor()
                LBsaved.text = i18n.t("editor.saved.trash")
                LBlastchange.text = i18n.t("editor.saved.noDate")
            },
            onRefreshNotebooks = { notebookTreePresenter.refresh() }
        )
        trashPresenter.init()

        TVtrashcan.setCellFactory {
            object : TreeCell<TrashNode>() {

                private var expandedListener: javafx.beans.value.ChangeListener<Boolean>? = null
                private var listenedTreeItem: TreeItem<TrashNode>? = null

                override fun updateItem(item: TrashNode?, empty: Boolean) {
                    expandedListener?.let { l -> listenedTreeItem?.expandedProperty()?.removeListener(l) }
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
                                icon.setOnMouseClicked { e ->
                                    ti.isExpanded = !ti.isExpanded
                                    e.consume()
                                }
                                icon.opacity = 0.70
                                graphic = icon
                            }

                            updateIcon()

                            val l = javafx.beans.value.ChangeListener<Boolean> { _, _, _ -> updateIcon() }
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
        TVtrashcan.selectionModel.selectionMode = javafx.scene.control.SelectionMode.MULTIPLE

        TVtrashcan.addEventFilter(KeyEvent.KEY_PRESSED) { e ->
            if (e.code != KeyCode.DELETE) return@addEventFilter

            val selectedNodes = TVtrashcan.selectionModel.selectedItems
                .mapNotNull { it.value }
                .filter { it !is TrashNode.Root }

            if (selectedNodes.isEmpty()) return@addEventFilter

            val folders = selectedNodes.filterIsInstance<TrashNode.FolderBranch>()
            val notes = selectedNodes.filterIsInstance<TrashNode.NoteLeaf>()

            if (folders.isNotEmpty()) {
                val ok = Dialogs.confirm(
                    title = i18n.t("trash.deletePermanent.title"),
                    header = if (folders.size == 1)
                        "Ordner '${folders[0].name}' endgültig löschen?"
                    else
                        "${folders.size} Ordner endgültig löschen?",
                    content = i18n.t("trash.deletePermanent.contentFolders")
                )
                if (!ok) {
                    e.consume()
                    return@addEventFilter
                }

                folders.forEach { f -> trashPresenter.purgeTrashedNotebookRecursively(f.notebookId) }

                TVtrashcan.selectionModel.clearSelection()
                e.consume()
                return@addEventFilter
            }

            if (notes.isNotEmpty()) {
                val ok = Dialogs.confirm(
                    title = i18n.t("trash.deletePermanent.title"),
                    header = if (notes.size == 1)
                        "Notiz '${notes[0].title}' endgültig löschen?"
                    else
                        "${notes.size} Notizen endgültig löschen?",
                    content = i18n.t("trash.deletePermanent.contentNotes")
                )
                if (!ok) {
                    e.consume()
                    return@addEventFilter
                }

                notes.forEach { n -> trashPresenter.purgeTrashedNote(n.noteId) }

                TVtrashcan.selectionModel.clearSelection()
                e.consume()
            }
        }

        TVtrashcan.addEventFilter(KeyEvent.KEY_PRESSED) { e ->
            if (e.code == KeyCode.ESCAPE) {
                TVtrashcan.selectionModel.clearSelection()
                e.consume()
                return@addEventFilter
            }

            if (e.code == KeyCode.A && e.isControlDown) {
                TVtrashcan.selectionModel.selectAll()
                e.consume()
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

                LBsaved.text = i18n.t("editor.saved.revisionRestored")
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
            i18n = i18n,
            service = service,
            notebookRepo = notebookRepo,
            noteRepo = noteRepo,
            themeManager = themeManager,
            onAfterChange = { notebookTreePresenter.refresh() },
            onAfterTrashChange = { trashPresenter.refresh() },
            onClearSelection = { clearEditorAndSelections() },
            onSelectedNotebookChanged = { editor.selectedNotebookId = it }
        )
    }

    private fun setupNotebookTreeUi() {
        TVnotebook.selectionModel.selectionMode = javafx.scene.control.SelectionMode.MULTIPLE

        TVnotebook.addEventFilter(KeyEvent.KEY_PRESSED) { e ->
            if (e.code == KeyCode.ESCAPE) {
                TVnotebook.selectionModel.clearSelection()
                e.consume()
                return@addEventFilter
            }

            if (e.code == KeyCode.A && e.isControlDown) {
                TVnotebook.selectionModel.selectAll()
                e.consume()
            }
        }

        TVnotebook.setCellFactory {
            NotebookTreeCell(
                i18n = i18n,
                service = service,
                notebookRepo = notebookRepo,
                themeManager = themeManager,
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
                onTrashNotebookRecursively = { nbId -> notebookActions.trashNotebookRecursively(nbId) },
                onRenameNotebook = { nbId, currentName ->
                    notebookActions.renameNotebookDialog(nbId, currentName)
                }
            )
        }

        rebuildNotebookRootMenu()
    }

    private fun rebuildNotebookRootMenu() {
        val rootMenu = ContextMenu(
            MenuItem(i18n.t("menu.createFolder")).apply {
                setOnAction { notebookActions.createNotebookDialog(null) }
            },
            MenuItem(i18n.t("menu.createRootNote")).apply {
                setOnAction {
                    editor.startNewNote(null)
                    uiState.applyCenterMode()
                }
            }
        )

        themeManager.register(rootMenu)
        notebookRootMenu = rootMenu
        TVnotebook.contextMenu = rootMenu
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
        BTNsync.setOnAction { openSyncSettingsDialog() }

        BTNemptyTrash.setOnAction {
            trashPresenter.purgeAllPermanently()
            clearEditorAndSelections()
        }
    }

    // =========================================================
    // Actions
    // =========================================================
    @FXML
    fun darkmode_on(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        themeManager.toggle()
    }

    @FXML
    fun language_on(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        i18n.toggleLang()
        applyI18n()

        // ✅ wichtig: Editortexte (Counter + "Not saved" etc.) neu ziehen
        if (::editor.isInitialized) editor.applyI18nTexts()

        updateLanguageIcon()
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

    // =========================================================
    // Context menus (Title + Content)
    // =========================================================
    private fun rebuildEditorContextMenus() {

        fun attachMenu(control: TextInputControl) {
            val undo = MenuItem(i18n.t("ctx.undo")).apply { setOnAction { control.undo() } }
            val redo = MenuItem(i18n.t("ctx.redo")).apply { setOnAction { control.redo() } }

            val cut = MenuItem(i18n.t("ctx.cut")).apply { setOnAction { control.cut() } }
            val copy = MenuItem(i18n.t("ctx.copy")).apply { setOnAction { control.copy() } }
            val paste = MenuItem(i18n.t("ctx.paste")).apply { setOnAction { control.paste() } }
            val del = MenuItem(i18n.t("ctx.delete")).apply { setOnAction { control.replaceSelection("") } }
            val all = MenuItem(i18n.t("ctx.selectAll")).apply { setOnAction { control.selectAll() } }

            val menu = ContextMenu(
                undo, redo,
                SeparatorMenuItem(),
                cut, copy, paste, del,
                SeparatorMenuItem(),
                all
            )

            themeManager.register(menu)

            val prev = menu.onShowing
            menu.onShowing = EventHandler { evt ->
                prev?.handle(evt)

                val hasSel = control.selection.length > 0
                val editable = control.isEditable
                val clipboardHas = Clipboard.getSystemClipboard().hasString()
                val hasText = !control.text.isNullOrEmpty()

                cut.isDisable = !editable || !hasSel
                copy.isDisable = !hasSel
                paste.isDisable = !editable || !clipboardHas
                del.isDisable = !editable || !hasSel
                all.isDisable = !hasText

                undo.isDisable = !editable
                redo.isDisable = !editable
            }

            control.contextMenu = menu
        }

        attachMenu(titleField)
        attachMenu(contentArea)
    }

    // =========================================================
    // Savestates
    // =========================================================
    private fun openSavestatesFor(noteId: NoteId) {
        uiState.showSavestates()
        clearEditorAndSelections()
        savestatePresenter.showFor(noteId)
    }

    // =========================================================
    // Sync Settings Dialog
    // =========================================================
    private fun openSyncSettingsDialog() {
        val cfg = syncService.loadConfig()

        val enabled = CheckBox(i18n.t("sync.enabled")).apply { isSelected = cfg.enabled }
        val serverMode = CheckBox(i18n.t("sync.serverMode")).apply { isSelected = cfg.serverMode }

        val hostField = FxTextField(cfg.host)
        val portField = FxTextField(cfg.port.toString())

        fun applyServerModeUi() {
            val isServer = serverMode.isSelected
            hostField.isDisable = isServer
            hostField.opacity = if (isServer) 0.6 else 1.0
        }

        serverMode.selectedProperty().addListener { _, _, _ -> applyServerModeUi() }
        applyServerModeUi()

        val grid = GridPane().apply {
            hgap = 10.0
            vgap = 10.0
            padding = Insets(10.0)

            add(enabled, 0, 0, 2, 1)
            add(serverMode, 0, 1, 2, 1)

            add(Label(i18n.t("sync.serverHost")), 0, 2)
            add(hostField, 1, 2)

            add(Label(i18n.t("sync.port")), 0, 3)
            add(portField, 1, 3)
        }

        val btnFindServer = Button(i18n.t("sync.findServer"))
        val btnApply = Button(i18n.t("sync.apply"))
        val btnCancel = Button(i18n.t("sync.cancel"))
        val btnSyncNow = Button(i18n.t("sync.syncNow"))

        btnSyncNow.isDefaultButton = true
        btnCancel.isCancelButton = true

        val footer = HBox(12.0, btnFindServer, btnApply, btnCancel, btnSyncNow).apply {
            alignment = Pos.CENTER
            padding = Insets(12.0, 16.0, 0.0, 16.0)
            children.forEach { node ->
                if (node is Button) {
                    node.minWidth = 0.0
                    node.maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(node, Priority.ALWAYS)
                }
            }
        }

        val content = javafx.scene.layout.VBox(10.0, grid, footer).apply { padding = Insets(0.0) }

        val dialog = Dialog<ButtonType>().apply {
            title = i18n.t("sync.title")
            headerText = i18n.t("sync.header")
            dialogPane.content = content
            dialogPane.buttonTypes.clear()
            dialogPane.padding = Insets(0.0)
        }

        dialog.setOnShown {
            val nativeButtonBar = dialog.dialogPane.lookup(".button-bar")
            nativeButtonBar?.isManaged = false
            nativeButtonBar?.isVisible = false

            val window = dialog.dialogPane.scene.window
            window.setOnCloseRequest {
                dialog.setResult(ButtonType.CANCEL)
                dialog.close()
            }
        }

        themeManager.register(dialog)

        fun validateAndSave(): Boolean {
            val port = portField.text.trim().toIntOrNull()
            if (port == null || port <= 0 || port > 65535) {
                Dialogs.error(i18n.t("sync.title"), i18n.t("sync.invalidPort", portField.text))
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
            val port = portField.text.trim().toIntOrNull()
            if (port == null || port <= 0 || port > 65535) {
                Dialogs.error(i18n.t("sync.serverFind"), i18n.t("sync.findServer.invalidPort", portField.text))
                return@setOnAction
            }

            btnFindServer.isDisable = true
            val oldText = btnFindServer.text
            btnFindServer.text = i18n.t("sync.findServer.searching")

            Thread {
                try {
                    val udpFound = discoveryClient.discover(timeoutMs = 900)
                    if (udpFound.isNotEmpty()) {
                        val s = udpFound.first()
                        Platform.runLater {
                            hostField.text = s.host
                            portField.text = s.httpPort.toString()
                            Dialogs.info(i18n.t("sync.serverFound"), "Found (UDP): ${s.name}\n${s.host}:${s.httpPort}")
                        }
                        return@Thread
                    }

                    val scanned = lanScanner.scanForServers(
                        port = port,
                        totalTimeoutMs = 2500,
                        perHostTimeoutMs = 250,
                        maxConcurrency = 64
                    )

                    Platform.runLater {
                        if (scanned.isEmpty()) {
                            Dialogs.info(i18n.t("sync.serverFind"), i18n.t("sync.serverFind.none"))
                        } else {
                            val s = scanned.first()
                            hostField.text = s.host
                            portField.text = s.httpPort.toString()
                            Dialogs.info(i18n.t("sync.serverFound"), "Found (Scan): ${s.name}\n${s.host}:${s.httpPort}")
                        }
                    }
                } catch (t: Throwable) {
                    Platform.runLater {
                        Dialogs.error(i18n.t("sync.serverFind"), i18n.t("sync.findError", t.message ?: ""))
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
            else -> {}
        }
    }

    private fun doSyncNow() {
        val cfg = syncService.loadConfig()
        if (!cfg.enabled) {
            Dialogs.info(i18n.t("sync.title"), i18n.t("sync.disabled"))
            return
        }

        if (cfg.serverMode) {
            if (!localSyncServer.isRunning()) {
                try {
                    localSyncServer.start(cfg.port)
                    if (!discoveryServer.isRunning()) discoveryServer.start()
                } catch (t: Throwable) {
                    Dialogs.error(i18n.t("sync.title"), i18n.t("sync.serverStartFail", t.message ?: ""))
                    return
                }
            }
            Dialogs.info(i18n.t("sync.title"), i18n.t("sync.serverModeActive", cfg.port))
            return
        }

        try {
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

            val localNbs = notebookRepo.findAll()
            val nbPushBody = NotebookWire.encodeLines(localNbs)
            val nbPushResult = syncClient.pushNotebooks(cfg.host, cfg.port, nbPushBody)

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

            val localChanged = noteRepo.findAll()
            val pushBody = NoteWire.encodeLines(localChanged)
            val pushResult = syncClient.pushNotes(cfg.host, cfg.port, pushBody)

            notebookTreePresenter.refresh()
            trashPresenter.refresh()

            Dialogs.info(
                i18n.t("sync.title"),
                "Notebooks Pull: received=${remoteNbs.size}, applied=$nbApplied\n" +
                        "Notebooks Push: sent=${localNbs.size}, Server: $nbPushResult\n\n" +
                        "Notes Pull: received=${pulledNotes.size}, applied=$pulledApplied\n" +
                        "Notes Push: sent=${localChanged.size}, Server: $pushResult"
            )
        } catch (t: Throwable) {
            Dialogs.error(i18n.t("sync.title"), i18n.t("sync.failed", t.message ?: ""))
        }
    }

    private fun startServerIfEnabled() {
        val cfg = syncService.loadConfig()
        if (cfg.enabled && cfg.serverMode) {
            try {
                localSyncServer.start(cfg.port)
                if (!discoveryServer.isRunning()) discoveryServer.start()
            } catch (t: Throwable) {
                Dialogs.error(i18n.t("sync.title"), i18n.t("sync.serverStartFail", t.message ?: ""))
            }
        }
    }

    private fun updateLanguageIcon() {
        val isDe = i18n.currentLang().equals("de", ignoreCase = true)

        LANG_DE.isVisible = isDe
        LANG_DE.isManaged = isDe

        LANG_EN.isVisible = !isDe
        LANG_EN.isManaged = !isDe
    }

    fun close() {
        discoveryServer.stop()
        localSyncServer.stop()
        db.close()
    }
}
