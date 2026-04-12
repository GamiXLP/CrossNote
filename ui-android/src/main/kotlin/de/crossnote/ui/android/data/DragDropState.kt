package de.crossnote.ui.android.data

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

class DragDropState {
    var draggedItemId by mutableStateOf<String?>(null)
    var draggedItemType by mutableStateOf<ItemType?>(null)
    var draggedItemName by mutableStateOf("")
    var dragOffset by mutableStateOf(Offset.Zero)
    var dragStartWindowPos by mutableStateOf(Offset.Zero)
    var touchOffsetInItem by mutableStateOf(Offset.Zero)
    var containerWindowPos by mutableStateOf(Offset.Zero)
    var listBounds by mutableStateOf<Rect?>(null)
    
    // The visual offset of the icon relative to the user's finger (in pixels)
    var visualOffset by mutableStateOf(Offset.Zero)
    
    val dropTargets = mutableStateMapOf<String?, Rect>()
    val dragSources = mutableStateMapOf<String, DragSourceEntry>()
    var onDropAction by mutableStateOf<(String?) -> Unit>({})

    enum class ItemType { NOTE, NOTEBOOK }
    data class DragSourceInfo(val id: String, val name: String, val type: ItemType, val onDrop: (String?) -> Unit)
    data class DragSourceEntry(val info: DragSourceInfo, val bounds: Rect)

    fun getTouchWindowPosition(): Offset = dragStartWindowPos + touchOffsetInItem + dragOffset
    
    fun getVisualWindowPosition(): Offset = getTouchWindowPosition() + visualOffset
    
    fun getLocalDragPosition(): Offset = dragStartWindowPos + dragOffset - containerWindowPos

    fun findTargetId(position: Offset): String? {
        return dropTargets.entries
            .filter { it.value.contains(position) }
            .minByOrNull { it.value.width * it.value.height }
            ?.key
    }

    fun isHovering(targetId: String?): Boolean {
        val visualPos = getVisualWindowPosition()
        return findTargetId(visualPos) == targetId
    }
}

@Composable
fun rememberDragDropState() = remember { DragDropState() }
