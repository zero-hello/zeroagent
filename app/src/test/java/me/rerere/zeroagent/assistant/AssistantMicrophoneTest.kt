package me.rerere.zeroagent.assistant
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class AssistantMicrophoneTest {
    @Test fun `wake yields to foreground and cancellation releases the pending claim`() = runBlocking {
        val wake = AssistantMicrophone.tryWake()!!
        val pending = async(start = CoroutineStart.UNDISPATCHED) { AssistantMicrophone.acquire() }
        assertEquals(1, AssistantMicrophone.foregroundRequests.value)
        assertNull(AssistantMicrophone.tryWake())
        pending.cancelAndJoin(); assertEquals(0, AssistantMicrophone.foregroundRequests.value)
        wake.close(); wake.close()
        val foreground = AssistantMicrophone.acquire(); assertNull(AssistantMicrophone.tryWake())
        foreground.close(); assertEquals(0, AssistantMicrophone.foregroundRequests.value)
        AssistantMicrophone.tryWake()!!.close()
    }
}
