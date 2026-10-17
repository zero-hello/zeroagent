package me.rerere.zeroagent.data.ai.realtime

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

internal data class VoiceOutputRoute(val id: Int, val type: Int)
internal fun defaultVoiceOutputRoute(routes: List<VoiceOutputRoute>, current: Int?): Int? {
    fun external(route: VoiceOutputRoute) = route.type !in setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
    return routes.firstOrNull { it.id == current && external(it) }?.id
        ?: routes.firstOrNull { external(it) }?.id
        ?: routes.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.id
        ?: routes.firstOrNull()?.id
}

internal data class VoiceLevels(val amplitude: Float = 0f, val brightness: Float = 0f)
internal fun pcmLevels(bytes: ByteArray, count: Int = bytes.size): VoiceLevels {
    if (count < 2) return VoiceLevels()
    var energy = 0.0; var changes = 0.0; var previous = 0.0
    val samples = count / 2
    for (i in 0 until samples) {
        val value = (((bytes[i * 2].toInt() and 255) or (bytes[i * 2 + 1].toInt() shl 8)).toShort().toDouble()) / 32768.0
        energy += value * value; changes += abs(value - previous); previous = value
    }
    return VoiceLevels(sqrt(energy / samples).toFloat().coerceIn(0f, 1f), (changes / samples * 3).toFloat().coerceIn(0f, 1f))
}

