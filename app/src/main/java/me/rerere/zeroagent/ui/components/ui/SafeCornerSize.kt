package me.rerere.zeroagent.ui.components.ui

/** Spring overshoot must never become a negative RoundedCornerShape size. */
internal fun safeCornerSize(value: Float): Float =
    if (value.isFinite()) value.coerceAtLeast(0f) else 0f
