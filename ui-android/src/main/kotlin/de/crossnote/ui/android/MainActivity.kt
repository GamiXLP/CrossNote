package de.crossnote.ui.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
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
            CrossNoteTheme {
                val notes by viewModel.notes.collectAsState()
                val trashedNotes by viewModel.trashedNotes.collectAsState()
                val notebookTree by viewModel.notebookTree.collectAsState()
                val trashedNotebookTree by viewModel.trashedNotebookTree.collectAsState()
                val currentNote by viewModel.currentNote.collectAsState()
                val currentScreen by viewModel.currentScreen.collectAsState()
                val revisions by viewModel.revisions.collectAsState()
                val errorMessage by viewModel.errorMessage.collectAsState()
                val expandedIds by viewModel.expandedNotebookIds.collectAsState()
                val isSyncing by viewModel.isSyncing.collectAsState()
                
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val dragDropState = rememberDragDropState()
                val density = LocalDensity.current

                var showAddNotebookDialog by remember { mutableStateOf(false) }
                var parentForNewNotebook by remember { mutableStateOf<String?>(null) }
                var renamingNotebook by remember { mutableStateOf<Pair<String, String>?>(null) }

                LaunchedEffect(errorMessage) {
                    errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearError()
                    }
                }

                LaunchedEffect(currentScreen) {
                    dragDropState.dropTargets.clear()
                    dragDropState.dragSources.clear()
                    dragDropState.listBounds = null
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Column(modifier = Modifier.fillMaxHeight()) {
                                Spacer(Modifier.height(12.dp))
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.Default.Notes, contentDescription = null) },
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
                                
                                Spacer(Modifier.weight(1f))
                                
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
                    }
                ) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            dragDropState.containerWindowPos = it.positionInWindow()
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val touchPos = offset + dragDropState.containerWindowPos
                                    val sourceEntry = dragDropState.dragSources.values
                                        .filter { it.bounds.contains(touchPos) }
                                        .minByOrNull { it.bounds.width * it.bounds.height }
                                    
                                    if (sourceEntry != null) {
                                        val info = sourceEntry.info
                                        dragDropState.draggedItemId = info.id
                                        dragDropState.draggedItemType = info.type
                                        dragDropState.draggedItemName = info.name
                                        dragDropState.onDropAction = info.onDrop
                                        dragDropState.dragOffset = Offset.Zero
                                        dragDropState.dragStartWindowPos = sourceEntry.bounds.topLeft
                                        dragDropState.touchOffsetInItem = touchPos - sourceEntry.bounds.topLeft
                                        
                                        // Set visual offset for the icon (slightly left and up)
                                        dragDropState.visualOffset = Offset(
                                            with(density) { -40.dp.toPx() },
                                            with(density) { -60.dp.toPx() }
                                        )
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragDropState.draggedItemId != null) {
                                        dragDropState.dragOffset += dragAmount
                                    }
                                },
                                onDragEnd = {
                                    if (dragDropState.draggedItemId != null) {
                                        // Calculate target based on visual icon position, not finger position
                                        val visualPos = dragDropState.getVisualWindowPosition()
                                        val targetId = dragDropState.findTargetId(visualPos)
                                        
                                        if (targetId != dragDropState.draggedItemId) {
                                            dragDropState.onDropAction(targetId)
                                        }
                                    }
                                    dragDropState.draggedItemId = null
                                },
                                onDragCancel = {
                                    dragDropState.draggedItemId = null
                                }
                            )
                        }
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                TopAppBar(
                                    title = { 
                                        Text(
                                            when {
                                                currentNote != null -> {
                                                    if (currentNote!!.id.value == "temp-new") "New Note" 
                                                    else currentNote!!.title.ifBlank { "Untitled Note" }
                                                }
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
                                        if (currentNote != null && !currentNote!!.isTrashed() && currentNote!!.id.value != "temp-new") {
                                            var showDeleteConfirm by remember { mutableStateOf(false) }
                                            var showMoveDialog by remember { mutableStateOf(false) }
                                            
                                            IconButton(onClick = { showMoveDialog = true }) {
                                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move Note")
                                            }
                                            IconButton(onClick = { showDeleteConfirm = true }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Move to Trash")
                                            }
                                            
                                            if (showDeleteConfirm) {
                                                ConfirmDialog(
                                                    title = "Delete Note",
                                                    text = "Move this note to trash?",
                                                    onConfirm = { 
                                                        viewModel.moveNoteToTrash(currentNote!!.id.value)
                                                        showDeleteConfirm = false
                                                    },
                                                    onDismiss = { showDeleteConfirm = false }
                                                )
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
                                            onSave = { t, c -> viewModel.updateNote(currentNote!!.id.value, t, c) },
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
                                        NotebookTree(
                                            tree = notebookTree, 
                                            dragDropState = dragDropState,
                                            expandedIds = expandedIds,
                                            onToggleExpand = { viewModel.toggleNotebookExpanded(it) },
                                            onNoteClick = { viewModel.selectNote(it.id) },
                                            onAddNoteToNotebook = { viewModel.startNewNote(it) },
                                            onAddSubNotebook = { parentId ->
                                                parentForNewNotebook = parentId
                                                showAddNotebookDialog = true 
                                            },
                                            onRenameNotebook = { id, name -> renamingNotebook = id to name },
                                            onDeleteNotebook = { viewModel.deleteNotebook(it) },
                                            onMoveNotebook = { id, target -> viewModel.moveNotebook(id, target) },
                                            onMoveNote = { id, target -> viewModel.moveNote(id, target) }
                                        )
                                    }
                                    currentScreen == Screen.Settings -> {
                                        SettingsScreen(
                                            isSyncing = isSyncing,
                                            onSync = { h, p -> viewModel.syncWithServer(h, p) }
                                        )
                                    }
                                }
                            }
                        }

                        // Dragging Overlay
                        if (dragDropState.draggedItemId != null) {
                            val touchLocal = dragDropState.getLocalDragPosition() + dragDropState.touchOffsetInItem
                            Surface(
                                modifier = Modifier
                                    .graphicsLayer {
                                        // Use the visual offset for positioning the icon
                                        translationX = touchLocal.x + dragDropState.visualOffset.x
                                        translationY = touchLocal.y + dragDropState.visualOffset.y
                                        scaleX = 1.1f
                                        scaleY = 1.1f
                                        alpha = 0.8f
                                    },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 8.dp
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (dragDropState.draggedItemType == DragDropState.ItemType.NOTE) Icons.AutoMirrored.Filled.InsertDriveFile else Icons.Default.Folder,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(dragDropState.draggedItemName)
                                }
                            }
                        }
                    }

                    if (showAddNotebookDialog) {
                        var name by remember { mutableStateOf("") }
                        val focusRequester = remember { FocusRequester() }

                        AlertDialog(
                            onDismissRequest = { showAddNotebookDialog = false },
                            title = { Text(if (parentForNewNotebook == null) "New Root Notebook" else "New Sub-Notebook") },
                            text = {
                                TextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Name") },
                                    modifier = Modifier.focusRequester(focusRequester)
                                )
                            },
                            confirmButton = {
                                Button(onClick = { 
                                    viewModel.createNotebook(name, parentForNewNotebook)
                                    showAddNotebookDialog = false 
                                }, enabled = name.isNotBlank()) {
                                    Text("Save")
                                }
                            },
                            dismissButton = { TextButton(onClick = { showAddNotebookDialog = false }) { Text("Cancel") } }
                        )

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    }

                    renamingNotebook?.let { (id, oldName) ->
                        var name by remember { mutableStateOf(oldName) }
                        val focusRequester = remember { FocusRequester() }

                        AlertDialog(
                            onDismissRequest = { renamingNotebook = null },
                            title = { Text("Rename Notebook") },
                            text = {
                                TextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Name") },
                                    modifier = Modifier.focusRequester(focusRequester)
                                )
                            },
                            confirmButton = {
                                Button(onClick = { viewModel.renameNotebook(id, name); renamingNotebook = null }, enabled = name.isNotBlank()) {
                                    Text("Save")
                                }
                            },
                            dismissButton = { TextButton(onClick = { renamingNotebook = null }) { Text("Cancel") } }
                        )

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}
