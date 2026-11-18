package me.rerere.zeroagent.data.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/** Coordinates exclusive microphone capture and lets foreground capture preempt background work. */
object AudioCaptureCoordinator {
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
        try {
            mutex.lock(owner)
        } catch (error: Throwable) {
            requested.update { (it - 1).coerceAtLeast(0) }
            throw error
        }
        return Lease(owner, foreground = true)
    }

    fun tryAcquireBackground(): Lease? {
        if (requested.value > 0) return null
        val owner = Any()
        if (!mutex.tryLock(owner)) return null
        if (requested.value > 0) {
            mutex.unlock(owner)
            return null
        }
        return Lease(owner, foreground = false)
    }
}
