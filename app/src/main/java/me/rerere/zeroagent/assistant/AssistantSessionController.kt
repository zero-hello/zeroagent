package me.rerere.zeroagent.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.ai.realtime.*
import me.rerere.zeroagent.data.datastore.*
import me.rerere.zeroagent.service.ChatService
import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

internal fun assistantModel(settings: Settings): Model = settings.nativeVoiceSelection()?.first
    ?: throw AssistantFailure(AssistantIssue.MODEL_UNAVAILABLE)

/** System entry and in-app entry share the same native-audio speech service and model. */
internal class AssistantSessionController(private val context: Context) : KoinComponent {
    private val store: SettingsStore by inject()
    private val client: OkHttpClient by inject()
    private val chat: ChatService by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(CapsuleState())
    val state = mutable.asStateFlow()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private var native: RealtimeVoiceSession? = null
    private var observer: Job? = null
    private var levelObserver: Job? = null
    private var settingsObserver: Job? = null
    private var foreground: AssistantAudioSessions.Lease? = null
    private var id: Uuid? = null
    val chatId: Uuid? get() = id
    private var typed = false
    private var completed = 0
    private var responseModelId: Uuid? = null
    private sealed interface Command {
        data object Start : Command
        data object Listen : Command
        data class Text(val value: String) : Command
        data class Update(val value: RealtimeVoiceState) : Command
        data object AudioLost : Command
    }
    private val commands = Channel<Command>(16)
    private val actor = scope.launch {
        try {
            for (command in commands) {
                if (closed.get()) break
                try {
                    when (command) {
                        Command.Start -> initialize()
                        Command.Listen -> {
                            val current = native
                            if (current == null || current.state.value.phase in setOf(RealtimePhase.ERROR, RealtimePhase.CLOSED)) initialize()
                            else { ensureForeground(); typed = false; current.interrupt(); current.resumeVoice(); mutable.update { it.copy(phase = CapsulePhase.LISTENING, textInput = false, issue = null, errorSummary = null) } }
                        }
                        is Command.Text -> {
                            val current = native ?: throw AssistantFailure(AssistantIssue.MODEL_UNAVAILABLE)
                            typed = true
                            current.parkForText(); foreground?.close(); foreground = null
                            if (!current.sendText(command.value)) throw AssistantFailure(AssistantIssue.NETWORK)
                            mutable.update { it.copy(phase = CapsulePhase.THINKING, textInput = true, heard = command.value, issue = null, errorSummary = null) }
                        }
                        is Command.Update -> receive(command.value)
                        Command.AudioLost -> throw AssistantFailure(AssistantIssue.AUDIO_FOCUS)
                    }
                } catch (e: CancellationException) { if (e is TimeoutCancellationException) fail(AssistantIssue.MICROPHONE_BUSY) else throw e }
                catch (e: Throwable) { fail(if (e is AssistantFailure) e.issue else if (e is SecurityException) AssistantIssue.MICROPHONE_PERMISSION else AssistantIssue.MODEL_FAILED, e) }
            }
        } finally {
            withContext(NonCancellable) {
                observer?.cancel(); levelObserver?.cancel(); settingsObserver?.cancel()
                native?.close(); foreground?.close()
                id?.let { chat.removeConversationReference(it) }
                mutable.update { it.copy(phase = CapsulePhase.CLOSED, microphoneLevel = 0f) }
                commands.close(); scope.cancel()
            }
        }
    }
    fun start() { if (started.compareAndSet(false, true)) commands.trySend(Command.Start) }
    fun startListening() { if (!closed.get()) scope.launch { commands.send(Command.Listen) } }
    fun finishListening() { native?.muteInput() }
    fun sendText(text: String): Boolean {
        if (text.isBlank() || closed.get() || native == null || native?.state?.value?.phase in setOf(RealtimePhase.CONNECTING, RealtimePhase.ERROR)) return false
        return commands.trySend(Command.Text(text.trim())).isSuccess
    }
    fun close(preservePendingForChat: Boolean = false) {
        if (!closed.compareAndSet(false, true)) return
        native?.close(); actor.cancel()
    }
    private suspend fun ensureForeground() {
        if (foreground == null) foreground = AssistantAudioSessions.begin(context) { commands.trySend(Command.AudioLost) }
        withTimeout(4000) { AssistantAudioSessions.serviceReady.first { it } }
    }
    private suspend fun initialize() {
        val settings = store.settingsFlow.first { !it.init }
        if (!settings.voiceAssistant.enabled) throw AssistantFailure(AssistantIssue.DISABLED)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw AssistantFailure(AssistantIssue.MICROPHONE_PERMISSION)
        val (model, provider) = settings.nativeVoiceSelection() ?: throw AssistantFailure(AssistantIssue.MODEL_UNAVAILABLE)
        responseModelId = model.id
        observer?.cancelAndJoin(); levelObserver?.cancelAndJoin(); native?.close()
        ensureForeground(); typed = false; completed = 0
        if (id == null) {
            id = Uuid.random(); chat.addConversationReference(id!!); chat.initializeConversation(id!!)
            chat.updateConversationState(id!!) { it.copy(chatModelId = settings.getCurrentChatModel()?.takeIf { selected -> me.rerere.ai.provider.Modality.TEXT in selected.outputModalities }?.id) }
        }
        mutable.value = CapsuleState()
        val connection = RealtimeVoiceSession(context, client); native = connection
        observer = scope.launch { connection.state.collect { commands.send(Command.Update(it)) } }
        levelObserver = scope.launch { connection.levels.sample(40).collect { levels -> mutable.update { it.copy(microphoneLevel = levels.input.amplitude) } } }
        if (settingsObserver == null) settingsObserver = scope.launch { store.settingsFlow.collect { if (!it.init && !it.voiceAssistant.enabled) close() } }
        connection.start(model, provider, listOf(model.generation.systemPrompt,
            "Respond naturally in the user's language. Do not claim device actions or external searches without real tool results.").filter { it.isNotBlank() }.joinToString("\n\n"))
    }
    private suspend fun receive(update: RealtimeVoiceState) {
        if (update.phase == RealtimePhase.ERROR) { fail(AssistantIssue.MODEL_FAILED, IllegalStateException(update.error)); return }
        if (update.completedTurns > completed) {
            completed = update.completedTurns
            native?.parkForText(); foreground?.close(); foreground = null
            mutable.update { it.copy(phase = CapsulePhase.TEXT, textInput = true, heard = update.userTranscript, answer = update.assistantTranscript, microphoneLevel = 0f) }
            persistTurn(update.userTranscript, update.assistantTranscript)
            return
        }
        val phase = when (update.phase) {
            RealtimePhase.CONNECTING -> CapsulePhase.STARTING
            RealtimePhase.LISTENING, RealtimePhase.MUTED -> if (mutable.value.textInput) CapsulePhase.TEXT else CapsulePhase.LISTENING
            RealtimePhase.THINKING -> CapsulePhase.THINKING
            RealtimePhase.SPEAKING -> if (typed) CapsulePhase.THINKING else CapsulePhase.SPEAKING
            RealtimePhase.ERROR -> CapsulePhase.ERROR
            RealtimePhase.CLOSED -> return
        }
        mutable.update { it.copy(phase = phase, textInput = typed || it.textInput, heard = update.userTranscript, answer = update.assistantTranscript, echoWarning = !update.echoCancellation) }
    }
    private suspend fun persistTurn(user: String, answer: String) {
        val conversationId = id ?: return
        if (answer.isBlank()) return
        val conversation = chat.getConversationFlow(conversationId).value
        val messages = conversation.currentMessages + buildList {
            if (user.isNotBlank()) add(UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(user))))
            add(UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text(answer)), modelId = responseModelId))
        }
        chat.saveConversation(conversationId, conversation.updateCurrentMessages(messages))
    }
    private fun fail(issue: AssistantIssue, error: Throwable? = null) {
        native?.close(); foreground?.close(); foreground = null
        mutable.update { it.copy(phase = CapsulePhase.ERROR, textInput = true, issue = issue, microphoneLevel = 0f,
            errorSummary = error?.let { e -> context.zeroAgentLocalizedContext().localizedErrorMessage(e) }) }
    }
}
