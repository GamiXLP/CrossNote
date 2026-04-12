package de.crossnote.ui.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import crossnote.app.note.NoteSummaryDto
import crossnote.app.note.NotebookNodeDto
import crossnote.app.note.NotebookTreeDto
import de.crossnote.ui.android.data.DragDropState

@Composable
fun NotebookNodeItem(
    node: NotebookNodeDto, 
    depth: Int,
    tree: NotebookTreeDto,
    dragDropState: DragDropState,
    expandedIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onNoteClick: (NoteSummaryDto) -> Unit,
    onAddNote: (String) -> Unit,
    onAddSub: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMoveNotebook: (String, String?) -> Unit,
    onMoveNote: (String, String?) -> Unit
) {
    val expanded = expandedIds.contains(node.id)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val isDraggedOver = dragDropState.draggedItemId != null && 
                        dragDropState.draggedItemId != node.id &&
                        dragDropState.isHovering(node.id)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDraggedOver) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent)
    ) {
        DraggableItem(
            id = node.id,
            name = node.name,
            type = DragDropState.ItemType.NOTEBOOK,
            dragDropState = dragDropState,
            onDrop = { targetId: String? -> onMoveNotebook(node.id, targetId) }
        ) {
            ListItem(
                headlineContent = { Text(node.name) },
                leadingContent = { 
                    Icon(
                        if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder, 
                        contentDescription = null 
                    ) 
                },
                trailingContent = {
                    if (expanded) {
                        Row {
                            IconButton(onClick = { onAddNote(node.id) }) {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Add Note")
                            }
                            IconButton(onClick = { onAddSub(node.id) }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Sub-Folder")
                            }
                            IconButton(onClick = { onRename(node.id, node.name) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                },
                modifier = Modifier
                    .padding(start = (depth * 16).dp)
                    .clickable { onToggleExpand(node.id) }
                    .onGloballyPositioned { coordinates ->
                        val rect = Rect(coordinates.positionInWindow(), coordinates.size.toSize())
                        dragDropState.dropTargets[node.id] = rect
                    }
            )
        }
        if (expanded) {
            node.subNotebooks.forEach { sub ->
                NotebookNodeItem(
                    node = sub,
                    depth = depth + 1,
                    tree = tree,
                    dragDropState = dragDropState,
                    expandedIds = expandedIds,
                    onToggleExpand = onToggleExpand,
                    onNoteClick = onNoteClick,
                    onAddNote = onAddNote,
                    onAddSub = onAddSub,
                    onRename = onRename,
                    onDelete = onDelete,
                    onMoveNotebook = onMoveNotebook,
                    onMoveNote = onMoveNote
                )
            }
            
            if (node.notes.isEmpty() && node.subNotebooks.isEmpty()) {
                Text(
                    text = "(Empty)", 
                    modifier = Modifier.padding(start = (depth * 16 + 48).dp, bottom = 8.dp), 
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                node.notes.forEach { note ->
                    DraggableItem(
                        id = note.id,
                        name = note.title,
                        type = DragDropState.ItemType.NOTE,
                        dragDropState = dragDropState,
                        onDrop = { targetId: String? -> onMoveNote(note.id, targetId) }
                    ) {
                        ListItem(
                            headlineContent = { Text(note.title) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                            modifier = Modifier
                                .padding(start = ((depth + 1) * 16).dp)
                                .clickable { onNoteClick(note) }
                        )
                    }
                }
            }
        }
        HorizontalDivider()

        if (showDeleteConfirm) {
            ConfirmDialog(
                title = "Delete Notebook",
                text = "Delete '${node.name}' and all contents recursively?",
                onConfirm = { onDelete(node.id); showDeleteConfirm = false },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

@Composable
fun MoveItemDialog(
    title: String,
    tree: NotebookTreeDto?,
    currentId: String? = null,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Root") },
                        modifier = Modifier.clickable { onMove(null) }.background(if (currentId == null) Color.LightGray.copy(alpha = 0.2f) else Color.Transparent)
                    )
                    HorizontalDivider()
                }
                
                fun renderNodes(nodes: List<NotebookNodeDto>, depth: Int) {
                    nodes.forEach { node ->
                        if (node.id != currentId) {
                            item {
                                ListItem(
                                    headlineContent = { Text(node.name) },
                                    modifier = Modifier.clickable { onMove(node.id) }.padding(start = (depth * 16).dp)
                                )
                                HorizontalDivider()
                            }
                            renderNodes(node.subNotebooks, depth + 1)
                        }
                    }
                }
                
                if (tree != null) {
                    renderNodes(tree.notebooks, 0)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
