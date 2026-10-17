package me.rerere.zeroagent.assistant.wake

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AcousticWakeEngineTest {
    private fun sample(scale: Double = 1.0, shift: Int = 0): ShortArray {
        val data = ShortArray(24000)
        for (i in 3200 until 17600) {
            val t = (i - 3200 + shift) / 16000.0
            val envelope = .12 * sin(PI * (i - 3200) / 14400).pow(2)
            val carrier = sin(2 * PI * (170 + 75 * sin(t * 8)) * t) + .45 * sin(2 * PI * 510 * t)
            data[i] = (envelope * carrier * scale * 32767).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }
    @Test fun `personalization requires the requested real sample count`() {
        assertThrows(IllegalArgumentException::class.java) { AcousticWakeEngine.train("Hey Zero", "en", listOf(sample())) }
        val profile = AcousticWakeEngine.train("Hey Zero", "en", List(5) { sample(1 + it * .02) })
        assertTrue(AcousticWakeEngine.valid(profile))
        assertTrue(AcousticWakeEngine.detect(profile, sample(.85)))
        assertFalse(AcousticWakeEngine.detect(profile, ShortArray(24000)))
    }
    @Test fun `quality rejects silence short clips and hard clipping`() {
        assertEquals(SampleProblem.TOO_QUIET, assertThrows(WakeSampleException::class.java) { WakeSampleQuality.inspect(ShortArray(16000)) }.problem)
        assertEquals(SampleProblem.TOO_SHORT, assertThrows(WakeSampleException::class.java) { WakeSampleQuality.inspect(ShortArray(1000)) }.problem)
        assertEquals(SampleProblem.CLIPPED, assertThrows(WakeSampleException::class.java) { WakeSampleQuality.inspect(ShortArray(16000) { 32767 }) }.problem)
    }
    @Test fun `corrupt profiles never enter the detector`() {
        val profile = WakeProfile(phrase = "Hey Zero", language = "en", threshold = Float.NaN, templates = emptyList())
        assertFalse(AcousticWakeEngine.valid(profile)); assertFalse(AcousticWakeEngine.detect(profile, sample()))
    }
    @Test fun `ten sample training retains phrase and language without claiming STT`() {
        val profile = AcousticWakeEngine.train("Hey Zero", "zh-TW", List(10) { sample(.8 + it * .025) })
        assertEquals(10, profile.templates.size); assertEquals("Hey Zero", profile.phrase); assertEquals("zh-TW", profile.language)
    }
    @Test fun `an engine without personalization cannot pretend to train`() {
        val engine = object : PersonalWakeEngine {
            override val id = "fixed"
            override val supportsPersonalization = false
            override fun train(phrase: String, language: String, samples: List<ShortArray>): WakeProfile = error("must not be called")
            override fun valid(profile: WakeProfile) = false
            override fun detect(profile: WakeProfile, pcm: ShortArray) = false
        }
        assertThrows(IllegalStateException::class.java) { WakeEngines.train(engine, "Hey Zero", "en", List(5) { sample() }) }
    }

}
