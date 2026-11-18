package me.rerere.zeroagent.ui.pages.chat

/** Keep the four actions in complete rows while leaving room for text at the current scale. */
internal fun welcomeActionColumns(widthDp: Float, fontScale: Float): Int {
    val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(1f) ?: 1f
    val minimumWidth = 128f * scale
    return when {
        widthDp >= minimumWidth * 4 + 8f * 3 -> 4
        widthDp >= minimumWidth * 2 + 8f -> 2
        else -> 1
    }
}
