package me.rerere.zeroagent.assistant

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** A visible one-shot session can keep audio alive without enabling permanent wake listening. */
internal object AssistantAudioSessions {
    private val lock = Any()
    private val callbacks = mutableMapOf<String, () -> Unit>()
    private val mutable = MutableStateFlow(0)
    val count = mutable.asStateFlow()
    val serviceReady = MutableStateFlow(false)
    class Lease internal constructor(private val id: String) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(lock) { callbacks.remove(id); mutable.value = callbacks.size }
        }
    }
    fun begin(context: Context, onStopped: () -> Unit): Lease {
        val id = UUID.randomUUID().toString()
        synchronized(lock) { callbacks[id] = onStopped; mutable.value = callbacks.size }
        try { ContextCompat.startForegroundService(context, Intent(context, AssistantWakeService::class.java)) }
        catch (e: Exception) { synchronized(lock) { callbacks.remove(id); mutable.value = callbacks.size }; throw AssistantFailure(AssistantIssue.SERVICE_STOPPED) }
        return Lease(id)
    }
    fun stopAll() {
        val stopped = synchronized(lock) { callbacks.values.toList().also { callbacks.clear(); mutable.value = 0 } }
        stopped.forEach { runCatching(it) }
    }
}
