package me.rerere.zeroagent.ui.pages.extensions.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalOutputGateTest {
    @Test fun finalOrStoppedMessageCannotBeOverwrittenByQueuedOutput() {
        val gate = TerminalOutputGate()
        val command = gate.start()
        assertTrue(gate.accepts(command))
        gate.finish(command)
        assertFalse(gate.accepts(command))
    }

    @Test fun oldCommandCannotOverwriteOrFinishTheNextCommand() {
        val gate = TerminalOutputGate()
        val oldCommand = gate.start()
        val nextCommand = gate.start()
        assertFalse(gate.accepts(oldCommand))
        gate.finish(oldCommand)
        assertTrue(gate.accepts(nextCommand))
    }
}
