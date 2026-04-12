package de.crossnote.ui.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import crossnote.app.note.NoteSummaryDto

@Composable
fun NoteList(notes: List<NoteSummaryDto>, onNoteClick: (NoteSummaryDto) -> Unit) {
    if (notes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No notes found.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(notes) { note ->
                ListItem(
                    headlineContent = { Text(note.title.ifBlank { "(Untitled)" }) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                    modifier = Modifier.clickable { onNoteClick(note) }
                )
                HorizontalDivider()
            }
        }
    }
}
