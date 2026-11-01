package me.rerere.zeroagent.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.rerere.zeroagent.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
