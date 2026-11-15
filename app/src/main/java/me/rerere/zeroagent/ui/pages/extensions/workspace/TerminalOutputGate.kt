package me.rerere.zeroagent.ui.pages.extensions.workspace

/** Main-thread ownership gate: queued output belongs only to its active command. */
internal class TerminalOutputGate {
    private var generation = 0L
    private var active = false

    fun start(): Long {
        active = true
        return ++generation
    }

    fun accepts(token: Long): Boolean = active && generation == token

    fun finish(token: Long) {
        if (generation == token) active = false
    }
}
