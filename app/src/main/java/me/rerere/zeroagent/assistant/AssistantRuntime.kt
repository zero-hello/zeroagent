package me.rerere.zeroagent.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class WakeStatus { STOPPED, STARTING, LISTENING, PAUSED, ERROR }
internal data class WakeRuntimeState(val status: WakeStatus = WakeStatus.STOPPED, val issue: AssistantIssue? = null)
internal object AssistantRuntime {
    private val mutable = MutableStateFlow(WakeRuntimeState())
    val wake = mutable.asStateFlow()
    fun update(status: WakeStatus, issue: AssistantIssue? = null) { mutable.value = WakeRuntimeState(status, issue) }
}

internal enum class CapsulePhase { STARTING, LISTENING, THINKING, SPEAKING, TEXT, ERROR, CLOSED }
internal data class CapsuleState(
    val phase: CapsulePhase = CapsulePhase.STARTING,
    val textInput: Boolean = false,
    val heard: String = "",
    val answer: String = "",
    val microphoneLevel: Float = 0f,
    val playbackPosition: Long = 0L,
    val playbackDuration: Long = 0L,
    val issue: AssistantIssue? = null,
    val errorSummary: String? = null,
    val echoWarning: Boolean = false,
)

/** Epochs are also checked by STT/LLM/TTS callbacks; old work cannot speak after an interruption. */
internal class AssistantTurnEpoch {
    private val current = java.util.concurrent.atomic.AtomicLong()
    fun next(): Long = current.incrementAndGet()
    fun valid(epoch: Long): Boolean = current.get() == epoch
}
