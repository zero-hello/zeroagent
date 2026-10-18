package me.rerere.zeroagent.assistant.wake

import kotlinx.serialization.Serializable
import me.rerere.zeroagent.assistant.VoiceAssistantSettings
import kotlin.math.*

/** Personal acoustic matching, not speech-to-text or neural model fine-tuning. */
interface PersonalWakeEngine {
    val id: String
    val supportsPersonalization: Boolean
    fun train(phrase: String, language: String, samples: List<ShortArray>): WakeProfile
    fun valid(profile: WakeProfile): Boolean
    fun detect(profile: WakeProfile, pcm: ShortArray): Boolean
}

@Serializable
data class WakeTemplate(val seconds: Float, val features: List<List<Float>>)
@Serializable
data class WakeProfile(
    val version: Int = 1,
    val engine: String = "local-acoustic-v1",
    val phrase: String,
    val language: String,
    val threshold: Float,
    val templates: List<WakeTemplate>,
)

enum class SampleProblem { TOO_QUIET, TOO_SHORT, TOO_LONG, NOISY, CLIPPED, INCONSISTENT, NO_SPEECH, TONAL }
class WakeSampleException(val problem: SampleProblem) : IllegalArgumentException(problem.name)
data class QualifiedSample(val start: Int, val end: Int, val seconds: Float, val snrDb: Float)

object WakeSampleQuality {
    const val RATE = 16000
    const val FRAME = 320
    const val MIN_SIGNAL = .0006f
    fun inspect(pcm: ShortArray): QualifiedSample {
        if (pcm.size < RATE / 3) throw WakeSampleException(SampleProblem.TOO_SHORT)
        if (pcm.size > RATE * 6) throw WakeSampleException(SampleProblem.TOO_LONG)
        if (pcm.count { abs(it.toInt()) >= 32600 }.toFloat() / pcm.size > .01f) throw WakeSampleException(SampleProblem.CLIPPED)
        val rms = (0 until pcm.size - FRAME + 1 step FRAME).map { begin ->
            sqrt((begin until begin + FRAME).sumOf { val v = pcm[it] / 32768.0; v * v } / FRAME).toFloat()
        }
        val noise = rms.sorted()[(rms.size / 10).coerceAtMost(rms.lastIndex)].coerceAtLeast(.0001f)
        val peak = rms.maxOrNull() ?: 0f
        if (peak < MIN_SIGNAL) throw WakeSampleException(SampleProblem.TOO_QUIET)
        val speech = rms.indices.filter { rms[it] > max(MIN_SIGNAL * .8f, noise * 2.2f) }
        if (speech.isEmpty()) throw WakeSampleException(SampleProblem.NOISY)
        val first = speech.first(); val last = speech.last()
        val seconds = (last - first + 1) * FRAME.toFloat() / RATE
        if (seconds < .32f) throw WakeSampleException(SampleProblem.TOO_SHORT)
        if (seconds > 2.8f) throw WakeSampleException(SampleProblem.TOO_LONG)
        val energy = speech.map { rms[it] }.average().toFloat()
        val snr = (20 * log10((energy / noise).toDouble())).toFloat()
        if (snr < 9f || speech.size < (last - first + 1) * .3f) throw WakeSampleException(SampleProblem.NOISY)
        val voicedLevels = speech.map { rms[it] }
        val mean = voicedLevels.average()
        val variation = sqrt(voicedLevels.sumOf { (it - mean).pow(2) } / voicedLevels.size) / mean
        if (variation < .06) throw WakeSampleException(SampleProblem.TONAL)
        val periodic = speech.count { frame -> periodicity(pcm, frame * FRAME) > .5f }
        if (periodic < speech.size * .2f) throw WakeSampleException(SampleProblem.NOISY)
        return QualifiedSample(max(0, first * FRAME - FRAME), min(pcm.size, (last + 2) * FRAME), seconds, snr)
    }
    private fun periodicity(pcm: ShortArray, start: Int): Float {
        var best = 0.0
        val mean = (start until start + FRAME).sumOf { pcm[it].toDouble() } / FRAME
        for (lag in 32..240 step 4) {
            var cross = 0.0; var a2 = 0.0; var b2 = 0.0
            for (i in 0 until FRAME - lag) {
                val a = pcm[start + i] - mean; val b = pcm[start + i + lag] - mean
                cross += a * b; a2 += a * a; b2 += b * b
            }
            if (a2 > 0 && b2 > 0) best = max(best, cross / sqrt(a2 * b2))
        }
        return best.toFloat()
    }
}

