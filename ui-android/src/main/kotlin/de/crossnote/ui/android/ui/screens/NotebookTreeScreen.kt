package de.crossnote.ui.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import crossnote.app.note.NoteSummaryDto
import crossnote.app.note.NotebookTreeDto
import de.crossnote.ui.android.data.DragDropState
import de.crossnote.ui.android.ui.components.DraggableItem
import de.crossnote.ui.android.ui.components.NotebookNodeItem
import kotlinx.coroutines.delay

@Composable
fun NotebookTree(
    tree: NotebookTreeDto?, 
    dragDropState: DragDropState,
    expandedIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onNoteClick: (NoteSummaryDto) -> Unit,
    onAddNoteToNotebook: (String) -> Unit,
    onAddSubNotebook: (String) -> Unit,
    onRenameNotebook: (String, String) -> Unit,
    onDeleteNotebook: (String) -> Unit,
    onMoveNotebook: (String, String?) -> Unit,
    onMoveNote: (String, String?) -> Unit
) {
    val scrollState = rememberLazyListState()
    
    LaunchedEffect(dragDropState.draggedItemId) {
        while (dragDropState.draggedItemId != null) {
            val bounds = dragDropState.listBounds ?: break
            // Use visual position (where the icon is) for scrolling triggers
            val visualPos = dragDropState.getVisualWindowPosition()
            val threshold = 150f
            val maxSpeed = 20f
            
            if (visualPos.y < bounds.top + threshold) {
                val dist = (bounds.top + threshold - visualPos.y).coerceAtLeast(0f)
                val speed = (dist / threshold).coerceIn(0f, 1f) * maxSpeed
                scrollState.scrollBy(-speed)
            } else if (visualPos.y > bounds.bottom - threshold) {
                val dist = (visualPos.y - (bounds.bottom - threshold)).coerceAtLeast(0f)
                val speed = (dist / threshold).coerceIn(0f, 1f) * maxSpeed
                scrollState.scrollBy(speed)
            }
            delay(16)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .onGloballyPositioned {
                    val rect = Rect(
                        it.positionInWindow(),
                        it.size.toSize()
                    )
                    dragDropState.dropTargets[null] = rect
                }
                .then(
                    if (dragDropState.draggedItemId != null) {
                        Modifier.padding(vertical = 8.dp).height(64.dp).background(
                            if (dragDropState.isHovering(null)) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.shapes.small
                        )
                    } else {
                        Modifier.height(0.dp)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (dragDropState.draggedItemId != null) {
                Text("Move to Root", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (tree == null || (tree.rootNotes.isEmpty() && tree.notebooks.isEmpty())) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No notebooks or root notes.")
            }
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned {
                        dragDropState.listBounds = Rect(it.positionInWindow(), it.size.toSize())
                    },
                contentPadding = PaddingValues(bottom = 150.dp)
            ) {
                if (tree.rootNotes.isNotEmpty()) {
                    item {
                        Text("Root Notes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                    }
                    items(tree.rootNotes) { note ->
                        DraggableItem(
                            id = note.id,
                            name = note.title,
                            type = DragDropState.ItemType.NOTE,
                            dragDropState = dragDropState,
                            onDrop = { targetId -> onMoveNote(note.id, targetId) }
                        ) {
                            ListItem(
                                headlineContent = { Text(note.title) },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                                modifier = Modifier
                                    .clickable { onNoteClick(note) }
                                    .onGloballyPositioned {
                                        val rect = Rect(it.positionInWindow(), it.size.toSize())
                                        dragDropState.dropTargets[note.id] = rect
                                        dragDropState.dropTargets["target-root-area-${note.id}"] = rect 
                                    }
                            )
                        }
                    }
                }
                
                if (tree.notebooks.isNotEmpty()) {
                    item {
                        Text("Notebooks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                    }
                    items(tree.notebooks) { notebook ->
                        NotebookNodeItem(
                            node = notebook, 
                            depth = 0,
                            tree = tree,
                            dragDropState = dragDropState,
                            expandedIds = expandedIds,
                            onToggleExpand = onToggleExpand,
                            onNoteClick = { onNoteClick(it) },
                            onAddNote = onAddNoteToNotebook,
                            onAddSub = onAddSubNotebook,
                            onRename = onRenameNotebook,
                            onDelete = onDeleteNotebook,
                            onMoveNotebook = onMoveNotebook,
                            onMoveNote = onMoveNote
                        )
                    }
                }
            }
        }
    }
}
