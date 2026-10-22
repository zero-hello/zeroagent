package me.rerere.zeroagent.assistant.wake

import kotlinx.coroutines.*
import me.rerere.zeroagent.assistant.AssistantMicrophone
import org.junit.Assert.*
import org.junit.Test

class WakeMicrophoneLeaseTest {
    @Test fun `a timed out waiting enrollment cannot leak a foreground microphone request`() = runBlocking {
        val owner = AssistantMicrophone.acquire()
        try {
            assertEquals(1, AssistantMicrophone.foregroundRequests.value)
            assertNull(withTimeoutOrNull(30) { AssistantMicrophone.acquire() })
            assertEquals(1, AssistantMicrophone.foregroundRequests.value)
        } finally { owner.close() }
        assertEquals(0, AssistantMicrophone.foregroundRequests.value)
        AssistantMicrophone.tryWake()!!.close()
    }
    @Test fun `canceled capture owner releases its lease in finally`() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val capture = launch {
            val lease = AssistantMicrophone.acquire()
            try { ready.complete(Unit); awaitCancellation() } finally { lease.close() }
        }
        ready.await(); capture.cancelAndJoin()
        assertEquals(0, AssistantMicrophone.foregroundRequests.value)
        AssistantMicrophone.tryWake()!!.close()
    }
}
