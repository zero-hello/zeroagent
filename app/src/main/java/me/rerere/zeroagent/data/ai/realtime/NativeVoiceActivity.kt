package me.rerere.zeroagent.data.ai.realtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/** Suppress standalone TTS during a native conversation, including parked text continuation. */
internal object NativeVoiceActivity {
    private val count = MutableStateFlow(0)
    val active = count.asStateFlow()
    fun acquire(): AutoCloseable {
        count.update { it + 1 }
        val closed = AtomicBoolean(false)
        return AutoCloseable { if (closed.compareAndSet(false, true)) count.update { (it - 1).coerceAtLeast(0) } }
    }
}
