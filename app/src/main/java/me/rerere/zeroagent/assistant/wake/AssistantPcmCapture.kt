package me.rerere.zeroagent.assistant.wake

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** Only stop() is cross-thread; release stays in the capture coroutine after callbacks unwind. */
class AssistantPcmCapture(private val context: Context, private val communication: Boolean = false) {
    private val stopped = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile var echoCancellation = false; private set
    @SuppressLint("MissingPermission")
    suspend fun read(keepGoing: () -> Boolean = { true }, frame: suspend (ShortArray, Float) -> Unit) = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("assistant_microphone_permission")
        var echo: AcousticEchoCanceler? = null; var noise: NoiseSuppressor? = null
        try {
            val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT); check(min > 0) { "assistant_audio_unavailable" }
            val record = AudioRecord.Builder().setAudioSource(if (communication) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(maxOf(min * 2, 10240)).build()
            recorder = record
            check(record.state == AudioRecord.STATE_INITIALIZED) { "assistant_audio_unavailable" }
            if (communication) {
                if (AcousticEchoCanceler.isAvailable()) echo = runCatching { AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull()
                if (NoiseSuppressor.isAvailable()) noise = runCatching { NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull()
                echoCancellation = echo?.enabled == true
            }
            if (stopped.get()) return@withContext
            record.startRecording()
            val buffer = ShortArray(320)
            var lastRead = android.os.SystemClock.elapsedRealtime()
            var filled = 0
            while (isActive && !stopped.get() && keepGoing()) {
                val count = record.read(buffer, filled, buffer.size - filled, AudioRecord.READ_NON_BLOCKING)
                if (count < 0) { if (stopped.get()) break else error("assistant_audio_unavailable") }
                if (count == 0) {
                    if (android.os.SystemClock.elapsedRealtime() - lastRead > 3000) error("assistant_audio_unavailable")
                    delay(10); continue
                }
                lastRead = android.os.SystemClock.elapsedRealtime()
                filled += count
                if (filled == buffer.size) {
                    if (record.activeRecordingConfiguration?.isClientSilenced == true) error("assistant_audio_silenced")
                    val pcm = buffer.copyOf(); filled = 0
                    val rms = sqrt(pcm.sumOf { val n = it / 32768.0; n * n } / pcm.size).toFloat()
                    frame(pcm, rms)
                }
            }
        } finally {
            stopped.set(true)
            runCatching { recorder?.stop() }; runCatching { echo?.release() }; runCatching { noise?.release() }
            runCatching { recorder?.release() }; recorder = null
        }
    }
    fun stop() { stopped.set(true); runCatching { recorder?.stop() } }
}

/** A session owns one microphone lease; rejected attempts never become enrollment samples. */
suspend fun recordWakeSamples(
    context: Context,
    timeoutMillis: Long,
    onLevel: suspend (Float, WakeCapturePhase) -> Unit,
    onSample: suspend (ShortArray) -> Boolean,
    onRejected: suspend (SampleProblem) -> Unit,
    onMeter: suspend (WakeCaptureMeter) -> Unit = {},
) {
    var lease: me.rerere.zeroagent.assistant.AssistantMicrophone.Lease? = null
    val capture = AssistantPcmCapture(context)
    var segmenter = AdaptiveWakeSegmenter()
    var restUntil = 0L
    var keepGoing = true
    var lastLevel = 0L
    try {
        withTimeout(3000) { lease = me.rerere.zeroagent.assistant.AssistantMicrophone.acquire() }
        withTimeout(timeoutMillis) {
            capture.read(keepGoing = { keepGoing }) { pcm, level ->
                val now = System.nanoTime()
                val resting = now < restUntil
                if (!resting && restUntil != 0L) { segmenter = AdaptiveWakeSegmenter(); restUntil = 0L }
                val segment = if (resting) null else segmenter.accept(pcm)
                val phase = if (resting) WakeCapturePhase.RESTING else segmenter.phase
                if (now - lastLevel >= 100_000_000L) {
                    onLevel(level, phase)
                    onMeter(WakeCaptureMeter(level, segmenter.noiseFloor, segmenter.startThreshold, phase))
                    lastLevel = now
                }
                segment?.problem?.let { onRejected(it) }
                segment?.pcm?.let { clip ->
                    try {
                        WakeSampleQuality.inspect(clip)
                        onLevel(level, WakeCapturePhase.SAVING)
                        keepGoing = onSample(clip)
                        // Keep draining the microphone during the gap so stale speech is not reused.
                        if (keepGoing) restUntil = System.nanoTime() + 700_000_000L
                    } catch (quality: WakeSampleException) { onRejected(quality.problem) }
                }
            }
        }
    } finally { capture.stop(); lease?.close() }
}

suspend fun recordWakeSample(context: Context, onLevel: (Float) -> Unit): ShortArray {
    var sample: ShortArray? = null
    recordWakeSamples(context, 15_000, { level, _ -> onLevel(level) }, { sample = it; false }, { throw WakeSampleException(it) })
    return sample ?: throw WakeSampleException(SampleProblem.NO_SPEECH)
}
