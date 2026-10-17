package me.rerere.zeroagent.assistant

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AssistantModelReplyTest {
    @Test fun `provider cancellation is returned so the capsule can leave thinking`() = runBlocking {
        val reply = CompletableDeferred<String?>(); reply.cancel(CancellationException("provider stopped"))
        assertTrue(assistantReply { reply.await() }.exceptionOrNull() is CancellationException)
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun `closing the session cancels without returning an old reply`() = runBlocking {
        var returned = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assistantReply { awaitCancellation() }; returned = true }
        job.cancelAndJoin(); assertTrue(job.isCancelled); assertFalse(returned)
    }
    @Test fun `normal and failed replies retain their result`() = runBlocking {
        assertEquals("whole answer", assistantReply { "whole answer" }.getOrThrow())
        assertTrue(assistantReply { throw IllegalStateException("offline") }.exceptionOrNull() is IllegalStateException)
    }
}
