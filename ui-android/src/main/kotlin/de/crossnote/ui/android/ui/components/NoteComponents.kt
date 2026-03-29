package de.crossnote.ui.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crossnote.app.note.NoteSummaryDto
import crossnote.app.note.RevisionSummaryDto

@Composable
fun TrashList(
    notes: List<NoteSummaryDto>, 
    onNoteClick: (NoteSummaryDto) -> Unit,
    onRestore: (NoteSummaryDto) -> Unit,
    onPurge: (NoteSummaryDto) -> Unit
) {
    if (notes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Trash is empty.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(notes) { note ->
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
                    modifier = Modifier.clickable { onNoteClick(note) }
                )
                HorizontalDivider()

                if (showRestoreConfirm) {
                    ConfirmDialog(
                        title = "Restore Note",
                        text = "Do you want to restore this note?",
                        onConfirm = { onRestore(note); showRestoreConfirm = false },
                        onDismiss = { showRestoreConfirm = false }
                    )
                }
                if (showPurgeConfirm) {
                    ConfirmDialog(
                        title = "Purge Note",
                        text = "DANGER: Delete this note permanently?",
                        onConfirm = { onPurge(note); showPurgeConfirm = false },
                        onDismiss = { showPurgeConfirm = false }
                    )
                }
            }
        }
    }
}

@Composable
fun NoteEditor(
    noteId: String,
    title: String,
    content: String,
    isTrashed: Boolean,
    revisions: List<RevisionSummaryDto>,
    onSave: (String, String) -> Unit,
    onRestoreRevision: (String) -> Unit
) {
    var editedTitle by remember { mutableStateOf(title) }
    var editedContent by remember { mutableStateOf(content) }
    var showRevisions by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = editedTitle,
            onValueChange = { editedTitle = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = isTrashed
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = editedContent,
            onValueChange = { editedContent = it },
            label = { Text("Content") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            readOnly = isTrashed
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (revisions.isNotEmpty()) {
                TextButton(onClick = { showRevisions = true }) {
                    Text("History (${revisions.size})")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            
            if (!isTrashed) {
                Button(onClick = { onSave(editedTitle, editedContent) }) {
                    Text("Save")
                }
            }
        }
    }

    if (showRevisions) {
        AlertDialog(
            onDismissRequest = { showRevisions = false },
            title = { Text("Revisions") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(revisions) { rev ->
                        ListItem(
                            headlineContent = { Text(rev.createdAtIso) },
                            modifier = Modifier.clickable { onRestoreRevision(rev.id); showRevisions = false }
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRevisions = false }) { Text("Close") } }
        )
    }
}
