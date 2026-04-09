package de.crossnote.ui.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.crossnote.ui.android.data.DragDropState
import de.crossnote.ui.android.data.Screen
import de.crossnote.ui.android.data.rememberDragDropState
import de.crossnote.ui.android.ui.components.*
import de.crossnote.ui.android.ui.screens.*
import de.crossnote.ui.android.ui.theme.CrossNoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: NotesViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            CrossNoteTheme(darkTheme = isDarkMode) {
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: NotesViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val trashedNotes by viewModel.trashedNotes.collectAsState()
    val notebookTree by viewModel.notebookTree.collectAsState()
    val trashedNotebookTree by viewModel.trashedNotebookTree.collectAsState()
    val currentNote by viewModel.currentNote.collectAsState()
    val revisions by viewModel.revisions.collectAsState()
    val previewRevision by viewModel.previewRevision.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val expandedIds by viewModel.expandedNotebookIds.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val dragDropState = rememberDragDropState()

    var showAddNotebookDialog by remember { mutableStateOf(false) }
    var parentForNewNotebook by remember { mutableStateOf<String?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    label = { Text("All Notes") },
                    selected = currentScreen == Screen.Notes,
                    onClick = {
                        viewModel.setScreen(Screen.Notes)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Notebooks") },
                    selected = currentScreen == Screen.Notebooks,
                    onClick = {
                        viewModel.setScreen(Screen.Notebooks)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    label = { Text("Trash") },
                    selected = currentScreen == Screen.Trash,
                    onClick = {
                        viewModel.setScreen(Screen.Trash)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = currentScreen == Screen.Settings,
                    onClick = {
                        viewModel.setScreen(Screen.Settings)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                currentNote != null -> if (currentNote?.id?.value == "temp-new") "New Note" else "Edit Note"
                                currentScreen == Screen.Notes -> "All Notes"
                                currentScreen == Screen.Notebooks -> "Notebooks"
                                currentScreen == Screen.Trash -> "Trash"
                                currentScreen == Screen.Settings -> "Settings"
                                else -> "CrossNote"
                            }
                        )
                    },
                    navigationIcon = {
                        if (currentNote != null) {
                            IconButton(onClick = { viewModel.closeNote() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    actions = {
                        if (currentNote != null && currentNote?.id?.value != "temp-new") {
                            var showMoveDialog by remember { mutableStateOf(false) }
                            
                            IconButton(onClick = { showMoveDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to Notebook")
                            }
                            
                            if (!currentNote!!.isTrashed()) {
                                IconButton(onClick = { viewModel.moveNoteToTrash(currentNote!!.id.value) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                            
                            if (showMoveDialog) {
                                MoveItemDialog(
                                    title = "Move Note",
                                    tree = notebookTree,
                                    onMove = { targetId ->
                                        viewModel.moveNote(currentNote!!.id.value, targetId)
                                        showMoveDialog = false
                                    },
                                    onDismiss = { showMoveDialog = false }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (currentNote == null) {
                    when (currentScreen) {
                        Screen.Notes -> {
                            FloatingActionButton(onClick = { viewModel.startNewNote() }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Note")
                            }
                        }
                        Screen.Notebooks -> {
                            FloatingActionButton(onClick = { 
                                parentForNewNotebook = null
                                showAddNotebookDialog = true 
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Notebook")
                            }
                        }
                        else -> {}
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when {
                    currentNote != null -> {
                        NoteEditor(
                            noteId = currentNote!!.id.value,
                            title = currentNote!!.title,
                            content = currentNote!!.content,
                            isTrashed = currentNote!!.isTrashed(),
                            revisions = revisions,
                            previewRevision = previewRevision,
                            onClose = { t, c -> viewModel.updateNote(currentNote!!.id.value, t, c) },
                            onAutoSave = { t, c -> viewModel.persistNote(currentNote!!.id.value, t, c) },
                            onSelectRevision = { viewModel.selectRevisionForPreview(it) },
                            onClearPreview = { viewModel.clearPreview() },
                            onRestoreRevision = { viewModel.restoreRevision(currentNote!!.id.value, it) }
                        )
                    }
                    currentScreen == Screen.Notes -> {
                        NoteList(notes = notes, onNoteClick = { viewModel.selectNote(it.id) })
                    }
                    currentScreen == Screen.Trash -> {
                        TrashList(
                            notes = trashedNotes, 
                            notebookTree = trashedNotebookTree,
                            expandedIds = expandedIds,
                            onToggleExpand = { viewModel.toggleNotebookExpanded(it) },
                            onNoteClick = { viewModel.selectNote(it.id) },
                            onRestore = { viewModel.restoreNote(it.id) },
                            onPurge = { viewModel.purgeNote(it.id) },
                            onRestoreNotebook = { viewModel.restoreNotebook(it) },
                            onPurgeNotebook = { viewModel.purgeNotebook(it) }
                        )
                    }
                    currentScreen == Screen.Notebooks -> {
                        var renamingNotebook by remember { mutableStateOf<Pair<String, String>?>(null) }

                        NotebookTree(
                            tree = notebookTree,
                            dragDropState = dragDropState,
                            expandedIds = expandedIds,
                            onToggleExpand = { viewModel.toggleNotebookExpanded(it) },
                            onNoteClick = { viewModel.selectNote(it.id) },
                            onAddNoteToNotebook = { viewModel.startNewNote(it) },
                            onAddSubNotebook = { 
                                parentForNewNotebook = it
                                showAddNotebookDialog = true 
                            },
                            onRenameNotebook = { id, name -> renamingNotebook = id to name },
                            onDeleteNotebook = { viewModel.deleteNotebook(it) },
                            onMoveNotebook = { id, target -> viewModel.moveNotebook(id, target) },
                            onMoveNote = { id, target -> viewModel.moveNote(id, target) }
                        )

                        renamingNotebook?.let { (id, oldName) ->
                            var name by remember { mutableStateOf(oldName) }
                            AlertDialog(
                                onDismissRequest = { renamingNotebook = null },
                                title = { Text("Rename Notebook") },
                                text = {
                                    TextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("Name") }
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = { viewModel.renameNotebook(id, name); renamingNotebook = null }, enabled = name.isNotBlank()) {
                                        Text("Save")
                                    }
                                },
                                dismissButton = { TextButton(onClick = { renamingNotebook = null }) { Text("Cancel") } }
                            )
                        }
                    }
                    currentScreen == Screen.Settings -> {
                        SettingsScreen(
                            isSyncing = isSyncing,
                            onSync = { h, p -> viewModel.syncWithServer(h, p) },
                            isDarkMode = isDarkMode,
                            onDarkModeChange = { viewModel.setDarkMode(it) }
                        )
                    }
                }
            }
        }
    }

    if (showAddNotebookDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddNotebookDialog = false },
            title = { Text("New Notebook") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNotebook(name, parentForNewNotebook)
                        showAddNotebookDialog = false
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNotebookDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}
