package me.rerere.tts.provider.providers

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SystemTtsDeadlineTest {
    @Test fun `engine timeout is a recoverable synthesis error`() = runBlocking {
        val error = runCatching { withSystemTtsDeadline(15) { delay(2000) } }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("tts_engine_timeout", error?.message)
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun `user cancellation never becomes synthesis failure`() = runBlocking {
        var returned = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            withSystemTtsDeadline(5000) { awaitCancellation() }
            returned = true
        }
        job.cancelAndJoin(); assertTrue(job.isCancelled); assertFalse(returned)
    }
    @Test fun `available engine results pass through`() = runBlocking {
        assertEquals("ready", withSystemTtsDeadline(5000) { "ready" })
    }
}
