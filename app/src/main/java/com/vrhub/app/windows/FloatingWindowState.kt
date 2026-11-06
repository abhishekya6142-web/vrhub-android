package com.vrhub.app.windows

/** Platform-neutral state for a movable/resizable XR window. Rendering is supplied by the UI layer. */
data class FloatingWindowState(
    val id: String,
    val title: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = -1.5f,
    val width: Float = 0.8f,
    val height: Float = 0.55f,
    val isVisible: Boolean = true,
    val isPinned: Boolean = false
) {
    fun movedTo(newX: Float, newY: Float, newZ: Float = z) =
        copy(x = newX, y = newY, z = newZ)

    fun resized(newWidth: Float, newHeight: Float) =
        copy(width = newWidth.coerceAtLeast(0.2f), height = newHeight.coerceAtLeast(0.15f))
}
