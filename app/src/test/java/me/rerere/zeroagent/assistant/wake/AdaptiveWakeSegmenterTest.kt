package me.rerere.zeroagent.assistant.wake

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class AdaptiveWakeSegmenterTest {
    private fun frames(pcm: ShortArray) = (0 until pcm.size - 319 step 320).map { pcm.copyOfRange(it, it + 320) }
    @Test fun `quiet speech below the old fixed threshold creates a qualified segment`() {
        for (scale in listOf(.002, .004, .008)) {
            val segmenter = AdaptiveWakeSegmenter()
            val pcm = ShortArray(6400) + WakeTestAudio.phrase(scale)
            val clips = frames(pcm).mapNotNull { segmenter.accept(it)?.pcm }
            assertEquals("scale=$scale", 1, clips.size)
            assertTrue(WakeSampleQuality.inspect(clips.single()).snrDb > 9)
            assertTrue(frames(pcm).maxOf { AdaptiveWakeSegmenter.rms(it) } < .012f)
        }
    }
    @Test fun `five and ten natural utterances automatically accumulate separate real clips`() {
        for (target in listOf(5, 10)) {
            val segmenter = AdaptiveWakeSegmenter()
            var count = 0
            val input = ShortArray(6400) + List(target) { WakeTestAudio.phrase() }.flatMap { it.toList() }.toShortArray()
            frames(input).forEach { frame -> segmenter.accept(frame)?.pcm?.let { WakeSampleQuality.inspect(it); count++ } }
            assertEquals(target, count)
        }
    }
    @Test fun `stationary ambient noise does not trigger forever as speech`() {
        val random = Random(2)
        val pcm = ShortArray(16000 * 10) { (random.nextDouble(-.025, .025) * 32767).toInt().toShort() }
        val segmenter = AdaptiveWakeSegmenter()
        val events = frames(pcm).mapNotNull { segmenter.accept(it) }
        assertTrue(events.none { it.pcm != null })
        assertTrue(events.any { it.problem == SampleProblem.NOISY })
    }
    @Test fun `an isolated loud click does not start an utterance`() {
        val segmenter = AdaptiveWakeSegmenter()
        repeat(25) { segmenter.accept(ShortArray(320)) }
        segmenter.accept(ShortArray(320) { 20000 })
        repeat(30) { assertNull(segmenter.accept(ShortArray(320))) }
        assertEquals(WakeCapturePhase.LISTENING, segmenter.phase)
    }
    @Test fun `uncut long speech is rejected and requires a quiet recovery gap`() {
        val segmenter = AdaptiveWakeSegmenter()
        repeat(20) { segmenter.accept(ShortArray(320)) }
        val events = (0..250).mapNotNull { n -> segmenter.accept(ShortArray(320) { i -> (sin((n * 320 + i) * .1) * 6000).toInt().toShort() }) }
        assertTrue(events.any { it.problem == SampleProblem.TOO_LONG })
        assertTrue(events.none { it.pcm != null })
    }
    @Test fun `the level meter shows quiet valid speech without inventing a nonzero silence level`() {
        assertEquals(0f, AdaptiveWakeSegmenter.meter(0f), 0f)
        assertTrue(AdaptiveWakeSegmenter.meter(.004f) > .3f)
        assertEquals(0f, AdaptiveWakeSegmenter.meter(Float.NaN), 0f)
    }
}
