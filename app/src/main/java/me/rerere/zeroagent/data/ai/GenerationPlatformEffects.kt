package me.rerere.zeroagent.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** Android effects only. The caller retains the run owner, pause callback and coroutine context. */
interface GenerationPlatformEffects {
    fun onTurnStart()
    fun onToolExecution(name: String, owner: Any, onPause: () -> Unit)
    fun onTurnComplete(owner: Any)
}

internal fun <T> Flow<T>.withPlatformEffects(effects: GenerationPlatformEffects, owner: Any): Flow<T> =
    onStart { effects.onTurnStart() }.onCompletion { effects.onTurnComplete(owner) }

internal enum class AutoReturnDecision { NONE, USER_SWITCHED, RETURN }

internal fun autoReturnDecision(
    navigatedAway: Boolean,
    automated: Boolean,
    destination: String?,
    foreground: String?,
    ownPackage: String,
): AutoReturnDecision = when {
    !navigatedAway || !automated -> AutoReturnDecision.NONE
    destination != null && foreground != null && foreground != destination && foreground != ownPackage ->
        AutoReturnDecision.USER_SWITCHED
    else -> AutoReturnDecision.RETURN
}

/** Match the existing startActivity boundary: report Exceptions, but let JVM Errors propagate. */
internal inline fun launchAutoReturn(launch: () -> Unit, onFailure: (Exception) -> Unit) {
    try {
        launch()
    } catch (error: Exception) {
        onFailure(error)
    }
}
