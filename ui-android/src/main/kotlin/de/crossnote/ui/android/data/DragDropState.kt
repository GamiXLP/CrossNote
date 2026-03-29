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
    
    val dropTargets = mutableStateMapOf<String?, Rect>()
    val dragSources = mutableStateMapOf<String, DragSourceEntry>()
    var onDropAction by mutableStateOf<(String?) -> Unit>({})

    enum class ItemType { NOTE, NOTEBOOK }
    data class DragSourceInfo(val id: String, val name: String, val type: ItemType, val onDrop: (String?) -> Unit)
    data class DragSourceEntry(val info: DragSourceInfo, val bounds: Rect)

    fun getTouchWindowPosition(): Offset = dragStartWindowPos + touchOffsetInItem + dragOffset
    
    fun getLocalDragPosition(): Offset = dragStartWindowPos + dragOffset - containerWindowPos

    fun findTargetId(touchPos: Offset): String? {
        return dropTargets.entries
            .filter { it.value.contains(touchPos) }
            .minByOrNull { it.value.width * it.value.height }
            ?.key
    }

    fun isHovering(targetId: String?): Boolean {
        val touchPos = getTouchWindowPosition()
        return findTargetId(touchPos) == targetId
    }
}

@Composable
fun rememberDragDropState() = remember { DragDropState() }
