package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.ui.state.ToggleableState

internal fun toolApprovalSelectionState(selected: Set<String>, adjustable: Set<String>): ToggleableState = when {
    adjustable.isEmpty() || selected.none { it in adjustable } -> ToggleableState.Off
    selected.containsAll(adjustable) -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}