object AcousticWakeEngine : PersonalWakeEngine {
    override val id = "local-acoustic-v1"
    override val supportsPersonalization = true
    override fun train(phrase: String, language: String, samples: List<ShortArray>): WakeProfile {
        require(phrase == VoiceAssistantSettings.FIXED_WAKE_PHRASE)
        require(samples.size == 5 || samples.size == 10)
        val templates = samples.map(::template)
        val durations = templates.map { it.seconds }
        if (durations.max() / durations.min() > 2.2f) throw WakeSampleException(SampleProblem.INCONSISTENT)
        val distances = templates.indices.map { i ->
            templates.indices.filter { it != i }.minOf { j -> distance(templates[i].features, templates[j].features) }
        }
        if (distances.any { !it.isFinite() || it > 1.2f }) throw WakeSampleException(SampleProblem.INCONSISTENT)
        val radius = (distances.max() * 1.3f + .08f).coerceIn(.30f, .95f)
        return WakeProfile(phrase = VoiceAssistantSettings.FIXED_WAKE_PHRASE, language = language, threshold = radius, templates = templates)
    }
    override fun detect(profile: WakeProfile, pcm: ShortArray): Boolean {
        if (!valid(profile)) return false
        val sample = try { template(pcm) } catch (_: WakeSampleException) { return false }
        val matches = profile.templates.filter { sample.seconds / it.seconds in .60f..1.65f }
            .map { distance(sample.features, it.features) }.sorted()
        return matches.size >= 2 && matches[1] <= profile.threshold
    }
    override fun valid(profile: WakeProfile): Boolean = profile.version == 1 && profile.engine == id &&
        profile.phrase == VoiceAssistantSettings.FIXED_WAKE_PHRASE && profile.threshold.isFinite() && profile.threshold in .1f..1.3f &&
        profile.templates.size in setOf(5, 10) && profile.templates.all { t ->
            t.seconds.isFinite() && t.seconds in .3f..3f && t.features.size in 20..300 &&
                t.features.all { frame -> frame.size == 13 && frame.all { it.isFinite() && abs(it) < 20f } }
        }
    fun consistent(pcm: ShortArray, previous: List<ShortArray>): Boolean {
        if (previous.isEmpty()) return true
        val next = template(pcm)
        return previous.any { old -> val t = template(old); next.seconds / t.seconds in .55f..1.8f && distance(next.features, t.features) <= 1.2f }
    }
    private fun template(pcm: ShortArray): WakeTemplate {
        val q = WakeSampleQuality.inspect(pcm)
        val features = Mfcc.extract(pcm.copyOfRange(q.start, q.end))
        return WakeTemplate(q.seconds, features.map { it.toList() })
    }
    internal fun distance(a: List<List<Float>>, b: List<List<Float>>): Float {
        if (a.isEmpty() || b.isEmpty()) return Float.POSITIVE_INFINITY
        var previous = FloatArray(b.size + 1) { Float.POSITIVE_INFINITY }; previous[0] = 0f
        var next = FloatArray(b.size + 1)
        val band = max(abs(a.size - b.size) + 1, max(a.size, b.size) / 3)
        for (i in 1..a.size) {
            next.fill(Float.POSITIVE_INFINITY)
            for (j in max(1, i - band)..min(b.size, i + band)) {
                var cost = 0f
                for (k in 0 until 13) { val d = a[i - 1][k] - b[j - 1][k]; cost += d * d }
                next[j] = sqrt(cost / 13f) + minOf(previous[j], previous[j - 1], next[j - 1])
            }
            val swap = previous; previous = next; next = swap
        }
        return previous[b.size] / max(a.size, b.size)
    }
}

