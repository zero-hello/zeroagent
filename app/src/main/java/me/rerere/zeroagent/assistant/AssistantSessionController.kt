package me.rerere.zeroagent.assistant

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Modality
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRController
import me.rerere.asr.ASRStatus
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.zeroagent.assistant.wake.AssistantPcmCapture
import me.rerere.zeroagent.data.ai.realtime.VoiceActivityGate
import me.rerere.zeroagent.data.ai.tools.HeadlessConversations
import me.rerere.zeroagent.data.datastore.*
import me.rerere.zeroagent.service.ChatService
import me.rerere.zeroagent.ui.hooks.createAsrController
import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.zeroagent.utils.stripMarkdown
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

internal fun assistantModel(settings: Settings): Model {
    val choice = settings.voiceAssistant.modelId
    val model = if (choice.isBlank()) settings.getCurrentChatModel()
        else settings.providers.flatMap { it.models }.firstOrNull { it.id.toString() == choice }
    if (model == null || Modality.TEXT !in model.outputModalities || model.findProvider(settings.providers)?.enabled != true)
        throw AssistantFailure(AssistantIssue.MODEL_UNAVAILABLE)
    return model
}

internal fun assistantPlaybackFinished(status: PlaybackStatus, speaking: Boolean) = status == PlaybackStatus.Ended && !speaking

