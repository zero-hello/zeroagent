package me.rerere.zeroagent.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.rerere.zeroagent.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

