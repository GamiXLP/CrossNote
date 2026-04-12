package de.crossnote.ui.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import de.crossnote.ui.android.data.DragDropState

@Composable
fun DraggableItem(
    id: String,
    name: String,
    type: DragDropState.ItemType,
    dragDropState: DragDropState,
    onDrop: (String?) -> Unit,
    content: @Composable () -> Unit
) {
    val info = remember(id, name, type, onDrop) { DragDropState.DragSourceInfo(id, name, type, onDrop) }
    
    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                val bounds = Rect(coordinates.positionInWindow(), coordinates.size.toSize())
                dragDropState.dragSources[id] = DragDropState.DragSourceEntry(info, bounds)
                if (dragDropState.draggedItemId == id) {
                    dragDropState.dragStartWindowPos = coordinates.positionInWindow()
                }
            }
            .disposeOnUnmount {
                if (dragDropState.draggedItemId != id) {
                    dragDropState.dragSources.remove(id)
                }
            }
    ) {
        content()
    }
}

@Composable
fun Modifier.disposeOnUnmount(onDispose: () -> Unit): Modifier {
    DisposableEffect(Unit) {
        onDispose { onDispose() }
    }
    return this
}

@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
