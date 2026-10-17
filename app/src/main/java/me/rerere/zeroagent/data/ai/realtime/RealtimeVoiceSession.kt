package me.rerere.zeroagent.data.ai.realtime

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal enum class RealtimePhase { CONNECTING, LISTENING, THINKING, SPEAKING, MUTED, CLOSED, ERROR }
internal data class RealtimeVoiceState(
    val phase: RealtimePhase = RealtimePhase.CONNECTING,
    val userTranscript: String = "", val assistantTranscript: String = "",
    val input: VoiceLevels = VoiceLevels(), val output: VoiceLevels = VoiceLevels(),
    val inputMuted: Boolean = false, val outputMuted: Boolean = false,
    val error: String? = null, val echoCancellation: Boolean = true,
    val responding: Boolean = false,
    val outputRoutes: List<VoiceOutputRoute> = emptyList(),
    val selectedOutputRoute: Int? = null,
    val routeUnavailable: Boolean = false,
)

/** Lifecycle-owned session. No provider keys, endpoints, microphone frames or transcripts are logged. */
internal class RealtimeVoiceSession(context: Context, client: OkHttpClient) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(RealtimeVoiceState())
    val state = mutable.asStateFlow()
    private var socket: WebSocket? = null
    private var assistantMicLease: me.rerere.zeroagent.assistant.AssistantMicrophone.Lease? = null
    private val audioStateLock = Any()
    @Volatile private var audio: RealtimeAudio? = null
    private var protocol: RealtimeProtocol? = null
    private val terminal = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private var timeout: Job? = null
    @Volatile private var responseActive = false
    @Volatile private var responseDone = false
    @Volatile private var suppressAudio = false
    @Volatile private var item: String? = null
    private var itemByteOffset = 0
    @Volatile private var cancelledItem: String? = null
    private val activity = VoiceActivityGate()
    @Volatile private var lastProgressAt = System.nanoTime()
    private var userTurn = true
    private var assistantTurn = true
    // Keep the configured transport/proxy, but never run text-chat logging/auth interceptors
    // against a websocket URL (Gemini's authentication uses a query parameter).
    private val client = client.newBuilder().apply {
        interceptors().clear(); networkInterceptors().clear()
    }.followRedirects(false).followSslRedirects(false).readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()

    fun start(model: Model, provider: ProviderSetting, instructions: String) {
        if (terminal.get() || socket != null) return
        scope.launch {
            try {
                val connection = RealtimeConnections.create(model, provider)
                withTimeout(3000) {
                    val lease = me.rerere.zeroagent.assistant.AssistantMicrophone.acquire()
                    synchronized(audioStateLock) {
                        if (terminal.get()) lease.close() else assistantMicLease = lease
                    }
                }
                if (terminal.get()) return@launch
                protocol = connection.protocol
                val request = Request.Builder().url(connection.url).apply {
                    connection.headers.forEach { (k, v) -> header(k, v) }
                }.build()
                timeout = scope.launch { delay(15000); if (!ready.get()) fail("connection_timeout") }
                socket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (terminal.get()) { webSocket.close(1000, "closed"); return }
                        if (!webSocket.send(connection.protocol.setup(model, instructions).toString())) fail("connection_failed")
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) = receive(text)
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(bytes.utf8())
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        fail(when (response?.code) {
                            401, 403 -> "authentication_failed"
                            404, 400, 405, 426 -> "unsupported_endpoint"
                            429 -> "rate_limited"
                            else -> "connection_failed"
                        })
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (!terminal.get()) fail(if (ready.get()) "connection_closed" else "unsupported_endpoint")
                    }
                })
            } catch (_: TimeoutCancellationException) { fail("audio_error")
            } catch (e: CancellationException) { throw e
            } catch (e: IllegalArgumentException) {
                fail(e.message?.takeIf { it in setOf("unsupported_protocol", "unsupported_auth", "missing_key", "missing_model", "secure_endpoint_required") } ?: "invalid_endpoint")
            } catch (_: Exception) { fail("connection_failed") }
        }
    }

    private fun receive(text: String) {
        if (terminal.get()) return
        try {
            val message = Json.parseToJsonElement(text) as? JsonObject ?: return fail("protocol_error")
            protocol?.events(message)?.forEach(::handle)
        } catch (_: Exception) { fail("protocol_error") }
    }
    private fun handle(event: RealtimeEvent) {
        if (terminal.get()) return
        lastProgressAt = System.nanoTime()
        when (event) {
            RealtimeEvent.Ready -> if (ready.compareAndSet(false, true)) {
                timeout?.cancel()
                timeout = scope.launch {
                    while (isActive && !terminal.get()) {
                        delay(5000)
                        if (mutable.value.responding && System.nanoTime() - lastProgressAt > 45_000_000_000L) fail("response_timeout")
                    }
                }
                run {
                    try {
                        val created = RealtimeAudio(context, scope, ::capture, { levels -> mutable.update { it.copy(output = levels) } },
                            { fail("audio_error") }, { if (responseDone) phaseListening() },
                            { routes, selected -> mutable.update { it.copy(outputRoutes = routes, selectedOutputRoute = selected) } })
                        synchronized(audioStateLock) {
                            if (terminal.get()) { created.close(); return@run }
                            audio = created
                            created.inputMuted = mutable.value.inputMuted
                            created.setOutputMuted(mutable.value.outputMuted)
                        }
                        created.start(protocol!!.inputRate, protocol!!.outputRate)
                        if (terminal.get()) audio?.close() else {
                            mutable.update { it.copy(echoCancellation = audio!!.echoCancellationAvailable) }
                            phaseListening()
                        }
                    } catch (_: Exception) { fail("audio_error") }
                }
            }
            is RealtimeEvent.Audio -> {
                if (suppressAudio || !ready.get() || (event.item != null && event.item == cancelledItem)) return
                if (event.rate != protocol?.outputRate) return fail("unsupported_audio")
                if (event.item != item) { item = event.item; itemByteOffset = audio?.playedBytes ?: 0 }
                val bytes = try { Base64.decode(event.base64, Base64.DEFAULT) } catch (_: Exception) { return fail("protocol_error") }
                if (bytes.isEmpty()) return
                responseActive = true; responseDone = false
                if (audio?.enqueue(bytes) != true) return fail("audio_backpressure")
                mutable.update { it.copy(phase = if (it.outputMuted) RealtimePhase.MUTED else RealtimePhase.SPEAKING, responding = true) }
            }
            is RealtimeEvent.Transcript -> mutable.update {
                if (event.user) {
                    val reset = userTurn || !event.delta
                    userTurn = false
                    it.copy(userTranscript = (if (reset) event.text else it.userTranscript + event.text).takeLast(4000))
                } else {
                    val reset = assistantTurn || !event.delta
                    assistantTurn = false
                    it.copy(assistantTranscript = (if (reset) event.text else it.assistantTranscript + event.text).takeLast(4000))
                }
            }
            RealtimeEvent.ResponseStarted -> { if (suppressAudio) return; responseActive = true; responseDone = false; suppressAudio = false; assistantTurn = true; mutable.update { it.copy(phase = RealtimePhase.THINKING, responding = true) } }
            RealtimeEvent.SpeechStarted -> {
                interrupt(); responseDone = false; userTurn = true
                // Gemini interrupted is the boundary: subsequent audio belongs to the next turn.
                if (protocol === GeminiLiveProtocol) suppressAudio = false
            }
            RealtimeEvent.SpeechStopped -> { assistantTurn = true; suppressAudio = false; mutable.update { it.copy(phase = RealtimePhase.THINKING, responding = true) } }
            RealtimeEvent.ResponseDone -> { responseActive = false; responseDone = true; suppressAudio = false; userTurn = true; assistantTurn = true; if (audio?.playing != true) phaseListening() }
            is RealtimeEvent.Error -> fail(when (event.code) {
                "invalid_api_key", "UNAUTHENTICATED", "PERMISSION_DENIED" -> "authentication_failed"
                "model_not_found", "INVALID_ARGUMENT", "invalid_value", "unsupported_model" -> "unsupported_endpoint"
                "rate_limit_exceeded", "RESOURCE_EXHAUSTED", "insufficient_quota" -> "rate_limited"
                "session_expired" -> "session_expired"
                else -> "protocol_error"
            })
        }
    }
    private fun capture(bytes: ByteArray, levels: VoiceLevels) {
        if (terminal.get()) return
        mutable.update { it.copy(input = levels) }
        if (!mutable.value.inputMuted) when (activity.accept(levels.amplitude, audio?.playing == true, audio?.echoCancellationAvailable == true)) {
            VoiceActivityGate.Edge.START -> {
                if (responseActive || audio?.playing == true) interrupt()
                responseDone = false
                userTurn = true
            }
            VoiceActivityGate.Edge.END -> {
                // A short backend reply may finish before the local quiet window ends.
                // Never overwrite completed playback or a currently speaking response.
                if (!responseActive && !responseDone && audio?.playing != true) {
                    lastProgressAt = System.nanoTime()
                    mutable.update { it.copy(phase = RealtimePhase.THINKING, responding = true) }
                }
            }
            null -> Unit
        }
        val event = protocol?.audio(Base64.encodeToString(bytes, Base64.NO_WRAP)) ?: return
        val ws = socket ?: return
        if (ws.queueSize() > 128_000 || !ws.send(event.toString())) fail("network_backpressure")
    }
    fun sendText(text: String) {
        if (!ready.get() || terminal.get() || text.isBlank()) return
        interrupt()
        suppressAudio = false; assistantTurn = true; userTurn = true; responseDone = false
        mutable.update { it.copy(userTranscript = text.takeLast(4000), phase = RealtimePhase.THINKING, responding = true) }
        protocol?.text(text.trim())?.forEach { if (socket?.send(it.toString()) != true) fail("connection_failed") }
    }
    fun interrupt() {
        if (terminal.get()) return
        val wasActive = responseActive || audio?.playing == true
        val played = audio?.playedMillisSince(itemByteOffset, protocol?.outputRate ?: 24000) ?: 0
        suppressAudio = true
        item?.let { cancelledItem = it }
        audio?.interrupt()
        if (wasActive) protocol?.interrupt(item, played)?.forEach { socket?.send(it.toString()) }
        responseActive = false; responseDone = true; item = null; itemByteOffset = 0
        phaseListening()
    }
    fun selectOutputRoute(id: Int) {
        val changed = audio?.selectOutputRoute(id) == true
        mutable.update { it.copy(routeUnavailable = !changed) }
    }
    fun muteOutput() = synchronized(audioStateLock) { val muted = !mutable.value.outputMuted; audio?.setOutputMuted(muted); mutable.update { it.copy(outputMuted = muted, output = VoiceLevels()) } }
    fun muteInput() = synchronized(audioStateLock) { val muted = !mutable.value.inputMuted; audio?.inputMuted = muted; activity.reset(); mutable.update { it.copy(inputMuted = muted, input = VoiceLevels(), phase = if (muted) RealtimePhase.MUTED else RealtimePhase.LISTENING) } }
    private fun phaseListening() = mutable.update { it.copy(phase = if (it.inputMuted) RealtimePhase.MUTED else RealtimePhase.LISTENING, responding = false) }
    private fun fail(code: String) {
        if (!terminal.compareAndSet(false, true)) return
        timeout?.cancel(); socket?.cancel(); synchronized(audioStateLock) { audio?.close(); assistantMicLease?.close(); assistantMicLease = null }
        mutable.update { it.copy(phase = RealtimePhase.ERROR, error = code, input = VoiceLevels(), output = VoiceLevels()) }
    }
    fun close() {
        if (!terminal.getAndSet(true)) { timeout?.cancel(); socket?.close(1000, "user ended"); synchronized(audioStateLock) { audio?.close(); assistantMicLease?.close(); assistantMicLease = null } }
        mutable.update { it.copy(phase = RealtimePhase.CLOSED) }
        scope.launch { delay(1000); scope.cancel() }
    }
}