/** Two independent IO loops keep capture running during playback. PCM never goes through TTS. */
internal class RealtimeAudio(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCapture: (ByteArray, VoiceLevels) -> Unit,
    private val onPlayback: (VoiceLevels) -> Unit,
    private val onError: () -> Unit,
    private val onDrained: () -> Unit,
    private val onRoutesChanged: (List<VoiceOutputRoute>, Int?) -> Unit = { _, _ -> },
) {
    companion object {
        private val ownershipLock = Any()
        private var activeOwner: RealtimeAudio? = null
    }
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private val generation = AtomicInteger()
    private val playbackLock = Any()
    private data class Chunk(val pcm: ByteArray, val generation: Int)
    private val queue = Channel<Chunk>(100)
    private var captureJob: Job? = null
    private var playJob: Job? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private var previousMode: Int? = null
    private var previousDevice: AudioDeviceInfo? = null
    private val devicesCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = publishRoutes()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = publishRoutes()
    }
    private val communicationListener = AudioManager.OnCommunicationDeviceChangedListener { publishRoutes() }
    private fun publishRoutes() {
        if (stopped) return
        val routes = runCatching { audioManager.availableCommunicationDevices.map { VoiceOutputRoute(it.id, it.type) } }.getOrDefault(emptyList())
        onRoutesChanged(routes, runCatching { audioManager.communicationDevice?.id }.getOrNull())
    }
    fun selectOutputRoute(id: Int): Boolean = synchronized(ownershipLock) {
        if (stopped || activeOwner !== this) return false
        val device = audioManager.availableCommunicationDevices.firstOrNull { it.id == id } ?: return false
        runCatching { audioManager.setCommunicationDevice(device) }.getOrDefault(false).also { publishRoutes() }
    }
    @Volatile private var stopped = false
    @Volatile var muted = false; private set
    @Volatile var inputMuted = false
    @Volatile var playing = false; private set
    @Volatile var playedBytes = 0; private set
    var echoCancellationAvailable = false; private set

    @Synchronized
    @SuppressLint("MissingPermission")
    fun start(inputRate: Int, outputRate: Int) {
        check(!stopped)
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ if (it == AudioManager.AUDIOFOCUS_LOSS || it == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onError() }, android.os.Handler(android.os.Looper.getMainLooper())).build()
        check(audioManager.requestAudioFocus(focus!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        synchronized(ownershipLock) {
            previousMode = activeOwner?.previousMode ?: audioManager.mode
            previousDevice = if (activeOwner != null) activeOwner?.previousDevice else audioManager.communicationDevice
            activeOwner = this
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        val recordMin = AudioRecord.getMinBufferSize(inputRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(recordMin > 0)
        recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(inputRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(max(recordMin, inputRate / 5)).build().also { check(it.state == AudioRecord.STATE_INITIALIZED) }
        val session = recorder!!.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) echo = runCatching { AcousticEchoCanceler.create(session)?.apply { enabled = true } }.getOrNull()
        if (NoiseSuppressor.isAvailable()) noise = runCatching { NoiseSuppressor.create(session)?.apply { enabled = true } }.getOrNull()
        echoCancellationAvailable = echo?.enabled == true
        val playMin = AudioTrack.getMinBufferSize(outputRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(playMin > 0)
        player = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(AudioFormat.Builder()
            .setSampleRate(outputRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(max(playMin, outputRate / 10)).build().also { check(it.state == AudioTrack.STATE_INITIALIZED) }
        synchronized(playbackLock) { player?.setVolume(if (muted) 0f else 1f); player?.play() }
        audioManager.addOnCommunicationDeviceChangedListener(context.mainExecutor, communicationListener)
        audioManager.registerAudioDeviceCallback(devicesCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        val routes = audioManager.availableCommunicationDevices
        defaultVoiceOutputRoute(routes.map { VoiceOutputRoute(it.id, it.type) }, audioManager.communicationDevice?.id)?.let { selectOutputRoute(it) }
        publishRoutes()
        recorder!!.startRecording()
        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(inputRate / 50 * 2) // 20 ms, mono PCM16 LE
            try {
                while (isActive && !stopped) {
                    val size = recorder?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: break
                    if (size < 0) error("capture")
                    if (size > 0) {
                        if (inputMuted) onCapture(ByteArray(size), VoiceLevels())
                        else onCapture(buffer.copyOf(size), pcmLevels(buffer, size))
                    }
                }
            } catch (e: Exception) { if (isActive && !stopped) onError() }
        }
        playJob = scope.launch(Dispatchers.IO) {
            try {
                for (chunk in queue) {
                    var offset = 0
                    while (offset < chunk.pcm.size && isActive && !stopped && chunk.generation == generation.get()) {
                        val count = min(outputRate / 50 * 2, chunk.pcm.size - offset)
                        playing = true
                        val wrote = synchronized(playbackLock) {
                            if (chunk.generation != generation.get()) 0
                            else (player?.write(chunk.pcm, offset, count, AudioTrack.WRITE_NON_BLOCKING) ?: 0).also { if (it > 0) playedBytes += it }
                        }
                        if (wrote < 0) error("playback")
                        if (wrote == 0) { delay(3); continue }
                        if (chunk.generation != generation.get()) break
                        onPlayback(if (muted) VoiceLevels() else pcmLevels(chunk.pcm.copyOfRange(offset, offset + wrote)))
                        offset += wrote
                    }
                    if (queue.isEmpty && !stopped) {
                        // AudioTrack may still own buffered frames after the application queue empties.
                        val target = playedBytes / 2
                        while (isActive && !stopped && chunk.generation == generation.get() &&
                            (player?.playbackHeadPosition ?: target) < target) delay(10)
                        if (queue.isEmpty) { playing = false; onPlayback(VoiceLevels()); onDrained() }
                    }
                }
            } catch (e: Exception) { if (isActive && !stopped) onError() }
        }
    }

    fun enqueue(pcm: ByteArray): Boolean {
        if (stopped || pcm.size % 2 != 0) return false
        return synchronized(playbackLock) {
            queue.trySend(Chunk(pcm, generation.get())).isSuccess.also { if (it) playing = true }
        }
    }
    fun playedMillisSince(byteOffset: Int, sampleRate: Int): Int =
        (((player?.playbackHeadPosition ?: 0).toLong() * 2 - byteOffset).coerceAtLeast(0) * 1000 / (sampleRate * 2)).toInt()
    fun setOutputMuted(value: Boolean) {
        muted = value
        synchronized(playbackLock) { runCatching { player?.setVolume(if (value) 0f else 1f) } }
        if (value) onPlayback(VoiceLevels())
    }
    fun interrupt() {
        generation.incrementAndGet()
        while (queue.tryReceive().isSuccess) Unit
        synchronized(playbackLock) {
            runCatching { player?.pause(); player?.flush(); if (!stopped) player?.play() }
            playedBytes = 0
        }
        playing = false; onPlayback(VoiceLevels())
    }
    @Synchronized
    fun close() {
        if (stopped) return
        stopped = true
        runCatching { audioManager.unregisterAudioDeviceCallback(devicesCallback) }
        runCatching { audioManager.removeOnCommunicationDeviceChangedListener(communicationListener) }
        generation.incrementAndGet(); queue.close()
        runCatching { recorder?.stop() }; runCatching { player?.pause(); player?.flush() }
        captureJob?.cancel(); playJob?.cancel()
        // Releasing a recorder while its blocking read is still unwinding can race native audio.
        scope.launch(Dispatchers.IO + NonCancellable) {
            captureJob?.join(); playJob?.join()
            runCatching { echo?.release() }; runCatching { noise?.release() }
            runCatching { recorder?.release() }; runCatching { player?.release() }
            focus?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
            synchronized(ownershipLock) {
                if (activeOwner === this@RealtimeAudio) {
                    runCatching {
                        val previous = previousDevice?.let { old -> audioManager.availableCommunicationDevices.firstOrNull { it.id == old.id } }
                        if (previous != null) audioManager.setCommunicationDevice(previous) else audioManager.clearCommunicationDevice()
                    }
                    previousMode?.let { runCatching { audioManager.mode = it } }
                    activeOwner = null
                }
            }
            recorder = null; player = null
        }
    }
}
