package me.rerere.zeroagent.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** The app initializes underneath; restoring an Activity never restarts the animation. */
@Composable
fun ZeroAgentLaunchAnimation(enabled: Boolean) {
    var visible by rememberSaveable { mutableStateOf(enabled) }
    if (visible) ZeroAgentLaunchOverlay(onFinished = { visible = false })
}
