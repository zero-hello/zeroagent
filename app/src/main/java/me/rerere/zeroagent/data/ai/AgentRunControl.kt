package me.rerere.zeroagent.data.ai

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** One flow's cancellation latch; inherited by tool timeouts and child coroutines. */
internal class AgentRunControl(private val onPause: () -> Unit = {}) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AgentRunControl>

    private val paused = AtomicBoolean(false)
    @Volatile private var job: Job? = null

    fun bind(job: Job) {
        this.job = job
        if (paused.get()) job.cancel(CancellationException("Agent paused by user"))
    }

    fun pause() {
        if (!paused.compareAndSet(false, true)) return
        // Pause the owning chat queue before cancellation can trigger its completion hook.
        try {
            onPause()
        } finally {
            job?.cancel(CancellationException("Agent paused by user"))
        }
    }

    fun ensureRunning() {
        if (paused.get()) throw CancellationException("Agent paused by user")
    }
}

/** Check directly before a side effect as well as at dispatch boundaries. */
internal suspend fun ensureAgentExecutionActive() {
    val context = currentCoroutineContext()
    context.ensureActive()
    context[AgentRunControl]?.ensureRunning()
}
