package me.rerere.zeroagent.assistant.wake

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.log10

enum class WakeCapturePhase { CALIBRATING, LISTENING, SPEAKING, SAVING }
data class WakeSegment(val pcm: ShortArray? = null, val problem: SampleProblem? = null)

/** 20 ms PCM frames; ambient calibration, hysteresis and pre-roll preserve quiet onsets. */
class AdaptiveWakeSegmenter {
    var phase = WakeCapturePhase.CALIBRATING; private set
    var noiseFloor = .0001f; private set
    val startThreshold get() = max(WakeSampleQuality.MIN_SIGNAL, noiseFloor * 2.5f + .0001f)
    private val ambient = ArrayDeque<Float>()
    private val lead = ArrayDeque<ShortArray>()
    private val utterance = ArrayList<ShortArray>()
    private var onset = 0
    private var quiet = 0
    private var waiting = 0
    private var recovering = false

    fun accept(frame: ShortArray): WakeSegment? {
        require(frame.size == WakeSampleQuality.FRAME)
        val level = rms(frame)
        if (phase == WakeCapturePhase.CALIBRATING) {
            ambient.addLast(level)
            remember(frame)
            if (ambient.size >= 20) { updateNoise(); phase = WakeCapturePhase.LISTENING }
            return null
        }
        if (phase == WakeCapturePhase.SPEAKING) {
            utterance.add(frame.copyOf())
            quiet = if (level < max(WakeSampleQuality.MIN_SIGNAL * .7f, noiseFloor * 1.6f + .00005f)) quiet + 1 else 0
            if (utterance.size >= 175) {
                reset(); ambient.clear(); phase = WakeCapturePhase.CALIBRATING; recovering = true
                return WakeSegment(problem = SampleProblem.TOO_LONG)
            }
            if (quiet >= 18) {
                val pcm = ShortArray(utterance.size * frame.size)
                utterance.forEachIndexed { i, values -> values.copyInto(pcm, i * frame.size) }
                reset()
                return WakeSegment(pcm = pcm)
            }
            return null
        }
        remember(frame)
        waiting++
        if (recovering) {
            quiet = if (level < startThreshold) quiet + 1 else 0
            if (quiet >= 18) { recovering = false; quiet = 0 }
        } else {
            onset = if (level > startThreshold) onset + 1 else 0
            if (onset >= 3) {
                utterance.addAll(lead.map { it.copyOf() }); lead.clear()
                phase = WakeCapturePhase.SPEAKING; quiet = 0
                return null
            }
        }
        if (level < startThreshold) {
            ambient.addLast(level)
            while (ambient.size > 50) ambient.removeFirst()
            updateNoise()
        }
        if (waiting >= 400) {
            waiting = 0
            return WakeSegment(problem = if (noiseFloor > .004f) SampleProblem.NOISY else SampleProblem.NO_SPEECH)
        }
        return null
    }

    private fun remember(frame: ShortArray) {
        lead.addLast(frame.copyOf())
        while (lead.size > 15) lead.removeFirst()
    }
    private fun updateNoise() {
        val sorted = ambient.sorted()
        noiseFloor = sorted[(sorted.size / 5).coerceAtMost(sorted.lastIndex)].coerceAtLeast(.0001f)
    }
    private fun reset() {
        utterance.clear(); lead.clear(); onset = 0; quiet = 0; waiting = 0
        phase = WakeCapturePhase.LISTENING
    }
    companion object {
        fun meter(rms: Float): Float = if (rms <= 0 || !rms.isFinite()) 0f else
            ((20f * log10(rms) + 75f) / 65f).coerceIn(0f, 1f)
        fun rms(pcm: ShortArray): Float = sqrt(pcm.sumOf { val n = it / 32768.0; n * n } / pcm.size).toFloat()
    }
}
