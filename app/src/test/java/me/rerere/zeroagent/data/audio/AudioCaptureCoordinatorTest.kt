package me.rerere.zeroagent.data.audio

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudioCaptureCoordinatorTest {
    @Test
    fun foregroundCaptureWaitsForBackgroundCaptureAndCancellationReleasesItsClaim() = runBlocking {
        val background = AudioCaptureCoordinator.tryAcquireBackground()!!
        val pending = async(start = CoroutineStart.UNDISPATCHED) { AudioCaptureCoordinator.acquire() }

        assertEquals(1, AudioCaptureCoordinator.foregroundRequests.value)
        assertNull(AudioCaptureCoordinator.tryAcquireBackground())
        pending.cancelAndJoin()
        assertEquals(0, AudioCaptureCoordinator.foregroundRequests.value)

        background.close()
        val foreground = AudioCaptureCoordinator.acquire()
        assertNull(AudioCaptureCoordinator.tryAcquireBackground())
        foreground.close()
        val idle = AudioCaptureCoordinator.tryAcquireBackground()
        assertNotNull(idle)
        idle?.close()
    }
}
