package me.rerere.zeroagent.data.productivity

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ComparisonRunnerTest {
    @Test fun `one failed provider does not discard other answers`() = runBlocking {
        val outcomes = mutableMapOf<String, Result<String>>()
        ComparisonRunner.run(listOf("working", "broken"), request = { id ->
            if (id == "broken") error("provider failed") else "answer"
        }, update = { id, value -> outcomes[id] = value })
        assertEquals("answer", outcomes.getValue("working").getOrNull())
        assertTrue(outcomes.getValue("broken").isFailure)
    }

    @Test fun `cancellation reaches every request and emits no fake failures`() = runBlocking {
        var completed = 0
        var cancelled = 0
        val ready = CompletableDeferred<Unit>()
        var started = 0
        val parent = launch {
            ComparisonRunner.run(listOf("a", "b"), request = {
                started++
                if (started == 2) ready.complete(Unit)
                try { awaitCancellation() } finally { cancelled++ }
            }, update = { _, _ -> completed++ })
        }
        withTimeout(1000) { ready.await() }
        parent.cancelAndJoin()
        assertEquals(2, cancelled)
        assertEquals(0, completed)
    }
}
