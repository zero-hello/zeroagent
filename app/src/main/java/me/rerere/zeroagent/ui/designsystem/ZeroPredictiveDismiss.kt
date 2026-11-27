package me.rerere.zeroagent.ui.designsystem

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/** Previews dismissal while keeping the overlay's existing close action. */
@Composable
internal fun rememberZeroPredictiveDismiss(enabled: Boolean = true, onDismiss: () -> Unit): State<Float> {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismiss = rememberUpdatedState(onDismiss)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { progress.snapTo(it.progress.coerceIn(0f, 1f)) }
            dismiss.value()
        } finally {
            scope.launch { progress.animateTo(0f, ZeroMotion.snappy()) }
        }
    }
    return progress.asState()
}
