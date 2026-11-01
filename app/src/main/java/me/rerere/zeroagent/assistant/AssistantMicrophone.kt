package me.rerere.zeroagent.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground capture preempts the wake recorder; it must release before the next owner starts. */
object AssistantMicrophone {
    private val mutex = Mutex()
    private val requested = MutableStateFlow(0)
    val foregroundRequests = requested.asStateFlow()
    class Lease internal constructor(private val owner: Any, private val foreground: Boolean) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            mutex.unlock(owner)
            if (foreground) requested.update { (it - 1).coerceAtLeast(0) }
        }
    }
    suspend fun acquire(): Lease {
        val owner = Any()
        requested.update { it + 1 }
        try { mutex.lock(owner) } catch (e: Throwable) { requested.update { (it - 1).coerceAtLeast(0) }; throw e }
        return Lease(owner, true)
    }
    fun tryWake(): Lease? {
        if (requested.value > 0) return null
        val owner = Any()
        if (!mutex.tryLock(owner)) return null
        if (requested.value > 0) { mutex.unlock(owner); return null }
        return Lease(owner, false)
    }
}