/** All resource transitions run through one actor. Background callbacks carry their originating epoch. */
internal class AssistantSessionController(private val context: Context) : KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val chat: ChatService by inject()
    private val client: OkHttpClient by inject()
    private val ttsManager: TTSManager by inject()
    private val tts = TtsController(context, ttsManager)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(CapsuleState())
    val state = mutable.asStateFlow()
    private val turns = AssistantTurnEpoch()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var conversationId: Uuid? = null
    private var hasReference = false
    private var modelJob: Job? = null
    private var audioWatch: Job? = null
    private var sttWatch: Job? = null
    private var errorWatch: Job? = null
    private var timeout: Job? = null
    private var systemStt: SystemSpeechInput? = null
    private var cloudStt: ASRController? = null
    private var monitor: AssistantPcmCapture? = null
    private var monitorJob: Job? = null
    private var micLease: AssistantMicrophone.Lease? = null
    private var foreground: AssistantAudioSessions.Lease? = null
    private var preserveChat = false
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private var registered = false
    val chatId: Uuid? get() = conversationId
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (mutable.value.phase !in setOf(CapsulePhase.TEXT, CapsulePhase.ERROR, CapsulePhase.CLOSED)) commands.trySend(Command.AudioLost)
        }
    }
    private sealed interface Command {
        data object Init : Command
        data object Listen : Command
        data object FinishListen : Command
        data object AudioLost : Command
        data class Text(val text: String) : Command
        data class Speech(val epoch: Long, val text: String) : Command
        data class ModelDone(val epoch: Long, val answer: Result<String>, val speak: Boolean) : Command
        data class Playback(val epoch: Long, val playback: PlaybackState, val speaking: Boolean) : Command
        data class Error(val epoch: Long, val issue: AssistantIssue, val raw: String? = null) : Command
        data class Barge(val epoch: Long) : Command
    }
    private val actor = scope.launch {
        try {
            for (command in commands) {
                if (closed.get()) break
                try { handle(command) }
                catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) fail(AssistantIssue.MICROPHONE_BUSY) else throw e
                }
                catch (e: Throwable) { failure(e) }
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { cancelWork(cancelModel = !preserveChat) }
                runCatching { tts.dispose() }; releaseForeground()
                if (registered) runCatching { context.unregisterReceiver(noisy) }
                conversationId?.let { if (hasReference) chat.removeConversationReference(it); HeadlessConversations.unmark(it) }
                mutable.update { it.copy(phase = CapsulePhase.CLOSED, microphoneLevel = 0f) }
                commands.close(); scope.cancel()
            }
        }
    }
    fun start() { if (started.compareAndSet(false, true)) commands.trySend(Command.Init) }
    fun startListening() { if (!closed.get()) commands.trySend(Command.Listen) }
    fun finishListening() { commands.trySend(Command.FinishListen) }
    fun sendText(text: String): Boolean {
        if (text.isBlank() || closed.get() || conversationId == null) return false
        commands.trySend(Command.Text(text.trim())); return true
    }
    fun close(preservePendingForChat: Boolean = false) {
        if (!closed.compareAndSet(false, true)) return
        preserveChat = preservePendingForChat; turns.next()
        // Stop audible output and capture immediately; structured cleanup joins their jobs.
        tts.stop(); systemStt?.close(); cloudStt?.dispose(); monitor?.stop()
        actor.cancel()
    }
    private suspend fun handle(command: Command) {
        when (command) {
            Command.Init -> { initialize(); listen() }
            Command.Listen -> { if (conversationId == null) initialize(); listen() }
            Command.FinishListen -> { systemStt?.finish(); cloudStt?.stop() }
            Command.AudioLost -> if (mutable.value.phase !in setOf(CapsulePhase.TEXT, CapsulePhase.CLOSED)) fail(AssistantIssue.AUDIO_FOCUS)
            is Command.Text -> submit(command.text, speak = false)
            is Command.Speech -> if (turns.valid(command.epoch) && mutable.value.phase == CapsulePhase.LISTENING) {
                if (command.text.isBlank()) fail(AssistantIssue.NO_SPEECH) else submit(command.text.trim(), speak = true)
            }
            is Command.Barge -> if (turns.valid(command.epoch) && mutable.value.phase == CapsulePhase.SPEAKING) listen()
            is Command.Error -> if (turns.valid(command.epoch)) fail(command.issue, command.raw)
            is Command.ModelDone -> if (turns.valid(command.epoch)) {
                val error = command.answer.exceptionOrNull()
                if (error != null) {
                    val pending = conversationId?.let { id -> chat.getConversationFlow(id).value.currentMessages.any { m -> m.parts.any { it is UIMessagePart.Tool && it.isPending } } } == true
                    if (pending) fail(AssistantIssue.APPROVAL) else failure(error)
                } else {
                    val answer = command.answer.getOrThrow()
                    if (answer.isBlank()) fail(AssistantIssue.MODEL_FAILED)
                    else {
                        mutable.update { it.copy(answer = answer) }
                        if (command.speak && answer.stripMarkdown().isNotBlank()) play(command.epoch, answer)
                        else { releaseForeground(); mutable.update { it.copy(phase = CapsulePhase.TEXT, textInput = true) } }
                    }
                }
            }
            is Command.Playback -> if (turns.valid(command.epoch) && mutable.value.phase == CapsulePhase.SPEAKING) {
                mutable.update { it.copy(playbackPosition = command.playback.positionMs, playbackDuration = command.playback.durationMs) }
                if (command.playback.status == PlaybackStatus.Error) fail(AssistantIssue.TTS_FAILED, command.playback.errorMessage)
                else if (assistantPlaybackFinished(command.playback.status, command.speaking)) {
                    stopAudio(); releaseForeground(); mutable.update { it.copy(phase = CapsulePhase.TEXT, textInput = true, microphoneLevel = 0f) }
                }
            }
        }
    }
    private suspend fun initialize() {
        val settings = settingsStore.settingsFlow.first { !it.init }
        if (!settings.voiceAssistant.enabled) throw AssistantFailure(AssistantIssue.DISABLED)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw AssistantFailure(AssistantIssue.MICROPHONE_PERMISSION)
        val model = assistantModel(settings)
        if (!registered) { ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED); registered = true }
        val id = Uuid.random(); conversationId = id
        // Browser-headless is independent of auto-approval; normal tool gates stay enabled.
        HeadlessConversations.markBrowserHeadless(id)
        chat.addConversationReference(id); hasReference = true
        chat.initializeConversation(id)
        chat.updateConversationState(id) { it.copy(chatModelId = model.id,
            customSystemPrompt = "This is the ZeroAgent Android voice assistant. Reply in the user's language. Never claim an action succeeded without real tool results. Requests awaiting approval are not completed.") }
        scope.launch { settingsStore.settingsFlow.collect { if (!it.init && !it.voiceAssistant.enabled) close() } }
    }
    private suspend fun ensureForeground() {
        if (foreground == null) foreground = AssistantAudioSessions.begin(context) { commands.trySend(Command.AudioLost) }
        withTimeout(4000) { AssistantAudioSessions.serviceReady.first { it } }
    }
    private fun releaseForeground() { foreground?.close(); foreground = null }
    private suspend fun listen() {
        val epoch = turns.next(); cancelWork(cancelModel = true)
        val settings = settingsStore.settingsFlow.value; val config = settings.voiceAssistant
        if (!config.enabled) throw AssistantFailure(AssistantIssue.DISABLED)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw AssistantFailure(AssistantIssue.MICROPHONE_PERMISSION)
        ensureForeground()
        requestAudioFocus(epoch)
        withTimeout(3000) { micLease = AssistantMicrophone.acquire() }
        if (closed.get()) return
        mutable.update { it.copy(phase = CapsulePhase.LISTENING, textInput = false, heard = "", issue = null, errorSummary = null, microphoneLevel = 0f) }
        val language = config.sttTag(context.assistantAppLanguage())
        if (config.sttProviderId.isBlank()) {
            systemStt = SystemSpeechInput(context).also { input ->
                input.start(language, config.systemSttNetworkAllowed,
                    onText = { text -> mutable.update { if (turns.valid(epoch)) it.copy(heard = text) else it } },
                    onLevel = { level -> mutable.update { if (turns.valid(epoch)) it.copy(microphoneLevel = level) else it } },
                    onFinal = { commands.trySend(Command.Speech(epoch, it)) }, onError = { commands.trySend(Command.Error(epoch, it)) })
            }
        } else {
            val provider = settings.asrProviders.firstOrNull { it.id.toString() == config.sttProviderId } ?: throw AssistantFailure(AssistantIssue.STT_UNAVAILABLE)
            val input = createAsrController(context, client, provider.assistantLanguage(language)) ?: throw AssistantFailure(AssistantIssue.STT_UNAVAILABLE)
            cloudStt = input
            input.start { text -> mutable.update { if (turns.valid(epoch)) it.copy(heard = text) else it } }
            sttWatch = scope.launch {
                var heard = false; var quiet = 0L; var stopped = false
                input.state.collect { sample ->
                    if (!turns.valid(epoch)) return@collect
                    val level = sample.amplitudes.lastOrNull()?.coerceIn(0f, 1f) ?: 0f
                    mutable.update { if (turns.valid(epoch)) it.copy(microphoneLevel = level) else it }
                    if (level > .04f) { heard = true; quiet = 0L }
                    else if (heard && quiet == 0L) quiet = android.os.SystemClock.elapsedRealtime()
                    if (!stopped && heard && sample.status == ASRStatus.Listening && quiet > 0 && android.os.SystemClock.elapsedRealtime() - quiet > 650) { stopped = true; input.stop() }
                    when {
                        sample.status == ASRStatus.Error -> commands.trySend(Command.Error(epoch, AssistantIssue.STT_FAILED, sample.errorMessage))
                        sample.voiceTurn.isComplete -> commands.trySend(Command.Speech(epoch, sample.voiceTurn.finalText.orEmpty()))
                        sample.status == ASRStatus.Idle && (stopped || sample.transcript.isNotBlank()) -> commands.trySend(Command.Speech(epoch, sample.transcript.ifBlank { mutable.value.heard }))
                    }
                }
            }
        }
        timeout = scope.launch { delay(60000); commands.trySend(Command.Error(epoch, AssistantIssue.NO_SPEECH)) }
    }
    private suspend fun submit(text: String, speak: Boolean) {
        val epoch = turns.next(); cancelWork(cancelModel = true)
        if (!speak) releaseForeground()
        mutable.update { it.copy(phase = CapsulePhase.THINKING, textInput = !speak, heard = text, issue = null, errorSummary = null, microphoneLevel = 0f) }
        val settings = settingsStore.settingsFlow.value
        if (!settings.voiceAssistant.enabled) throw AssistantFailure(AssistantIssue.DISABLED)
        val model = assistantModel(settings); val id = conversationId ?: throw AssistantFailure(AssistantIssue.MODEL_UNAVAILABLE)
        chat.updateConversationState(id) { it.copy(chatModelId = model.id) }
        modelJob = scope.launch {
            val reply = assistantReply { chat.enqueueVoiceMessage(id, text).await() }
            commands.send(Command.ModelDone(epoch, reply, speak))
        }
    }
    private suspend fun play(epoch: Long, answer: String) {
        val settings = settingsStore.settingsFlow.value; val config = settings.voiceAssistant
        val base = if (config.ttsProviderId.isBlank()) TTSProviderSetting.SystemTTS()
            else settings.ttsProviders.firstOrNull { it.id.toString() == config.ttsProviderId } ?: throw AssistantFailure(AssistantIssue.TTS_UNAVAILABLE)
        val provider = base.assistantVoice(config, context.assistantAppLanguage())
        ensureForeground(); withTimeout(3000) { micLease = AssistantMicrophone.acquire() }
        mutable.update { it.copy(phase = CapsulePhase.SPEAKING, playbackPosition = 0, playbackDuration = 0) }
        requestAudioFocus(epoch)
        val capture = AssistantPcmCapture(context, communication = true); monitor = capture
        monitorJob = scope.launch {
            val activity = VoiceActivityGate()
            try {
                capture.read { _, level ->
                    if (!turns.valid(epoch)) { capture.stop(); return@read }
                    mutable.update { if (turns.valid(epoch)) it.copy(echoWarning = !capture.echoCancellation) else it }
                    if (activity.accept(level, true, capture.echoCancellation) == VoiceActivityGate.Edge.START) commands.trySend(Command.Barge(epoch))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { if (turns.valid(epoch)) it.copy(echoWarning = true) else it } }
        }
        tts.setProvider(provider); tts.speak(answer.stripMarkdown())
        audioWatch = scope.launch { combine(tts.playbackState, tts.isSpeaking) { playback, speaking -> Command.Playback(epoch, playback, speaking) }.collect { commands.send(it) } }
        errorWatch = scope.launch { tts.error.filterNotNull().first().let { commands.send(Command.Error(epoch, AssistantIssue.TTS_FAILED, it)) } }
    }
    private fun requestAudioFocus(epoch: Long) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ if (it < 0 && turns.valid(epoch)) commands.trySend(Command.Error(epoch, AssistantIssue.AUDIO_FOCUS)) }, Handler(Looper.getMainLooper())).build()
        focus = request
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw AssistantFailure(AssistantIssue.AUDIO_FOCUS)
    }
    private suspend fun stopAudio() {
        timeout?.cancel(); timeout = null
        systemStt?.close(); systemStt = null; cloudStt?.dispose(); cloudStt = null
        tts.stop(); monitor?.stop()
        val jobs = listOfNotNull(sttWatch, audioWatch, errorWatch, monitorJob)
        sttWatch = null; audioWatch = null; errorWatch = null; monitorJob = null; monitor = null
        jobs.forEach { it.cancel() }; jobs.joinAll()
        micLease?.close(); micLease = null
        val oldFocus = focus; focus = null; oldFocus?.let { audioManager.abandonAudioFocusRequest(it) }
    }
    private suspend fun cancelWork(cancelModel: Boolean) {
        modelJob?.cancelAndJoin(); modelJob = null
        stopAudio()
        if (cancelModel) conversationId?.let { runCatching { chat.stopGeneration(it) } }
    }
    private suspend fun fail(issue: AssistantIssue, raw: String? = null) {
        turns.next(); cancelWork(cancelModel = issue != AssistantIssue.APPROVAL); releaseForeground()
        mutable.update { it.copy(phase = CapsulePhase.ERROR, textInput = true, issue = issue,
            errorSummary = raw?.let { text -> context.zeroAgentLocalizedContext().localizedErrorMessage(raw = text) }, microphoneLevel = 0f) }
    }
    private suspend fun failure(error: Throwable) = fail(when (error) {
        is AssistantFailure -> error.issue
        is SecurityException -> AssistantIssue.MICROPHONE_PERMISSION
        is OutOfMemoryError -> AssistantIssue.MEMORY
        else -> AssistantIssue.MODEL_FAILED
    }, if (error is AssistantFailure) null else error.message)
}
