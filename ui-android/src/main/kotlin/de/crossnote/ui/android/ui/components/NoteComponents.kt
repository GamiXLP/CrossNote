package de.crossnote.ui.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crossnote.app.note.NoteSummaryDto
import crossnote.app.note.NotebookNodeDto
import crossnote.app.note.NotebookTreeDto
import crossnote.app.note.RevisionSummaryDto
import crossnote.domain.revision.Revision
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrashList(
    notes: List<NoteSummaryDto>,
    notebookTree: NotebookTreeDto?,
    expandedIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onNoteClick: (NoteSummaryDto) -> Unit,
    onRestore: (NoteSummaryDto) -> Unit,
    onPurge: (NoteSummaryDto) -> Unit,
    onRestoreNotebook: (String) -> Unit,
    onPurgeNotebook: (String) -> Unit
) {
    if (notes.isEmpty() && (notebookTree == null || notebookTree.notebooks.isEmpty())) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Trash is empty.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (notebookTree != null && notebookTree.notebooks.isNotEmpty()) {
                item {
                    Text("Trashed Notebooks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                }
                items(notebookTree.notebooks) { notebook ->
                    TrashedNotebookItem(
                        node = notebook,
                        depth = 0,
                        expandedIds = expandedIds,
                        onToggleExpand = onToggleExpand,
                        onNoteClick = onNoteClick,
                        onRestore = onRestore,
                        onPurge = onPurge,
                        onRestoreNotebook = onRestoreNotebook,
                        onPurgeNotebook = onPurgeNotebook
                    )
                }
            }

            if (notes.isNotEmpty()) {
                item {
                    Text("Trashed Notes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                }
                items(notes) { note ->
                    TrashedNoteItem(
                        note = note,
                        onNoteClick = { onNoteClick(note) },
                        onRestore = { onRestore(note) },
                        onPurge = { onPurge(note) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrashedNotebookItem(
    node: NotebookNodeDto,
    depth: Int,
    expandedIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onNoteClick: (NoteSummaryDto) -> Unit,
    onRestore: (NoteSummaryDto) -> Unit,
    onPurge: (NoteSummaryDto) -> Unit,
    onRestoreNotebook: (String) -> Unit,
    onPurgeNotebook: (String) -> Unit
) {
    val isExpanded = expandedIds.contains(node.id)
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showPurgeConfirm by remember { mutableStateOf(false) }

    Column {
        ListItem(
            headlineContent = { Text(node.name) },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(depth) { Spacer(Modifier.width(16.dp)) }
                    IconButton(onClick = { onToggleExpand(node.id) }) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                    Icon(Icons.Default.Folder, contentDescription = null)
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = { showRestoreConfirm = true }) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore")
                    }
                    IconButton(onClick = { showPurgeConfirm = true }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Purge")
                    }
                }
            },
            modifier = Modifier.clickable { onToggleExpand(node.id) }
        )
        HorizontalDivider()

        if (isExpanded) {
            node.subNotebooks.forEach { sub ->
                TrashedNotebookItem(
                    node = sub,
                    depth = depth + 1,
                    expandedIds = expandedIds,
                    onToggleExpand = onToggleExpand,
                    onNoteClick = onNoteClick,
                    onRestore = onRestore,
                    onPurge = onPurge,
                    onRestoreNotebook = onRestoreNotebook,
                    onPurgeNotebook = onPurgeNotebook
                )
            }
            node.notes.forEach { note ->
                Row {
                    repeat(depth + 1) { Spacer(Modifier.width(16.dp)) }
                    Box(modifier = Modifier.weight(1f)) {
                        TrashedNoteItem(
                            note = note,
                            onNoteClick = { onNoteClick(note) },
                            onRestore = { onRestore(note) },
                            onPurge = { onPurge(note) }
                        )
                    }
                }
            }
        }

        if (showRestoreConfirm) {
            ConfirmDialog(
                title = "Restore Notebook",
                text = "Restore this notebook and all its content?",
                onConfirm = { onRestoreNotebook(node.id); showRestoreConfirm = false },
                onDismiss = { showRestoreConfirm = false }
            )
        }
        if (showPurgeConfirm) {
            ConfirmDialog(
                title = "Purge Notebook",
                text = "DANGER: Delete this notebook and all its content permanently?",
                onConfirm = { onPurgeNotebook(node.id); showPurgeConfirm = false },
                onDismiss = { showPurgeConfirm = false }
            )
        }
    }
}

@Composable
fun TrashedNoteItem(
    note: NoteSummaryDto,
    onNoteClick: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showPurgeConfirm by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(note.title.ifBlank { "(Untitled)" }) },
        supportingContent = { Text("Trashed") },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
        trailingContent = {
            Row {
                IconButton(onClick = { showRestoreConfirm = true }) {
                    Icon(Icons.Default.Restore, contentDescription = "Restore")
                }
                IconButton(onClick = { showPurgeConfirm = true }) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Purge")
                }
            }
        },
        modifier = Modifier.clickable { onNoteClick() }
    )
    HorizontalDivider()

    if (showRestoreConfirm) {
        ConfirmDialog(
            title = "Restore Note",
            text = "Do you want to restore this note?",
            onConfirm = { onRestore(); showRestoreConfirm = false },
            onDismiss = { showRestoreConfirm = false }
        )
    }
    if (showPurgeConfirm) {
        ConfirmDialog(
            title = "Purge Note",
            text = "DANGER: Delete this note permanently?",
            onConfirm = { onPurge(); showPurgeConfirm = false },
            onDismiss = { showPurgeConfirm = false }
        )
    }
}

@Composable
fun NoteEditor(
    noteId: String,
    title: String,
    content: String,
    isTrashed: Boolean,
    revisions: List<RevisionSummaryDto>,
    previewRevision: Revision?,
    onClose: (String, String) -> Unit,
    onAutoSave: (String, String) -> Unit,
    onSelectRevision: (String) -> Unit,
    onClearPreview: () -> Unit,
    onRestoreRevision: (String) -> Unit
) {
    var editedTitle by remember(title, previewRevision) { 
        mutableStateOf(previewRevision?.title ?: title) 
    }
    var editedContent by remember(content, previewRevision) { 
        mutableStateOf(previewRevision?.content ?: content) 
    }
    var showRevisions by remember { mutableStateOf(false) }

    val isPreview = previewRevision != null

    // Auto-save logic
    if (!isPreview && !isTrashed) {
        LaunchedEffect(editedTitle, editedContent) {
            if (editedTitle != title || editedContent != content) {
                delay(10000L)
                onAutoSave(editedTitle, editedContent)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isPreview) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "Previewing Revision from ${previewRevision?.createdAt}",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        OutlinedTextField(
            value = editedTitle,
            onValueChange = { editedTitle = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = isTrashed || isPreview
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = editedContent,
            onValueChange = { editedContent = it },
            label = { Text("Content") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            readOnly = isTrashed || isPreview
        )
        
        if (!isPreview && !isTrashed) {
            Text(
                "Note will be automatically saved 10 seconds after the last change.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (revisions.isNotEmpty()) {
                TextButton(onClick = { showRevisions = true }) {
                    Text("History (${revisions.size})")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            
            Row {
                if (isPreview) {
                    TextButton(onClick = { onClearPreview() }) {
                        Text("Cancel Preview")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onRestoreRevision(previewRevision!!.id.value); onClearPreview() }) {
                        Text("Restore")
                    }
                } else if (!isTrashed) {
                    Button(onClick = { onClose(editedTitle, editedContent) }) {
                        Text("Close")
                    }
                } else {
                    Button(onClick = { onClose(editedTitle, editedContent) }) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showRevisions) {
        val formatter = remember {
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault())
        }

        AlertDialog(
            onDismissRequest = { showRevisions = false },
            title = { Text("Revisions") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(revisions.asReversed()) { rev ->
                        val instant = runCatching { Instant.parse(rev.createdAtIso) }.getOrNull()
                        val dateText = if (instant != null) formatter.format(instant) else rev.createdAtIso
                        
                        ListItem(
                            headlineContent = { Text(dateText) },
                            modifier = Modifier.clickable { 
                                onSelectRevision(rev.id)
                                showRevisions = false 
                            }
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRevisions = false }) { Text("Close") } }
        )
    }
}
