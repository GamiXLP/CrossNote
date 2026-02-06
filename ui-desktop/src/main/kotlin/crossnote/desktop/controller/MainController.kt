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

    fun close() {
        db.close()
    }
}