/** 25ms Hamming frames, 10ms hop, 26 mel filters, 13 cepstra; bounded per utterance. */
private object Mfcc {
    private const val FFT = 512
    private const val WINDOW = 400
    private const val HOP = 160
    private val window = DoubleArray(WINDOW) { .54 - .46 * cos(2 * PI * it / (WINDOW - 1)) }
    private fun mel(hz: Double) = 2595 * log10(1 + hz / 700)
    private fun hz(mel: Double) = 700 * (10.0.pow(mel / 2595) - 1)
    private val bins = IntArray(28) { i -> floor((FFT + 1) * hz(mel(80.0) + i * (mel(7600.0) - mel(80.0)) / 27) / 16000).toInt().coerceIn(0, FFT / 2) }
    private val dct = Array(13) { c -> DoubleArray(26) { f -> cos(PI * (c + 1) * (f + .5) / 26) } }
    fun extract(pcm: ShortArray): Array<FloatArray> {
        require(pcm.size <= 16000 * 6)
        val count = max(1, (pcm.size - WINDOW) / HOP + 1)
        val frames = Array(count) { FloatArray(13) }
        val re = DoubleArray(FFT); val im = DoubleArray(FFT); val power = DoubleArray(FFT / 2 + 1)
        val energies = DoubleArray(26)
        for (f in 0 until count) {
            re.fill(0.0); im.fill(0.0)
            for (i in 0 until WINDOW) {
                val at = f * HOP + i
                val sample = pcm.getOrElse(at) { 0 } / 32768.0
                val prior = if (at > 0) pcm.getOrElse(at - 1) { 0 } / 32768.0 else 0.0
                re[i] = (sample - .97 * prior) * window[i]
            }
            fft(re, im)
            for (i in power.indices) power[i] = (re[i] * re[i] + im[i] * im[i]) / FFT
            for (m in 0 until 26) {
                var energy = 0.0
                val left = bins[m]; val center = max(left + 1, bins[m + 1]); val right = max(center + 1, bins[m + 2])
                for (k in left until min(center, power.size)) energy += power[k] * (k - left).toDouble() / (center - left)
                for (k in center..min(right, power.lastIndex)) energy += power[k] * (right - k).toDouble() / (right - center)
                energies[m] = ln(max(energy, 1e-12))
            }
            for (c in 0 until 13) frames[f][c] = energies.indices.sumOf { energies[it] * dct[c][it] }.toFloat()
        }
        for (c in 0 until 13) {
            val mean = frames.map { it[c].toDouble() }.average()
            val std = sqrt(frames.sumOf { (it[c] - mean).pow(2) } / frames.size).coerceAtLeast(.15)
            for (f in frames.indices) frames[f][c] = ((frames[f][c] - mean) / std).toFloat().coerceIn(-8f, 8f)
        }
        return frames
    }
    private fun fft(re: DoubleArray, im: DoubleArray) {
        var j = 0
        for (i in 1 until FFT) {
            var bit = FFT shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val r = re[i]; re[i] = re[j]; re[j] = r; val q = im[i]; im[i] = im[j]; im[j] = q }
        }
        var length = 2
        while (length <= FFT) {
            val ar = cos(-2 * PI / length); val ai = sin(-2 * PI / length)
            for (start in 0 until FFT step length) {
                var wr = 1.0; var wi = 0.0
                for (k in 0 until length / 2) {
                    val a = start + k; val b = a + length / 2
                    val tr = wr * re[b] - wi * im[b]; val ti = wr * im[b] + wi * re[b]
                    re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti
                    val next = wr * ar - wi * ai; wi = wr * ai + wi * ar; wr = next
                }
            }
            length = length shl 1
        }
    }
}

/** Register a real adapter here; callers never fall back to another engine silently. */
object WakeEngines {
    val available: List<PersonalWakeEngine> = listOf(AcousticWakeEngine)
    fun find(id: String): PersonalWakeEngine? = available.firstOrNull { it.id == id }
    fun train(engine: PersonalWakeEngine, phrase: String, language: String, samples: List<ShortArray>): WakeProfile {
        check(engine.supportsPersonalization) { "wake_engine_does_not_support_personalization" }
        return engine.train(phrase, language, samples).also { require(engine.valid(it)) }
    }
}
