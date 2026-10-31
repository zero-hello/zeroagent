package me.rerere.zeroagent.ui.components.ui

internal fun stackCardGroupControls(widthDp: Float, fontScale: Float): Boolean =
    widthDp < 320f || fontScale >= 1.3f
