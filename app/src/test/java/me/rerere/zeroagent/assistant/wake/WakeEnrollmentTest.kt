package me.rerere.zeroagent.assistant.wake

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WakeEnrollmentTest {
    @Test fun `failed persistence cannot advance the sample count`() = runBlocking {
        val enrollment = WakeEnrollment()
        val sample = WakeTestAudio.phrase()
        try { enrollment.commit(listOf(sample)) { throw IOException("disk full") }; fail("must fail") } catch (_: IOException) { }
        assertEquals(0, enrollment.samples.size)
        enrollment.commit(listOf(sample)) { assertEquals(0, enrollment.samples.size) }
        assertEquals(1, enrollment.samples.size)
    }
    @Test fun `failed rerecord and deletion keep the previous committed data`() = runBlocking {
        val enrollment = WakeEnrollment(); val old = WakeTestAudio.phrase()
        enrollment.restore(listOf(old))
        for (next in listOf(emptyList(), listOf(WakeTestAudio.phrase(.2)))) {
            try { enrollment.commit(next) { throw IOException("storage unavailable") }; fail("must fail") } catch (_: IOException) { }
            assertArrayEquals(old, enrollment.samples.single())
        }
    }
    @Test fun `committed data does not retain a mutable microphone buffer`() = runBlocking {
        val input = WakeTestAudio.phrase(); val expected = input.copyOf(); val enrollment = WakeEnrollment()
        enrollment.commit(listOf(input)) { }
        input.fill(0)
        assertArrayEquals(expected, enrollment.samples.single())
    }
}
