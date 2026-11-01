package me.rerere.zeroagent.data.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AgentRunControlTest {
    @Test fun `pause before job binding still cancels the eventual run`() {
        val control = AgentRunControl()
        control.pause()
        val job = Job()
        control.bind(job)
        assertTrue(job.isCancelled)
        try { control.ensureRunning(); fail("paused control must block execution") } catch (_: CancellationException) { }
    }
    @Test fun `queue pause happens before cancellation and only once`() {
        val job = Job()
        var calls = 0
        val control = AgentRunControl { assertFalse(job.isCancelled); calls++ }
        control.bind(job)
        control.pause(); control.pause()
        assertEquals(1, calls)
        assertTrue(job.isCancelled)
    }
    @Test fun `timeout children inherit the paused latch`() = runBlocking {
        val control = AgentRunControl()
        var executed = false
        withContext(control) {
            control.pause()
            try { withTimeout(1000) { ensureAgentExecutionActive(); executed = true } }
            catch (_: CancellationException) { }
        }
        assertFalse(executed)
    }
}
