package me.rerere.zeroagent.assistant.wake

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class WakeSampleQualityTest {
    @Test fun `quiet modulated speech keeps real duration and useful SNR`() {
        val q = WakeSampleQuality.inspect(WakeTestAudio.phrase(.008))
        assertTrue(q.seconds in .4f..1.5f)
        assertTrue(q.snrDb > 9)
        assertTrue(q.start < q.end)
    }
    @Test fun `steady test tone cannot become a voice training sample`() {
        val pcm = ShortArray(32000) { i -> if (i in 6400..22400) (sin(2 * PI * 200 * i / 16000) * 4000).toInt().toShort() else 0 }
        assertEquals(SampleProblem.TONAL, assertThrows(WakeSampleException::class.java) { WakeSampleQuality.inspect(pcm) }.problem)
    }
    @Test fun `amplitude modulated broadband noise is still rejected`() {
        val random = Random(8)
        val pcm = ShortArray(32000) { i ->
            val t = i / 16000.0
            val envelope = if (t in .4..1.45) sin(PI * (t - .4) / 1.05).pow(2) else 0.0
            (random.nextDouble(-1.0, 1.0) * envelope * 6000).toInt().toShort()
        }
        assertEquals(SampleProblem.NOISY, assertThrows(WakeSampleException::class.java) { WakeSampleQuality.inspect(pcm) }.problem)
    }
    @Test fun `the acoustic engine does not accept a configurable wake phrase`() {
        assertThrows(IllegalArgumentException::class.java) { AcousticWakeEngine.train("Different words", "en", List(5) { WakeTestAudio.phrase() }) }
    }
}
