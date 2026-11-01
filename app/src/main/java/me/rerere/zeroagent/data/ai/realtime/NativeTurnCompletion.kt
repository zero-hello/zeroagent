package me.rerere.zeroagent.data.ai.realtime

import java.util.concurrent.atomic.AtomicBoolean

/** A response completes only once after server completion and actual output drain. */
internal class NativeTurnCompletion {
    private val armed = AtomicBoolean(false)
    fun begin() { armed.set(true) }
    fun cancel() { armed.set(false) }
    fun finish(responseDone: Boolean, playing: Boolean): Boolean = responseDone && !playing && armed.compareAndSet(true, false)
}
