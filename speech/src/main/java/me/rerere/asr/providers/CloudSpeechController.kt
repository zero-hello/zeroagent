package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.rerere.asr.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Shared capture lifecycle; protocol differences stay in CloudSpeechProtocol. No TTS path. */
class CloudSpeechController(
    private val context: Context,
    client: OkHttpClient,
    private val provider: ASRProviderSetting.Cloud,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(ASRState(isAvailable = true))
    override val state = mutable.asStateFlow()
    private val epoch = AtomicInteger()
    private val micLock = Any()
    private val textLock = Any()
    private var recorder: AudioRecord? = null
    private var recordJob: Job? = null
    private var socket: WebSocket? = null
    private var call: Call? = null
    private var watchdog: Job? = null
    private var protocol: CloudSpeechProtocol? = null
    private var callback: ((String) -> Unit)? = null
    private val segments = linkedMapOf<String, CloudSpeechEvent.Transcript>()
    private var heldAudio: ByteArray? = null
    private var buffer = ByteArrayOutputStream()
    private var disposed = false
    private var finalizationSent = false
    private val client = client.newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).build()

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (disposed || state.value.isRecording) return
        val revision = epoch.incrementAndGet()
        callback = onTranscriptChange
        synchronized(textLock) { segments.clear() }
        buffer = ByteArrayOutputStream(); heldAudio = null; finalizationSent = false
        mutable.value = ASRState(status = ASRStatus.Connecting, isAvailable = true)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail(revision, "permission denied: microphone"); return
        }
        val language = provider.language.ifBlank { context.resources.configuration.locales[0].toLanguageTag() }
        val sessionProtocol = CloudSpeechProtocol(provider.copy(language = language))
        protocol = sessionProtocol
        if (!provider.service.streaming) { capture(revision); return }
        watchdog = scope.launch { delay(15000); if (valid(revision) && state.value.status == ASRStatus.Connecting) fail(revision, "connection timeout") }
        try {
            socket = client.newWebSocket(sessionProtocol.request(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!valid(revision)) { webSocket.cancel(); return }
                    socket = webSocket
                    watchdog?.cancel(); capture(revision)
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!valid(revision)) return
                    try { sessionProtocol.parse(text).forEach { update(revision, it) } }
                    catch (_: Exception) { fail(revision, "unsupported speech response") }
                }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onMessage(webSocket, bytes.utf8())
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                    if (valid(revision)) {
                        if (state.value.status == ASRStatus.Stopping && finalizationSent && code == 1000) complete(revision)
                        else fail(revision, "speech connection closed: $code")
                    }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (valid(revision)) fail(revision, response?.let { "HTTP ${it.code}" } ?: "speech connection failed")
                    response?.close()
                }
            })
        } catch (_: Exception) { fail(revision, "invalid request: speech endpoint or credentials") }
    }

    @SuppressLint("MissingPermission")
    private fun capture(revision: Int) {
        recordJob = scope.launch(Dispatchers.IO) {
            var local: AudioRecord? = null
            try {
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                val microphone = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(minimum, 6400)).build()
                local = microphone
                check(microphone.state == AudioRecord.STATE_INITIALIZED)
                val started = synchronized(micLock) {
                    if (!valid(revision) || state.value.status == ASRStatus.Stopping) false else {
                        recorder = microphone; microphone.startRecording(); true
                    }
                }
                if (!started) return@launch
                withContext(Dispatchers.Main.immediate) {
                    if (valid(revision) && state.value.status != ASRStatus.Stopping) mutable.update { it.copy(status = ASRStatus.Listening) }
                }
                // AssemblyAI requires >=50 ms; 100 ms is accepted by all three stream APIs.
                val frame = ByteArray(3200)
                while (isActive && valid(revision) && state.value.status == ASRStatus.Listening) {
                    val size = microphone.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                    if (size < 0) { if (state.value.status == ASRStatus.Listening) fail(revision, "audio_error"); break }
                    if (size == 0) continue
                    mutable.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(calculateRmsAmplitude(frame, size))) }
                    val pcm = frame.copyOf(size)
                    if (provider.service.streaming) {
                        val ws = socket ?: break
                        if (ws.queueSize() > 128000) { fail(revision, "speech connection cannot keep up"); break }
                        val sent = if (provider.service == CloudSpeechService.ELEVENLABS) {
                            val previous = heldAudio
                            heldAudio = pcm
                            previous == null || ws.send(checkNotNull(protocol).audioJson(previous))
                        } else ws.send(pcm.toByteString())
                        if (!sent) { fail(revision, "speech connection failed"); break }
                    } else {
                        buffer.write(pcm)
                        // Short-audio REST APIs are capped below their 60-second limit.
                        if (buffer.size() >= 16000 * 2 * 55) { scope.launch { stop() }; break }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (state.value.status != ASRStatus.Stopping) fail(revision, "audio_error") }
            finally {
                synchronized(micLock) {
                    runCatching { local?.stop() }; runCatching { local?.release() }
                    if (recorder === local) recorder = null
                }
            }
        }
    }

    override fun stop() {
        if (disposed || !state.value.isRecording || state.value.status == ASRStatus.Stopping) return
        val revision = epoch.get()
        mutable.update { it.copy(status = ASRStatus.Stopping) }
        stopMic()
        scope.launch {
            recordJob?.join()
            if (!valid(revision)) return@launch
            finalizationSent = true
            if (provider.service.streaming) {
                val sent = if (provider.service == CloudSpeechService.ELEVENLABS) {
                    val last = heldAudio
                    if (last == null) { complete(revision); return@launch }
                    socket?.send(checkNotNull(protocol).audioJson(last, commit = true))
                } else socket?.send(checkNotNull(protocol).finishMessage())
                if (sent != true) { fail(revision, "speech connection failed"); return@launch }
                watchdog?.cancel()
                watchdog = scope.launch { delay(8000); if (valid(revision)) fail(revision, "speech final transcript timeout") }
            } else {
                val pcm = buffer.toByteArray(); buffer.reset()
                if (pcm.isEmpty()) { complete(revision); return@launch }
                try {
                    val updates = withContext(Dispatchers.IO) {
                        val requestCall = client.newCall(checkNotNull(protocol).request(pcm)); call = requestCall
                        requestCall.execute().use { response ->
                            check(response.isSuccessful) { cloudSpeechHttpError(response.code, response.peekBody(8192).string()) }
                            checkNotNull(protocol).parse(response.body.string())
                        }
                    }
                    if (valid(revision)) { updates.forEach { update(revision, it) }; complete(revision) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val reason = when {
                        e is java.net.SocketTimeoutException -> "speech request timeout"
                        e.message?.startsWith("HTTP ") == true -> e.message!!
                        e is kotlinx.serialization.SerializationException -> "unsupported speech response"
                        e is java.io.IOException -> "speech connection failed"
                        e is IllegalArgumentException -> "invalid request: speech settings"
                        else -> "speech request failed"
                    }
                    fail(revision, reason)
                }
                finally { call = null }
            }
        }
    }
    override fun pauseCapture() = stop()

    private fun update(revision: Int, event: CloudSpeechEvent) {
        when (event) {
            is CloudSpeechEvent.Transcript -> {
                val text = synchronized(textLock) {
                    if (!valid(revision)) return
                    updateCloudTranscript(segments, event)
                }
                scope.launch {
                    if (!valid(revision)) return@launch
                    mutable.update { it.copy(transcript = text) }; callback?.invoke(text)
                    if (provider.service == CloudSpeechService.ELEVENLABS && finalizationSent && event.final) complete(revision)
                }
            }
            CloudSpeechEvent.Complete -> if (finalizationSent) complete(revision)
            is CloudSpeechEvent.Error -> fail(revision, event.code)
        }
    }
    private fun valid(revision: Int) = !disposed && epoch.get() == revision
    private fun stopMic() = synchronized(micLock) { runCatching { recorder?.stop() } }.let { Unit }
    private fun complete(revision: Int) {
        scope.launch {
            if (!valid(revision)) return@launch
            epoch.incrementAndGet(); watchdog?.cancel(); stopMic(); socket?.close(1000, "done"); socket = null
            recordJob?.join(); mutable.update { it.copy(status = ASRStatus.Idle, errorMessage = null, amplitudes = emptyList()) }
        }
    }
    private fun fail(revision: Int, error: String) {
        scope.launch {
            if (!valid(revision)) return@launch
            epoch.incrementAndGet(); watchdog?.cancel(); mutable.update { it.copy(status = ASRStatus.Stopping) }
            stopMic(); socket?.cancel(); call?.cancel(); recordJob?.join()
            mutable.update { it.copy(status = ASRStatus.Error, errorMessage = error, amplitudes = emptyList()) }
        }
    }
    override fun dispose() {
        disposed = true; epoch.incrementAndGet(); watchdog?.cancel(); stopMic(); socket?.cancel(); call?.cancel()
        recordJob?.cancel(); scope.cancel()
    }
}
