package me.rerere.zeroagent.service

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.zeroagent.AppScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.AssistantResolver
import me.rerere.zeroagent.data.ai.AutomationRecorder
import me.rerere.zeroagent.data.ai.tools.AppPlaybookFile
import me.rerere.zeroagent.data.ai.tools.AppPlaybookRules
import me.rerere.zeroagent.data.ai.GenerationChunk
import me.rerere.zeroagent.data.ai.GenerationLoop
import me.rerere.zeroagent.data.ai.ContextBudgetPlanner
import me.rerere.zeroagent.data.ai.ContextCompactionPlanner
import me.rerere.zeroagent.data.ai.ContextCompactionPresentation
import me.rerere.zeroagent.data.ai.CompactedMessageView
import me.rerere.zeroagent.data.ai.ContextCompactionView
import me.rerere.zeroagent.data.ai.ToolSurfaceResolver
import me.rerere.zeroagent.data.ai.TranslationHandler
import me.rerere.zeroagent.data.ai.mcp.McpManager
import me.rerere.zeroagent.data.ai.tools.ToolActivationState
import me.rerere.zeroagent.data.ai.tools.ChatToolFactory
import me.rerere.zeroagent.data.ai.tools.ChatToolContext
import me.rerere.zeroagent.data.ai.tools.InvalidMcpServerNamesException
import me.rerere.zeroagent.data.ai.tools.LocalTools
import me.rerere.zeroagent.data.ai.tools.CompactionToolResult
import me.rerere.zeroagent.data.ai.tools.ColdMemoryRules
import me.rerere.zeroagent.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.zeroagent.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.zeroagent.data.ai.transformers.ContextBudgetReminderTransformer
import me.rerere.zeroagent.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.zeroagent.data.ai.transformers.OcrTransformer
import me.rerere.zeroagent.data.ai.transformers.PlaceholderTransformer
import me.rerere.zeroagent.data.ai.transformers.PromptInjectionTransformer
import me.rerere.zeroagent.data.ai.transformers.RegexOutputTransformer
import me.rerere.zeroagent.data.ai.transformers.TemplateTransformer
import me.rerere.zeroagent.data.ai.transformers.ThinkTagTransformer
import me.rerere.zeroagent.data.ai.transformers.TimeReminderTransformer
import me.rerere.zeroagent.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.zeroagent.data.event.AppEventBus
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.assistantForModel
import me.rerere.zeroagent.data.datastore.DEFAULT_AUTO_MODEL_ID
import me.rerere.zeroagent.data.datastore.findModelById
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.datastore.getCompactionContextLength
import me.rerere.zeroagent.data.datastore.getContextCompactionTargetTokens
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.model.ConversationCompaction
import me.rerere.zeroagent.data.model.MessageNode
import me.rerere.zeroagent.data.model.AssistantAffectScope
import me.rerere.zeroagent.data.model.localFileUrls
import me.rerere.zeroagent.data.model.replaceRegexes
import me.rerere.zeroagent.data.model.toMessageNode
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.data.repository.FolderRepository
import me.rerere.zeroagent.data.repository.MemoryRepository
import me.rerere.zeroagent.data.repository.WorkspaceRepository
import me.rerere.zeroagent.web.BadRequestException
import me.rerere.zeroagent.web.NotFoundException
import me.rerere.zeroagent.utils.applyPlaceholders
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid
import me.rerere.zeroagent.data.usage.UsageCallContext
import me.rerere.zeroagent.data.usage.UsagePurpose

private const val TAG = "ChatService"
/**
 * Raw messages a model-initiated `compact_context` call keeps verbatim. Matches the default of
 * the manual compress dialog (`CompressContextDialog`) so the two entry points behave the same.
 */
private const val COMPACTION_TOOL_KEEP_RECENT_MESSAGES = 32
private fun Throwable.isContextLimitError(): Boolean {
    val markers = listOf(
        "context length",
        "context window",
        "maximum context",
        "max context",
        "context length exceeded",
        "maximum tokens",
        "prompt is too long",
        "prompt too long",
        "too many tokens",
        "token limit",
        "input is too long",
        "exceeds the model",
        "exceed.*context",
    )
    return generateSequence(this) { it.cause }
        .take(8)
        .any { cause ->
            val text = (cause.message.orEmpty() + " " + cause.toString())
                .lowercase()
                .replace('_', ' ')
            markers.any { marker ->
                if (marker.contains(".*")) Regex(marker).containsMatchIn(text) else marker in text
            }
        }
}

internal fun backgroundTextGenerationParams(
    model: Model,
    conversationId: Uuid,
    reasoningLevel: ReasoningLevel = model.generation.reasoningLevel,
): TextGenerationParams = TextGenerationParams(
    model = model,
    temperature = model.generation.temperature,
    topP = model.generation.topP,
    topK = model.generation.topK,
    maxTokens = model.generation.maxTokens,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = conversationId.toString(),
)

/** Explicit per-conversation overrides are retained for sub-agents; ordinary chats use the global model. */
internal fun resolveConversationChatModel(settings: Settings, conversation: Conversation): Model? =
    settings.findModelById(conversation.chatModelId ?: settings.chatModelId)

/**
 * Resolve the compression model's usable context ceiling. The token-threshold setting is an
 * explicit user-controlled limit when that mode is selected, which also covers providers such
 * as Codex whose model list does not publish context metadata.
 */
internal fun compactionContextLength(settings: Settings, model: Model): Int? =
    settings.getCompactionContextLength(model)

/**
 * Resolve the model to compress against. `settings.compressModelId` defaults to (and is
 * user-selectable as) the "Auto" placeholder, a literal [Model] owned by the built-in
 * "ZeroAgent" provider, which ships disabled — so resolving it as a real model and then
 * discovering its provider is disabled used to surface as an opaque failure. Falls through, in
 * order: the configured model (unless it's the Auto placeholder or its provider is disabled),
 * the current chat model (same guard), then the first model belonging to any enabled provider.
 * Returns null only when none of those is usable.
 */
internal fun resolveCompressionModel(settings: Settings): Model? {
    fun Model.takeIfUsable(): Model? {
        if (id == DEFAULT_AUTO_MODEL_ID) return null
        val provider = findProvider(settings.providers) ?: return null
        return if (provider.enabled) this else null
    }

    settings.findModelById(settings.compressModelId)?.takeIfUsable()?.let { return it }
    settings.getCurrentChatModel()?.takeIfUsable()?.let { return it }
    return settings.providers
        .filter { it.enabled }
        .flatMap { it.models }
        .firstNotNullOfOrNull { it.takeIfUsable() }
}

/** Message for the compression failure thrown when [resolveCompressionModel] finds nothing
 *  usable, naming the setting so the resulting error card is actionable. */
internal fun compressionModelUnavailableMessage(): String =
    "No enabled provider is available for compression. Set a different model under " +
        "\"Compress Model\" in Settings."

/**
 * Locates the [UIMessagePart.Tool] with [toolCallId] anywhere in [conversation]'s message
 * nodes, across every message version in each node (not just the currently-selected
 * branch) - the same scope [ChatService.handleToolApproval] mutates. Returns null if no
 * such part exists.
 */
internal fun findToolCallPart(conversation: Conversation, toolCallId: String): UIMessagePart.Tool? =
    conversation.messageNodes.asSequence()
        .flatMap { it.messages.asSequence() }
        .flatMap { it.parts.asSequence() }
        .filterIsInstance<UIMessagePart.Tool>()
        .firstOrNull { it.toolCallId == toolCallId }

/**
 * Returns [conversation] with the [UIMessagePart.Tool] matching [toolCallId] replaced by
 * [transform]'s result, wherever it appears (see [findToolCallPart]). Returns [conversation]
 * unchanged if no matching part exists.
 */
internal fun replaceToolCallPart(
    conversation: Conversation,
    toolCallId: String,
    transform: (UIMessagePart.Tool) -> UIMessagePart.Tool,
): Conversation {
    var found = false
    val updatedNodes = conversation.messageNodes.map { node ->
        node.copy(
            messages = node.messages.map { msg ->
                msg.copy(
                    parts = msg.parts.map { part ->
                        if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                            found = true
                            transform(part)
                        } else part
                    }
                )
            }
        )
    }
    return if (found) conversation.copy(messageNodes = updatedNodes) else conversation
}

/**
 * Result for a non-advancing automatic-compaction boundary (see
 * `ConversationCompactionCoordinator.createAutomaticCompaction`): keep the existing compaction as a no-op rather
 * than aborting the turn, unless there is none to fall back to. Pure so the no-op-vs-throw
 * split is unit-testable without constructing a [ChatService].
 */
internal fun automaticCompactionNoOpResult(existingCompaction: ConversationCompaction?): ConversationCompaction =
    existingCompaction
        ?: throw IllegalStateException("No new messages available for automatic compaction")

/**
 * Records [compaction] via [onCreated] only when it is non-null, then returns it unchanged.
 * `ConversationCompactionCoordinator.createAutomaticCompaction` returns null for a no-op boundary rather than the
 * pre-existing compaction (see its kdoc), specifically so its callers can tell a no-op apart
 * from a freshly created compaction when populating
 * [me.rerere.zeroagent.data.ai.CompactedMessageView.newlyCreatedAutoCompaction] -- recording a
 * no-op there would attach a "context compacted" tool card announcing a compaction that never
 * happened. Pure so the capture rule is unit-testable without constructing a [ChatService].
 */
internal fun recordAutoCompactionIfCreated(
    compaction: ConversationCompaction?,
    onCreated: (ConversationCompaction) -> Unit,
): ConversationCompaction? {
    compaction?.let(onCreated)
    return compaction
}

/**
 * A turn "stalled" when it ended without producing text the user can read: a hard failure
 * (any error, including retry exhaustion), or a success whose final assistant message carries
 * only reasoning/tool parts (or a blank text part) with no non-blank [UIMessagePart.Text].
 * Deliberate non-goal: detecting mid-sentence truncation of a turn that DID produce text -
 * there is no reliable signal for that, so it is treated as not stalled.
 */
internal fun isStalledTurn(succeeded: Boolean, lastMessage: UIMessage?): Boolean {
    if (!succeeded) return true
    if (lastMessage == null || lastMessage.role != MessageRole.ASSISTANT) return false
    return lastMessage.parts.filterIsInstance<UIMessagePart.Text>().none { it.text.isNotBlank() }
}

internal fun createForkConversation(
    source: Conversation,
    messageNodes: List<MessageNode>,
): Conversation = Conversation(
    id = Uuid.random(),
    assistantId = source.assistantId,
    messageNodes = messageNodes,
    customSystemPrompt = source.customSystemPrompt,
    modeInjectionIds = source.modeInjectionIds,
    lorebookIds = source.lorebookIds,
    workspaceCwd = source.workspaceCwd,
    folderId = source.folderId,
)

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

private val inputTransformers by lazy {
    listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

/** Aux-generation kind tracked by [AuxJobRegistry]. */
internal enum class AuxJobKind { TITLE, SUGGESTION }

/**
 * Tracks the in-flight title/suggestion job per (conversation, kind) so a burst of aux
 * generations can't pile up against the same llama.cpp server slot and evict the main
 * conversation's cached prompt prefix. [launch] cancels-and-replaces any previous job
 * registered for the same key; [cancelAll] cancels every job for one conversation without
 * touching another conversation's. Jobs deregister themselves on completion (success,
 * failure, or cancellation) so the map never accumulates entries for finished work.
 * Extracted as a small dependency-free class so this is unit-testable without an Android
 * runtime or a constructed [ChatService].
 */
internal class AuxJobRegistry {
    private val jobs = ConcurrentHashMap<Pair<Uuid, AuxJobKind>, Job>()

    fun launch(
        scope: CoroutineScope,
        conversationId: Uuid,
        kind: AuxJobKind,
        block: suspend () -> Unit,
    ): Job {
        val key = conversationId to kind
        jobs[key]?.cancel()
        // LAZY-start + register-before-start (mirrors ConversationSession.setJob) so the
        // completion handler below can never fire before the job is in the map.
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        job.invokeOnCompletion { jobs.remove(key, job) }
        jobs[key] = job
        job.start()
        return job
    }

    fun cancelAll(conversationId: Uuid) {
        jobs.keys
            .filter { it.first == conversationId }
            .forEach { key -> jobs[key]?.cancel() }
    }

    /** True while a job is registered for this key. Test-observability hook for the
     *  deregister-on-completion guarantee [launch] documents. */
    internal fun isTracked(conversationId: Uuid, kind: AuxJobKind): Boolean =
        jobs.containsKey(conversationId to kind)
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationLoop: GenerationLoop,
    private val translationHandler: TranslationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    private val chatToolFactory: ChatToolFactory,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val toolApprovalPreferences: me.rerere.zeroagent.data.preferences.ToolApprovalPreferences,
    private val workspaceRepository: WorkspaceRepository,
    private val folderRepository: FolderRepository,
) {
    // workspace 系统提示注入 (依赖 workspaceRepository, 故在类内构造)
    private val workspaceReminderTransformer = WorkspaceReminderTransformer(workspaceRepository)

    // 统一会话管理
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)

    // Title/suggestion aux-generation jobs, keyed per conversation - see AuxJobRegistry.
    private val auxJobs = AuxJobRegistry()

    /**
     * Per-conversation mutex serialising state-mutating operations: handleToolApproval,
     * stopGeneration, the chunk-handling save path, and explicit DB writes. Without this
     * the audit reports identified multiple write races where a fresh approval mutation
     * gets clobbered by a concurrent write from a stale snapshot. Generation chunks
     * themselves are NOT held under this mutex — only the persist boundaries.
     */
    private val sessionMutexes = ConcurrentHashMap<Uuid, Mutex>()
    private fun mutexFor(conversationId: Uuid): Mutex =
        sessionMutexes.getOrPut(conversationId) { Mutex() }

    private val compactionMutexes = ConcurrentHashMap<Uuid, Mutex>()
    private fun compactionMutexFor(conversationId: Uuid): Mutex =
        compactionMutexes.getOrPut(conversationId) { Mutex() }

    /**
     * T-03 / (1) - per-conversation activation state for the progressive tool catalog.
     *
     * Deliberately session-scoped and never persisted: which schemas the model opened is
     * discovery state, not user data, and losing it merely costs the model one extra
     * `tool_search` round-trip. [ToolActivationState] prunes names that leave the catalog on the
     * next turn, so a deleted server cannot leave a stale activation behind.
     */
    private val toolActivations = ConcurrentHashMap<Uuid, ToolActivationState>()

    private fun toolActivationFor(conversationId: Uuid): ToolActivationState =
        toolActivations.getOrPut(conversationId) { ToolActivationState() }

    private val compactionGenerator = ConversationCompactionGenerator(
        providerManager, conversationRepo,
        currentConversation = { getConversationFlow(it).value },
    )

    private val compactionCoordinator = ConversationCompactionCoordinator(
        context, settingsStore, conversationRepo, compactionGenerator,
        currentConversation = { getConversationFlow(it).value },
        compactionMutexFor = ::compactionMutexFor,
    )

    private val notifications = ChatNotifications(context) { getConversationFlow(it).value }

    private val streamingPersistence = StreamingConversationPersistence(
        snapshot = { getConversationFlow(it).value },
        writeSnapshot = { id, conversation, index -> persistConversationSnapshot(id, conversation, index) },
        nowMs = SystemClock::elapsedRealtime,
        onFailure = { id, error -> Log.w(TAG, "persistStreamingStateNow failed for $id", error) },
    )

    // This starts before the chat coroutine is dispatched, so a user can background the app
    // immediately after pressing Send without racing the foreground-service promotion.
    private val foregroundWorkTracker = ForegroundWorkTracker(
        onFirstAcquire = { ChatGenerationForegroundService.start(context) },
        onLastRelease = { ChatGenerationForegroundService.stop(context) },
    )

    /**
     * P2-08 — a foreground-service claim for work that is not a chat generation.
     *
     * [me.rerere.zeroagent.subagent.SubAgentEngine] holds one claim for the whole life of a
     * sub-agent run, because a background dispatch can outlive the parent turn and spans gaps —
     * tool execution, a concurrency-slot wait, the parent-notification wait — where none of our
     * generations is in flight, so the service would otherwise stop and the process become
     * reclaimable mid-orchestration.
     *
     * The claim lands on the same [foregroundWorkTracker] the generations use, so the service
     * starts on the first claim and stops on the last: an orchestration ending cannot stop a
     * service a live generation still needs, and a tracker back at zero cannot leave the service
     * running with no active run (the P2-08 red line). The returned callback is idempotent; call
     * it from the `finally` that ends the owning work so a cancelled or failed run releases too.
     */
    fun retainForegroundForActiveRun(): () -> Unit = foregroundWorkTracker.acquire()

    /**
     * Hydrate the in-memory session for [conversationId] from disk if it's currently
     * blank. Used by entry points (callback handlers, approval handlers) that may be hit
     * after a process restart with an empty session map — without this they read an
     * empty Conversation, mutate it, and `saveConversation` then OVERWRITES the persisted
     * state with empty content (silent data loss). Idempotent and cheap when the session
     * is already populated.
     */
    suspend fun ensureHydrated(conversationId: Uuid) {
        val session = getOrCreateSession(conversationId)
        if (session.state.value.messageNodes.isEmpty()) {
            val fromDb = conversationRepo.getConversationById(conversationId) ?: return
            if (fromDb.messageNodes.isNotEmpty()) {
                session.state.value = fromDb
            }
        }
    }

    // Conversations we have already told that the selected model can't use tools. Kept so the
    // warning below fires once per conversation instead of on every turn — see its call site.
    private val toolUnavailableWarned = ConcurrentHashMap.newKeySet<Uuid>()

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update { it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution) }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    // 前台状态管理
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> _isForeground.value = true
            Lifecycle.Event.ON_STOP -> {
                _isForeground.value = false
                // A user leaving the app does not cancel AppScope generation. Flush the latest
                // in-memory stream state while the process is still alive so returning to the
                // conversation (or an imminent process kill) never falls behind the UI.
                appScope.launch(Dispatchers.IO) {
                    runCatching { flushStreamingPersistence() }
                        .onFailure { Log.w(TAG, "flushStreamingPersistence on stop failed", it) }
                }
            }
            else -> {}
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    fun cleanup() = runCatching {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        sessions.values.forEach { it.cleanup() }
        sessions.clear()
        sessionMutexes.clear()
        compactionMutexes.clear()
        streamingPersistence.clear()
    }.onFailure {
        // Don't let a teardown hiccup escape, but don't swallow it silently either —
        // a failure here can leave the lifecycle observer registered (slow leak).
        Log.w(TAG, "cleanup failed", it)
    }

    // ---- Session 管理 ----

    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {
        return sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id
                ),
                scope = appScope,
                onIdle = { removeSession(it) },
                onGenerationFinished = { id, cause ->
                    val session = sessions[id]
                    if (cause != null) session?.messageQueue?.pause()
                    if (session?.state?.value?.currentMessages?.any { message ->
                            message.parts.any { it is UIMessagePart.Tool && it.isPending }
                        } == true) {
                        session.messageQueue.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
                    }
                    appScope.launch { dispatchNextQueuedMessage(id) }
                },
            ).also {
                _sessionsVersion.value++
                Log.i(TAG, "createSession: $id (total: ${sessions.size + 1})")
            }
        }
    }

    private fun removeSession(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            // Evict the per-conversation mutex so it doesn't accumulate forever.
            // dropSession() already removes it; removeSession() (idle eviction path)
            // was previously missing this cleanup, causing a slow leak on heavy-use
            // sessions where many conversations cycle in and out of memory.
            sessionMutexes.remove(conversationId)
            compactionMutexes.remove(conversationId)
            streamingPersistence.removeIfClean(conversationId)
            _sessionsVersion.value++
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    /**
     * Force-drop the in-memory session for [conversationId] regardless of refcount /
     * generation status. Prevents a straggler
     * coroutine writing back to the session can't resurrect the conversation after the
     * user reset it. Safe to call when no session exists — no-op.
     */
    fun dropSession(conversationId: Uuid) {
        val session = sessions.remove(conversationId) ?: return
        session.cleanup()
        sessionMutexes.remove(conversationId)
        compactionMutexes.remove(conversationId)
        streamingPersistence.drop(conversationId)
        _sessionsVersion.value++
        Log.i(TAG, "dropSession: $conversationId (remaining: ${sessions.size})")
    }

    // ---- 引用管理 ----

    fun addConversationReference(conversationId: Uuid) {
        getOrCreateSession(conversationId).acquire()
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    private fun launchWithConversationReference(
        conversationId: Uuid,
        block: suspend () -> Unit
    ): Job = appScope.launch {
        addConversationReference(conversationId)
        try {
            block()
        } finally {
            removeConversationReference(conversationId)
        }
    }

    /**
     * Like [launchWithConversationReference], but goes through [auxJobs] so launching this
     * [kind] for [conversationId] again cancels-and-replaces the previous one, and a new main
     * generation can cancel every aux job for that conversation via [AuxJobRegistry.cancelAll].
     */
    private fun launchAuxJob(
        conversationId: Uuid,
        kind: AuxJobKind,
        block: suspend () -> Unit,
    ): Job = auxJobs.launch(appScope, conversationId, kind) {
        addConversationReference(conversationId)
        try {
            block()
        } finally {
            removeConversationReference(conversationId)
        }
    }

    // ---- 对话状态访问 ----

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        return getOrCreateSession(conversationId).state
    }

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        val session = sessions[conversationId] ?: return flowOf(null)
        return session.generationJob
    }

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> {
        return getOrCreateSession(conversationId).processingStatus
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return _sessionsVersion.flatMapLatest {
            val currentSessions = sessions.values.toList()
            if (currentSessions.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(currentSessions.map { s ->
                    s.generationJob.map { job -> s.id to job }
                }) { pairs ->
                    pairs.filter { it.second != null }.toMap()
                }
            }
        }
    }

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        val releaseForegroundWork = foregroundWorkTracker.acquire()
        return appScope.launch(start = CoroutineStart.LAZY) {
            try {
                awaitForegroundWorkReady()
                block()
            } finally {
                releaseForegroundWork()
            }
        }
    }


    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        getOrCreateSession(conversationId) // 确保 session 存在
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            updateConversation(conversationId, conversation)
            settingsStore.updateAssistant(conversation.assistantId)
        } else {
            // A send can race this asynchronous initialization for a brand-new conversation.
            // Once the session already contains a user message, never replace it with the
            // assistant preset that was computed from the stale empty snapshot.
            if (getConversationFlow(conversationId).value.messageNodes.isNotEmpty()) return
            // 新建对话, 并添加预设消息
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
                newConversation = true
            ).updateCurrentMessages(assistant.presetMessages)
            updateConversation(conversationId, newConversation)
        }
    }

    // ---- 发送消息 ----

    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        getOrCreateSession(conversationId).messageQueue.state

    fun removeQueuedMessage(conversationId: Uuid, messageId: Uuid) {
        sessions[conversationId]?.messageQueue?.remove(messageId)?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessions[conversationId]?.messageQueue?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessions[conversationId]?.messageQueue?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessions.values.toList()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.messageQueue.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeMessageQueue(conversationId: Uuid) {
        sessions[conversationId]?.messageQueue?.resume()
        dispatchNextQueuedMessage(conversationId)
    }

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        val session = getOrCreateSession(conversationId)
        synchronized(session) {
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(content, answer)
            dispatchNextQueuedMessage(conversationId)
        }
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = getOrCreateSession(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.messageQueue.state.value.paused || session.messageQueue.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(session.state.value.currentMessages.none { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) { context.getString(R.string.chat_page_voice_tools_before_resume) }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchNextQueuedMessage(conversationId)
        }
        return reply
    }

    private fun dispatchNextQueuedMessage(conversationId: Uuid): Job? {
        val session = sessions[conversationId] ?: return null
        synchronized(session) {
            // A pending tool approval is still part of the current turn.
            if (session.getJob() != null || session.state.value.currentMessages.any { message ->
                    message.parts.any { it is UIMessagePart.Tool && it.isPending }
                }) return null
            val next = session.messageQueue.takeNext() ?: return null
            session.submittingMessage = next
            return sendQueuedMessage(session, next)
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        val content = queued.parts
        val answer = queued.answer
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = answer,
        ) {
            try {
                finishInterruptedPendingTools(conversationId)

                // The chat screen can be recreated before its asynchronous initialization
                // finishes. Load the durable conversation before taking the snapshot used for
                // the new user message, otherwise that message can be appended to an empty
                // in-memory session and visually replace the recovered history.
                ensureHydrated(conversationId)
                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = resolveConversationChatModel(settings, currentConversation)
                    ?.let { settings.assistantForModel(it) }
                    ?: AssistantResolver.forConversation(settings, currentConversation.assistantId).assistant
                val processedContent = preprocessUserInputParts(content, assistant)

                // 添加消息到列表
                val withUser = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, withUser)
                session.submittingMessage = null

                // Phase 16 — fast-path router. If the assistant has it enabled and the user's
                // message matches a deterministic intent, run the matching tool and inject the
                // result as a synthetic assistant message — skipping the LLM entirely.
                // Conservative: any match failure (tool throws, no result) falls back to the
                // normal LLM path. Headless conversations and non-text messages are skipped.
                val routedHandled = if (answer)
                    tryFastPathRoute(conversationId, processedContent, withUser, assistant)
                else false

                // 开始补全 — only if router didn't handle the turn
                if (answer && !routedHandled) {
                    handleMessageComplete(conversationId)
                }

                queued.reply?.completeWith(runCatching {
                    val messages = session.state.value.currentMessages
                    check(!session.messageQueue.state.value.paused) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check(messages.none { message -> message.parts.any { it is UIMessagePart.Tool && it.isPending } }) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    val previousIds = currentConversation.currentMessages.map { it.id }.toSet()
                    messages.filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) queued.reply?.completeExceptionally(cause)
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        // A fresh main generation makes any in-flight title/suggestion request for this
        // conversation redundant, and letting it keep running alongside the new request just
        // adds a second concurrent connection to the same server (see AuxJobRegistry kdoc).
        auxJobs.cancelAll(conversationId)
        session.setJob(job)
        return job
    }

    /**
     * Phase 16 — fast-path router entry. Returns `true` if the router successfully handled
     * the turn (synthesised an assistant message and stored it) so the caller knows to skip
     * the normal LLM dispatch. Returns `false` to fall through.
     */
    private suspend fun tryFastPathRoute(
        conversationId: Uuid,
        userParts: List<UIMessagePart>,
        afterUserSave: me.rerere.zeroagent.data.model.Conversation,
        assistant: Assistant,
    ): Boolean {
        // Headless paths (cron / sub-agent / external-automation / workflow) must always go
        // through the LLM — the fast-path is a per-user-turn optimisation, not a system-flow.
        if (me.rerere.zeroagent.data.ai.tools.HeadlessConversations.isHeadless(conversationId)) return false

        // Reuse the caller's resolved model profile for the entire turn; do not resolve a
        // different profile while constructing the fast-path tool surface.
        if (!assistant.fastPathRouterEnabled) return false

        val userText = userParts.filterIsInstance<UIMessagePart.Text>().joinToString(" ") { it.text }.trim()
        if (userText.isBlank()) return false

        val match = me.rerere.zeroagent.skills.FastPathRouter.route(userText) ?: return false

        // Tool list construction is non-trivial on assistants with many enabled categories
        // (allocates a fresh List<Tool> each call). Defer until AFTER a router match so the
        // common no-match path stays at a single regex scan + an early return.
        // Fast-path is gated on !isHeadless above; pass the caller context so any tools the
        // router fires inherit the right assistant id (workflows / sub-agents / etc).
        val tools = ToolSurfaceResolver.resolve(
            localTools = localTools,
            assistant = assistant,
            context = ToolSurfaceResolver.fastPathContext(assistant, conversationId),
        )
        val tool = tools.firstOrNull { it.name == match.toolName } ?: run {
            android.util.Log.d("FastPathRouter", "matched intent=${match.intent} but tool=${match.toolName} not registered for assistant; falling through")
            return false
        }

        // Defence-in-depth — even though v1's intent set is read-only, run HARDLINE here so
        // that adding a side-effecting intent later (e.g. "set brightness 50%") can't bypass
        // the floor by routing around the LLM-tool-call path that normally enforces it.
        val hardlineReason = me.rerere.zeroagent.data.ai.tools.HardlineCommandGuard
            .checkTool(match.toolName, match.args.toString())
        if (hardlineReason != null) {
            android.util.Log.w("FastPathRouter", "hardline-blocked intent=${match.intent} tool=${match.toolName}: $hardlineReason; falling through to LLM")
            return false
        }

        val rendered: String = try {
            val out = tool.execute(match.args)
            val rawText = out.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
            val parsed = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(rawText).jsonObject
            }.getOrNull()
            val formatted = if (match.format != null && parsed != null) {
                runCatching { match.format.invoke(parsed) }
                    .onFailure { Log.w("FastPathRouter", "formatter for intent=${match.intent} threw; falling back to raw text", it) }
                    .getOrNull()
            } else null
            // Fall back to raw text if formatter throws or produces nothing.
            formatted?.takeIf { it.isNotBlank() } ?: rawText
        } catch (t: Throwable) {
            android.util.Log.w("FastPathRouter", "tool ${match.toolName} threw, falling back to LLM", t)
            me.rerere.zeroagent.skills.FastPathRouterLog.record(
                me.rerere.zeroagent.skills.FastPathRouterLog.Entry(
                    whenMs = System.currentTimeMillis(),
                    intent = match.intent,
                    toolName = match.toolName,
                    userText = userText.take(120),
                    resultPreview = "tool threw: ${t.message?.take(80)}",
                    skippedLlm = false,
                )
            )
            return false
        }

        // Inject synthetic assistant message into the conversation.
        val withAssistant = afterUserSave.copy(
            messageNodes = afterUserSave.messageNodes + UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text(rendered)),
            ).toMessageNode(),
        )
        saveConversation(conversationId, withAssistant)
        me.rerere.zeroagent.skills.FastPathRouterLog.record(
            me.rerere.zeroagent.skills.FastPathRouterLog.Entry(
                whenMs = System.currentTimeMillis(),
                intent = match.intent,
                toolName = match.toolName,
                userText = userText.take(120),
                resultPreview = rendered.take(200),
                skippedLlm = true,
            )
        )
        return true
    }

    private fun preprocessUserInputParts(
        parts: List<UIMessagePart>,
        assistant: Assistant,
    ): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) = synchronized(getOrCreateSession(conversationId)) {
        val session = getOrCreateSession(conversationId)
        val previousJob = session.getJob()

        val releaseForegroundWork = foregroundWorkTracker.acquire()
        val job = appScope.launch {
            try {
                awaitForegroundWorkReady()
                previousJob?.join()
                val conversation = session.state.value

                // Locate the message's node up front. indexOf returns -1 when the node is no
                // longer in the conversation (e.g. it was edited or removed between the tap and
                // here). Both branches index off this: the USER branch would subList(0, 0) and
                // silently wipe the conversation, and the regenerate branch builds `0..<-1`,
                // whose endInclusive is -2, which handleMessageComplete turns into
                // subList(0, -1) and crashes ("fromIndex(0) > toIndex(-1)"). Bail on not-found.
                val node = conversation.getMessageNodeByMessage(message)
                val indexAt = conversation.messageNodes.indexOf(node)
                if (indexAt < 0) {
                    Log.w(TAG, "regenerateAtMessage: node for message ${message.id} not in conversation; skipping")
                    return@launch
                }
                if (message.role == MessageRole.USER) {
                    // 如果是用户消息，则截止到当前消息
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    compactionCoordinator.clearCompactionIfPrefixChanged(
                        conversationId,
                        before = conversation.messageNodes,
                        after = newConversation.messageNodes,
                        reason = "regenerate from user message",
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        compactionCoordinator.clearCompactionIfPrefixChanged(
                            conversationId,
                            before = conversation.messageNodes,
                            after = conversation.messageNodes.take(indexAt),
                            reason = "regenerate assistant message",
                        )
                        handleMessageComplete(conversationId, messageRange = 0..<indexAt)
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            } finally {
                releaseForegroundWork()
            }
        }

        // Same reasoning as sendQueuedMessage: a regenerate is a fresh main generation, so
        // drop any in-flight title/suggestion request for this conversation instead of
        // racing it against the regenerate.
        auxJobs.cancelAll(conversationId)
        session.setJob(job)
    }

    // ---- 处理工具调用审批 ----

    /** Scope of an "approve" decision. Once = this single tool call only. ChatScope =
     *  every future call of the same tool name in this conversation (until /new). There is no
     *  persistent "always" scope any more — the blacklist (Settings → Tool approvals) is where
     *  a tool stops asking for good. */
    enum class ApprovalScope { Once, ChatScope }

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
        scope: ApprovalScope = ApprovalScope.Once,
        toolName: String? = null,
    ) {
        val session = getOrCreateSession(conversationId)
        val convMutex = mutexFor(conversationId)

        // Snapshot the prior generation job BEFORE the appScope.launch below replaces it
        // via setJob. session.setJob runs synchronously after launch returns; the launched
        // body is dispatched and runs LATER (Dispatchers.Main posts to the looper). So
        // calling session.getJob() inside the body would return THIS very job — and
        // cancelAndJoin would self-cancel the resume coroutine: saveConversation's first
        // suspend then throws CancellationException, the tool stays Pending, and the
        // generation never resumes. The YOLO toggle masked this because auto-approval
        // skips the Pending → handleToolApproval path entirely.
        val priorGenerationJob = session.getJob()

        val approvalAccess = me.rerere.zeroagent.data.ai.tools.ConversationAccess.snapshot(conversationId)

        val releaseForegroundWork = foregroundWorkTracker.acquire()
        val job = appScope.launch {
            try {
                awaitForegroundWorkReady()
                convMutex.withLock {
                    // Hydrate from disk if the in-memory session is empty (post-restart
                    // path). Without this, the snapshot read below sees an empty
                    // Conversation and the saveConversation downstream OVERWRITES the
                    // persisted Pending tool with empty content — silent data loss.
                    ensureHydrated(conversationId)

                    afterPreviousGeneration(priorGenerationJob) {
                        val conversation = session.state.value
                        // Ignore double taps and stale approvals for completed or inactive tools.
                        if (conversation.currentMessages.none { message ->
                                message.getTools().any { it.toolCallId == toolCallId && it.isPending }
                            }) return@afterPreviousGeneration
                        val newApprovalState = when {
                            answer != null -> ToolApprovalState.Answered(answer)
                            approved -> ToolApprovalState.Approved
                            else -> ToolApprovalState.Denied(reason)
                        }

                        // Update the tool approval state on the SPECIFIC tool that was
                        // approved (already confirmed Pending above).
                        val updatedNodes = conversation.messageNodes.map { node ->
                            node.copy(
                                messages = node.messages.map { msg ->
                                    msg.copy(
                                        parts = msg.parts.map { part ->
                                            if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                                                part.copy(approvalState = newApprovalState)
                                            } else part
                                        }
                                    )
                                }
                            )
                        }
                        val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                        saveConversation(conversationId, updatedConversation)
                        if (approved && scope == ApprovalScope.ChatScope && toolName != null &&
                            conversation.currentMessages.any { message -> message.getTools().any {
                                it.toolCallId == toolCallId && it.toolName == toolName && it.isPending
                            } }
                        ) {
                            me.rerere.zeroagent.data.ai.tools.ConversationAccess.grantIfUnchanged(
                                conversationId, approvalAccess, toolName,
                            )
                        }
                    }
                }
                // Outside the mutex: kick off the resume generation if no tools remain pending.
                // (handleMessageComplete is a long-running flow collect; holding the mutex
                // through generation would block every subsequent state mutation for the turn.)
                val pendingNow = session.state.value.messageNodes.any { node ->
                    node.currentMessage.parts.any { part ->
                        part is UIMessagePart.Tool && part.isPending
                    }
                }
                if (!pendingNow) {
                    handleMessageComplete(conversationId)
                }
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            } finally {
                releaseForegroundWork()
            }
        }

        session.setJob(job, cancelPrevious = false)
    }

    /** Outcome of [rerunTool]. [Failure.message] is a short, non-localized diagnostic
     *  meant to be interpolated into a localized wrapper string in the UI, matching
     *  DirectModeActionRunner.StepResult.Failed's error strings. */
    sealed class RerunToolResult {
        data object Success : RerunToolResult()
        data class Failure(val message: String) : RerunToolResult()
    }

    /**
     * Re-executes the already-executed [UIMessagePart.Tool] identified by [toolCallId] in
     * [conversationId] and replaces its output in place. Modeled on the two existing
     * outside-the-generation-loop precedents: tryFastPathRoute's tool lookup + execute
     * above, and DirectModeActionRunner's guard + 60s timeout wrapping.
     *
     * Refuses while a generation job is active for the conversation so this can't race the
     * normal loop's own read-modify-write of the same message (same class of race
     * handleToolApproval's priorGenerationJob comment documents).
     *
     * Runs on [appScope] rather than the caller's coroutine - like sendMessage,
     * handleToolApproval, and regenerateAtMessage - so the up-to-60s tool.execute() and the
     * persist step below survive the caller (the sheet's button scope) being cancelled by a
     * dismiss/navigate-away instead of losing the result silently.
     */
    suspend fun rerunTool(conversationId: Uuid, toolCallId: String): RerunToolResult =
        appScope.async { rerunToolInternal(conversationId, toolCallId) }.await()

    private suspend fun rerunToolInternal(conversationId: Uuid, toolCallId: String): RerunToolResult {
        val session = getOrCreateSession(conversationId)
        if (session.isGenerating) {
            return RerunToolResult.Failure("a generation is already running for this conversation")
        }
        // Pin the session for the rest of this function. handleToolApproval keeps its
        // session alive across its own async work by setting the generation job
        // (isGenerating implies isInUse); rerunTool must NOT do that - setting a job would
        // flip `loading`/`isGenerating` for the whole conversation just because a tool is
        // being re-run, hiding regenerate buttons and other rerun buttons for no reason.
        // acquire()/release() pins the same way addConversationReference does (refCount),
        // without touching isGenerating. Without this, the 5s idle-eviction timer
        // (ConversationSession.IDLE_TIMEOUT_MS) can remove the session out from under the
        // up-to-60s tool.execute() below the moment the user navigates away (that drops
        // ChatVM's reference), and the write-back at the end would silently land in a
        // freshly-recreated blank session instead. The fallback below still covers the
        // remaining edge case where the session is gone anyway (e.g. dropSession(), which
        // explicitly ignores refcount).
        session.acquire()
        try {
            // Only the fast lookup/validation below runs under the mutex - matching the
            // class doc's "persist boundaries only" contract. tool.execute() (up to 60s)
            // below runs UNLOCKED so a concurrent handleToolApproval/stopGeneration on this
            // conversation doesn't block on this rerun.
            val (toolPart, tool) = mutexFor(conversationId).withLock {
                // Re-resolve the session under the lock instead of trusting `session`
                // pinned above: dropSession() (e.g. /new) removes it from the map
                // regardless of refcount, so a racing caller could have dropped it and
                // had a fresh session recreated for the same conversationId while we
                // waited for this lock. Checking/reading through the stale object would
                // miss a generation that started on the replacement session.
                val liveSession = getOrCreateSession(conversationId)
                if (liveSession.isGenerating) {
                    return RerunToolResult.Failure("a generation is already running for this conversation")
                }

                ensureHydrated(conversationId)
                val conversation = liveSession.state.value
                val toolPart = findToolCallPart(conversation, toolCallId)
                    ?: return RerunToolResult.Failure("tool call not found")
                if (!toolPart.isExecuted) {
                    return RerunToolResult.Failure("tool has not completed its first run yet")
                }

                val hardlineReason = me.rerere.zeroagent.data.ai.tools.HardlineCommandGuard
                    .checkTool(toolPart.toolName, toolPart.input)
                if (hardlineReason != null) {
                    return RerunToolResult.Failure("blocked: $hardlineReason")
                }

                val settings = settingsStore.settingsFlow.first()
                val model = resolveConversationChatModel(settings, conversation)
                    ?: return RerunToolResult.Failure("no chat model selected")
                val assistant = settings.assistantForModel(model)

                val tools = buildToolsForRerun(assistant, conversationId, conversation, model, settings)
                val tool = tools.firstOrNull { it.name == toolPart.toolName }
                    ?: return RerunToolResult.Failure("tool '${toolPart.toolName}' is not available")

                toolPart to tool
            }

            val startedAt = System.currentTimeMillis()
            val output = try {
                withTimeoutOrNull(60_000L) { tool.execute(toolPart.inputAsJson()) }
                    ?: return RerunToolResult.Failure("timed out after 60s")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "rerunTool: tool=${toolPart.toolName} threw", t)
                return RerunToolResult.Failure("${t::class.simpleName}: ${t.message.orEmpty()}".take(500))
            }

            // Apply against the LATEST state, not the pre-execute snapshot captured above -
            // sendMessage/handleToolApproval etc. can mutate the conversation during the
            // up-to-60s tool.execute() call. updateConversationState's atomic
            // StateFlow.update re-applies replaceToolCallPart against whatever is current
            // at write time, so a concurrent write in the meantime isn't silently reverted
            // the way overwriting with this function's stale `conversation` snapshot would.
            var applied = false
            val toolReplacer: (UIMessagePart.Tool) -> UIMessagePart.Tool = {
                applied = true
                it.copy(output = output, executionStartedAt = startedAt)
            }
            val sessionStillTracked = mutexFor(conversationId).withLock {
                if (!sessions.containsKey(conversationId)) return@withLock false
                updateConversationState(conversationId) { current ->
                    replaceToolCallPart(current, toolCallId, toolReplacer)
                }
                true
            }
            if (sessionStillTracked) {
                if (!applied) {
                    return RerunToolResult.Failure("tool call not found")
                }
                // Re-resolve the session rather than reading through `session` pinned at
                // the top of this function: updateConversationState() above applied the
                // update to whatever session is CURRENTLY in the map, which can be a
                // different object than `session` if dropSession() replaced it
                // mid-execute. Saving the old object's state here would silently revert
                // the update just made.
                saveConversation(conversationId, getOrCreateSession(conversationId).state.value)
            } else {
                // The session is gone despite the pin above (e.g. an explicit
                // dropSession() elsewhere ignores refcount). updateConversationState
                // would silently recreate a blank one and drop this result into it, so
                // instead apply straight to the repository-loaded conversation and
                // persist it there - mirrors renameConversation's sync-memory-then-persist
                // rule - so a completed re-run is never discarded.
                val stored = conversationRepo.getConversationById(conversationId)
                    ?: return RerunToolResult.Failure("conversation no longer exists")
                val updated = replaceToolCallPart(stored, toolCallId, toolReplacer)
                if (!applied) {
                    return RerunToolResult.Failure("tool call not found")
                }
                saveConversation(conversationId, updated)
            }
            return RerunToolResult.Success
        } finally {
            session.release()
        }
    }

    private suspend fun buildToolsForRerun(
        assistant: Assistant,
        conversationId: Uuid,
        conversation: Conversation,
        model: Model,
        settings: Settings,
    ): List<Tool> = chatToolFactory.createForRerun(
        ChatToolContext(settings, assistant, conversationId, model, conversation.workspaceCwd),
    )

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null,
        allowContextRetry: Boolean = true,
    ) {
        // A generation is one "turn" for read_window_tree's snapshot: within it a re-read can be
        // answered with "unchanged" or a delta, but a diff never spans two turns — so a model
        // coming back to a screen (possibly after a compaction) always gets the whole tree.
        me.rerere.zeroagent.data.ai.tools.local.TreeSnapshotCache.clear()
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val model = resolveConversationChatModel(settings, initialConversation)
            ?: throw IllegalStateException(context.getString(R.string.chat_select_model_first))
        // Keep durable IDs intact; the selected model owns this turn's tools and memory profile.
        val assistant = settings.assistantForModel(model)
        // Disabling a provider can leave a selected model whose provider has enabled=false:
        // the model lookup walks every provider regardless of state, so without this gate
        // inference fires (and bills) against the "disabled" provider's API key. Surface
        // the disabled state clearly instead of silently spending tokens.
        val resolvedProvider = model.findProvider(settings.providers)
        if (resolvedProvider == null) {
            throw IllegalStateException(
                "Selected model '${model.displayName.ifBlank { model.modelId }}' has no matching provider. " +
                    "Pick a different model in Settings."
            )
        }
        if (!resolvedProvider.enabled) {
            throw IllegalStateException(
                "Provider '${resolvedProvider.name}' is disabled — refusing to send. " +
                    "Re-enable it in Settings → Providers, or pick a different model."
            )
        }

        val senderName = assistant.name
        val useExternalWebSearch = shouldUseExternalWebSearch(assistant, model)

        val generationResult = runCatching {
            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // Tools the assistant has enabled that this model will never see. The gate lives in
            // the provider (the `tools` array is only emitted for a model marked as supporting
            // them), so the request goes out with no tools at all and the model has to improvise —
            // which is how you end up with raw tool-call markup in the chat bubble and nothing
            // pointing at the model setting three screens away. The old condition only looked at
            // MCP servers and external web search, so an assistant whose tools are all on-device
            // got no warning at all.
            //
            // Deliberately once per conversation: Assistant.localTools defaults to time_info, so
            // nearly every assistant has something to advertise, and an unguarded warning would
            // fire on every single turn.
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                val advertisesTools = useExternalWebSearch ||
                    mcpManager.getAllAvailableTools().isNotEmpty() ||
                    assistant.localTools.isNotEmpty()
                if (advertisesTools && toolUnavailableWarned.add(conversationId)) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value

            try {
                chatToolFactory.validateMcpServerNames()
            } catch (error: InvalidMcpServerNamesException) {
                sessions[conversationId]?.messageQueue?.pause()
                addError(
                    error = IllegalStateException(
                        context.getString(
                            R.string.error_mcp_invalid_server_name,
                            error.names.joinToString(", "),
                        )
                    ),
                    conversationId = conversationId,
                )
                return
            }

            // start generating
            val session = getOrCreateSession(conversationId)
            var compactedMessageView = if (messageRange == null) {
                compactionCoordinator.prepareMessagesForGeneration(
                    conversation = conversation,
                    settings = settings,
                    assistant = assistant,
                    model = model,
                    processingStatus = session.processingStatus,
                )
            } else {
                // Regenerating an assistant message: request the compacted view clipped to this
                // range so the request matches what a normal turn would have sent, instead of
                // the full raw history (RC1). loadCompactedMessageView resolves and, if stale,
                // clears the stored compaction exactly as the normal path does - reused here
                // rather than duplicated.
                ContextCompactionView.buildForRange(
                    conversation = conversation,
                    compaction = compactionCoordinator.loadCompactedMessageView(conversation).compaction,
                    endExclusive = messageRange.endInclusive + 1,
                )
            }
            val messagesForGeneration = if (messageRange != null) {
                compactedMessageView?.messages
                    ?: conversation.currentMessages.subList(
                        messageRange.start,
                        messageRange.endInclusive + 1,
                    )
            } else {
                compactedMessageView!!.messages
            }
            generationLoop.generateText(
                onUserPause = { session.messageQueue.pause() },
                settings = settings,
                model = model,
                processingStatus = session.processingStatus,
                // Read once per call so a background task gets its runtime context into the system prompt without
                // having to plumb a parameter all the way through sendMessage. Returns null
                // for in-app conversations that didn't register one.
                systemAddendum = me.rerere.zeroagent.data.ai.tools
                    .ConversationSystemAddendum.get(conversationId),
                canReviewTool = { toolName ->
                    toolName !in toolApprovalPreferences.snapshot().blacklist
                },
                isToolAutoApproved = { toolName ->
                    // Read the current conversation mode for each tool, including mid-turn revocation.
                    // Headless execution retains its separate policy and hardline restrictions.
                    val snapshot = toolApprovalPreferences.snapshot()
                    me.rerere.zeroagent.data.ai.tools.ToolApprovalPolicy.shouldAutoApprove(
                        toolName = toolName,
                        accessMode = me.rerere.zeroagent.data.ai.tools.ConversationAccess.mode(conversationId),
                        blacklist = snapshot.blacklist,
                        yolo = snapshot.yolo,
                        headless = me.rerere.zeroagent.data.ai.tools.HeadlessConversations
                            .shouldAutoApprove(conversationId),
                        allowedForChat = me.rerere.zeroagent.data.ai.tools.ToolApprovalAllowList
                            .isAllowedForChat(conversationId, toolName),
                    )
                },
                onAfterToolExecution = { generatedMessages ->
                    if (messageRange != null || !settings.enableAutoCompaction) {
                        null
                    } else {
                        val actualPromptTokens = generatedMessages.lastOrNull()?.usage
                            ?.promptTokens
                            ?.takeIf { it > 0 }
                        // The provider reports usage before tool execution. Estimate only the
                        // execution result appended afterwards so the threshold reflects the
                        // next request without turning automatic compaction into a preflight
                        // estimate of an otherwise unverified conversation.
                        val nextRequestTokens = ContextBudgetPlanner
                            .estimateInputTokens(generatedMessages)
                        val triggerTokens = compactionCoordinator.automaticCompactionTriggerTokens(settings, model)
                        if (actualPromptTokens == null ||
                            triggerTokens == null ||
                            nextRequestTokens < triggerTokens
                        ) {
                            null
                        } else {
                            Log.i(
                                TAG,
                                "Actual prompt usage reached compaction threshold after tool execution: " +
                                    "$actualPromptTokens reported, $nextRequestTokens including tool results " +
                                    ">= $triggerTokens",
                            )
                            val compacted = compactionCoordinator.prepareMessagesForGeneration(
                                conversation = getConversationFlow(conversationId).value,
                                settings = settings,
                                assistant = assistant,
                                model = model,
                                processingStatus = session.processingStatus,
                                force = true,
                            )
                            compacted.newlyCreatedAutoCompaction?.let { compaction ->
                                generatedMessages.lastOrNull()
                                    ?.takeIf { it.role == MessageRole.ASSISTANT }
                                    ?.let { sourceMessage ->
                                        attachAutomaticCompactionPresentation(
                                            conversationId = conversationId,
                                            messageId = sourceMessage.id,
                                            compaction = compaction,
                                        )
                                    }
                            }
                            compactedMessageView = compacted
                            compacted.messages
                        }
                    }
                },
                onBeforeModelRequest = {
                    awaitForegroundWorkReady()
                },
                messages = messagesForGeneration,
                assistant = assistant,
                conversationId = conversationId,
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                workspaceCwd = conversation.workspaceCwd,
                memories = if (assistant.useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                    add(workspaceReminderTransformer)
                    // 放在最后: 估算的是已注入模板 / workspace 引导之后的最终请求上下文
                    add(ContextBudgetReminderTransformer)
                },
                outputTransformers = outputTransformers,
                tools = try {
                    chatToolFactory.createForGeneration(
                        context = ChatToolContext(settings, assistant, conversationId, model, conversation.workspaceCwd),
                        useExternalWebSearch = useExternalWebSearch,
                        activation = { toolActivationFor(conversationId) },
                        onCompact = { instructions ->
                            compactConversationFromTool(
                                conversationId = conversationId,
                                conversation = getConversationFlow(conversationId).value,
                                settings = settings,
                                model = model,
                                instructions = instructions,
                            )
                        },
                    )
                } catch (error: InvalidMcpServerNamesException) {
                    addError(
                        error = IllegalStateException(context.getString(R.string.error_mcp_invalid_server_name, error.names.joinToString(", "))),
                        conversationId = conversationId,
                    )
                    return
                },
            ).onCompletion { completionCause ->
                // 取消 Live Update 通知
                notifications.cancelLiveUpdateNotification(conversationId)

                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = getConversationFlow(conversationId).value.copy(
                    messageNodes = getConversationFlow(conversationId).value.messageNodes.map { node ->
                        node.copy(messages = node.messages.map { it.finishReasoning() })
                    },
                    updateAt = Instant.now()
                )
                updateConversation(conversationId, updatedConversation)
                // The stream may end because it is waiting for approval, because the user
                // stopped it, or because transport failed. All three cases must leave the latest
                // partial assistant message and tool calls on disk.
                streamingPersistence.markDirty(conversationId)
                // onCompletion also runs for cancellation. Keep this final write alive after a
                // user presses Stop so a cancelled job cannot discard its last partial chunk.
                withContext(NonCancellable) {
                    streamingPersistence.persistNow(conversationId, updateSearchIndex = true)
                }

                // A Pending tool is an intentional pause, not a completed response. Surface the
                // approval request when the UI is backgrounded so the foreground-service chip
                // disappearing is not mistaken for the turn finishing. Transport failures and
                // cancellation are reported by their existing error paths and must not emit a
                // misleading completion notification here.
                if (
                    completionCause == null &&
                    !isForeground.value &&
                    settings.displaySetting.enableNotificationOnMessageGeneration
                ) {
                    val pendingTool = updatedConversation.currentMessages
                        .asReversed()
                        .asSequence()
                        .flatMap { it.parts.asReversed().asSequence() }
                        .filterIsInstance<UIMessagePart.Tool>()
                        .firstOrNull { it.isPending }
                    if (pendingTool != null) {
                        notifications.sendToolApprovalRequiredNotification(
                            conversationId = conversationId,
                            senderName = senderName,
                            pendingTool = pendingTool,
                        )
                    } else {
                        notifications.sendGenerationDoneNotification(conversationId, senderName)
                    }
                }
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val currentConversation = getConversationFlow(conversationId).value
                        val updatedConversation = if (compactedMessageView?.compaction != null) {
                            ContextCompactionView.mergeGeneratedMessages(
                                conversation = currentConversation,
                                view = compactedMessageView,
                                generatedMessages = chunk.messages,
                            )
                        } else {
                            currentConversation.updateCurrentMessages(chunk.messages)
                        }
                        updateConversation(conversationId, updatedConversation)
                        streamingPersistence.markDirty(conversationId)
                        streamingPersistence.persistIfDue(conversationId)

                        // Persist immediately when a tool transitions to "execution
                        // started but no output yet" — this writes the executionStartedAt
                        // breadcrumb to disk so a process kill mid-execute leaves a clear
                        // signal for the next replay (see GenerationLoop.kt's replay
                        // safety pass: Approved + executionStartedAt + empty → Denied
                        // interrupted_unknown_outcome). Without this, the marker stays in
                        // memory only and replay can't distinguish "freshly approved,
                        // never tried" from "interrupted mid-execute" → silent re-run.
                        val needsImmediatePersist = chunk.messages.lastOrNull()?.parts?.any { p ->
                            p is UIMessagePart.Tool &&
                                p.executionStartedAt != null &&
                                p.output.isEmpty() &&
                                p.approvalState is ToolApprovalState.Approved
                        } ?: false
                        if (needsImmediatePersist) {
                            streamingPersistence.persistNow(conversationId)
                        }

                        // 如果应用不在前台，发送 Live Update 通知
                        if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration && settings.displaySetting.enableLiveUpdateNotification) {
                            notifications.sendLiveUpdateNotification(conversationId, chunk.messages, senderName)
                        }
                    }
                }
            }
        }

        val generationFailure = generationResult.exceptionOrNull()
        // Screen-automation experience memory: whatever the agent just did to an app is written
        // to that app's cold-memory playbook here, by the host — the model never has to remember
        // to do it. Best-effort and no-op unless cold memory is configured.
        flushAutomationPlaybook(assistant)
        val lastMessage = getConversationFlow(conversationId).value.currentMessages.lastOrNull()
        // A tool round normally ends with an ASSISTANT message containing executed
        // tool calls. That message is still a valid point for a context-limit retry:
        // compact the conversation and retry after the tool results have been stored.
        val canRetryAfterContextLimit =
            lastMessage?.role != MessageRole.ASSISTANT ||
                lastMessage.getTools().any { it.isExecuted }
        if (
            allowContextRetry &&
            messageRange == null &&
            settings.enableAutoCompaction &&
            generationFailure != null &&
            generationFailure.isContextLimitError() &&
            canRetryAfterContextLimit
        ) {
            // Some providers omit context_length metadata or reject requests because their
            // system/tool schema overhead is larger than the local estimate. Force one
            // compaction pass and retry the same user turn once before surfacing the provider
            // error. The partial-output guard above prevents duplicating an already-streamed
            // assistant response.
            val session = getOrCreateSession(conversationId)
            val forcedView = runCatching {
                compactionCoordinator.prepareMessagesForGeneration(
                    conversation = getConversationFlow(conversationId).value,
                    settings = settings,
                    assistant = assistant,
                    model = model,
                    processingStatus = session.processingStatus,
                    force = true,
                    compactEntireContext = true,
                )
            }.onFailure {
                Log.w(TAG, "Context-limit retry compaction failed", it)
            }.getOrNull()
            if (forcedView?.compaction != null) {
                forcedView.newlyCreatedAutoCompaction?.let { compaction ->
                    getConversationFlow(conversationId).value.currentMessages
                        .lastOrNull { it.role == MessageRole.ASSISTANT }
                        ?.let { sourceMessage ->
                            attachAutomaticCompactionPresentation(
                                conversationId = conversationId,
                                messageId = sourceMessage.id,
                                compaction = compaction,
                            )
                        }
                }
                handleMessageComplete(
                    conversationId = conversationId,
                    messageRange = null,
                    allowContextRetry = false,
                )
                return
            }
        }

        // Retry status is transient. Clear it before surfacing the final result so a failed
        // stream does not leave the conversation stuck on “retrying” after the error card appears.
        getOrCreateSession(conversationId).processingStatus.value = null

        generationResult.onFailure {
            if (it is CancellationException) throw it
            sessions[conversationId]?.messageQueue?.pause()

            // 取消 Live Update 通知
            notifications.cancelLiveUpdateNotification(conversationId)

            // Persist the in-memory snapshot so the Auto/Pending → Denied transitions
            // GenerationLoop did inside its try/catch (the "generation_failed" recovery
            // path) survive a process restart. Without this, the failure path only
            // updates memory and the persisted DB row keeps the stale Pending state
            // forever — replay would re-run the loop against unrecoverable shape.
            runCatching {
                // A failed turn is always "stalled" (see isStalledTurn) - surface the continue
                // chip via the same chatSuggestions field generateSuggestion writes, so
                // ChatSuggestionsRow shows it even when enableSuggestion is off.
                val final = getConversationFlow(conversationId).value.copy(
                    chatSuggestions = listOf(context.getString(R.string.chat_suggestion_continue))
                )
                saveConversation(conversationId, final)
            }.onFailure { saveErr ->
                Log.w(TAG, "handleMessageComplete: failure-path save failed", saveErr)
            }

            it.printStackTrace()
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value

            if (isStalledTurn(succeeded = true, lastMessage = finalConversation.currentMessages.lastOrNull())) {
                // A stalled success (reasoning/tool-only turn) needs a way forward, not topic
                // suggestions - set the continue chip directly and skip generateSuggestion.
                saveConversation(
                    conversationId,
                    finalConversation.copy(
                        chatSuggestions = listOf(context.getString(R.string.chat_suggestion_continue))
                    )
                )
            } else {
                saveConversation(conversationId, finalConversation)
                launchAuxJob(conversationId, AuxJobKind.SUGGESTION) {
                    generateSuggestion(conversationId, finalConversation)
                }
            }

            launchAuxJob(conversationId, AuxJobKind.TITLE) {
                generateTitle(conversationId, finalConversation)
            }
        }
    }

    /**
     * Screen-automation experience memory — the WRITE half, and the **host** does it.
     *
     * The screen tools fed [AutomationRecorder] with what actually happened this turn (how the
     * app was entered, which selectors resolved, which missed). Here that is merged into the
     * app's cold-memory playbook, so recall works even on a model running without deep thinking
     * that never remembers to write anything itself. The observations live in a delimited block
     * ([AppPlaybookFile]), so prose the agent adds through `memory_write` is preserved untouched.
     *
     * Best-effort: failures are logged and swallowed, and it is a no-op unless cold memory is
     * configured. Observation state is drained first, so a retried turn cannot double-write.
     */
    private suspend fun flushAutomationPlaybook(assistant: Assistant) {
        val snapshots = AutomationRecorder.drain()
        if (snapshots.isEmpty()) return
        if (!assistant.coldMemoryEnabled) return
        val workspaceId = assistant.workspaceId?.toString() ?: return
        val dir = ColdMemoryRules.normalizeDir(assistant.coldMemoryDir) ?: return
        if (workspaceRepository.getById(workspaceId) == null) return

        for (snapshot in snapshots) {
            val fileName = AppPlaybookRules.fileNameFor(snapshot.packageName) ?: continue
            val path = if (dir.isEmpty()) fileName else "$dir/$fileName"
            runCatching {
                val existing = runCatching { workspaceRepository.readText(workspaceId, path) }
                    .getOrNull()
                    .orEmpty()
                val (prose, oldLines) = AppPlaybookFile.split(existing)
                workspaceRepository.writeText(
                    workspaceId,
                    path,
                    AppPlaybookFile.render(prose, AppPlaybookFile.mergeLines(oldLines, snapshot.lines)),
                    overwrite = true,
                )
            }.onFailure { e ->
                Log.d(TAG, "flushAutomationPlaybook($fileName) failed: ${e.message}")
            }
        }
    }

    // ---- 检查无效消息 ----

    private suspend fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // Close unresolved Auto/Pending tools left by a killed process, but keep the assistant
        // message itself. Removing the whole node here made the recovered tool call disappear as
        // soon as the user sent a follow-up message, and the next request then appeared to have
        // no trace of the interrupted turn. Approved/Denied/Answered tools remain resumable and
        // are handled by GenerationLoop's normal replay path.
        messagesNodes = messagesNodes.map { node ->
            val currentMessage = node.currentMessage
            val hasUnresumableTool = currentMessage.getTools().any {
                !it.isExecuted && !it.approvalState.canResumeToolExecution()
            }
            if (!hasUnresumableTool) {
                node
            } else {
                val repairedMessage = currentMessage.finishPendingTools(::cancelToolByRecovery)
                node.copy(
                    messages = node.messages.map { message ->
                        if (message.id == currentMessage.id) repairedMessage else message
                    }
                )
            }
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        if (messagesNodes != conversation.messageNodes) {
            compactionCoordinator.clearCompactionIfPrefixChanged(
                conversationId,
                before = conversation.messageNodes,
                after = messagesNodes,
                reason = "invalid message repair",
            )
            // Persist the repair before the model request starts. If the process is killed again
            // during the continuation, the historical tool call remains visible and replayable.
            saveConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
        }
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            )
        )
    }

    private fun cancelToolByRecovery(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"interrupted","error":"The previous generation ended before this tool completed. No tool execution was resumed automatically."}"""
                )
            ),
            approvalState = ToolApprovalState.Denied(
                "Previous generation interrupted before tool execution completed"
            )
        )
    }

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        // The newest user send can interrupt only the latest generation. Keep every older
        // node byte-for-byte intact so historical pending/failed tool records are not
        // rewritten as if the user cancelled them now.
        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        saveConversation(conversationId, updatedConversation)
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return@withContext

        val fallback = titleFallbackFrom(conversation.currentMessages)

        suspend fun applyTitle(title: String?) {
            if (title.isNullOrBlank()) return
            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                if (shouldWriteTitle(force, it.title)) {
                    saveConversation(conversationId, it.copy(title = title))
                }
            }
        }

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.fastModelId)
                ?: run { applyTitle(fallback); return@runCatching }
            val provider = model.findProvider(settings.providers)
                ?: run { applyTitle(fallback); return@runCatching }
            // Same defence as handleLlmTurn: don't burn tokens on a disabled provider.
            if (!provider.enabled) {
                applyTitle(fallback)
                return@runCatching
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = withContext(UsageCallContext(purpose = UsagePurpose.TITLE, conversationId = conversation.id.toString())) { providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText(maxLength = 500) })
                    ),
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            ) }

            applyTitle(result.message.toText().trim().ifBlank { fallback })
        }.onFailure {
            // Title generation is auxiliary — a failure here doesn't block the chat
            // and surfaces visibly as a blank conversation title in the list. Don't
            // push it onto the user-facing error stream: when the title model 429s,
            // the next message sees title.isBlank()==true, tries again, 429s again,
            // and the user gets a popup per message until they switch models. Match
            // the generateSuggestion pattern (log only) to keep the surface quiet.
            Log.w(TAG, "generateTitle failed", it)
            runCatching { applyTitle(fallback) }
                .onFailure { e -> Log.w(TAG, "generateTitle fallback apply failed", e) }
        }
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(
        conversationId: Uuid,
        conversation: Conversation,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            if (!settings.enableSuggestion) return@runCatching
            val model = settings.findModelById(settings.fastModelId)
                ?: return@runCatching
            val provider = model.findProvider(settings.providers) ?: return@runCatching
            // Same defence as handleLlmTurn: don't burn tokens on a disabled provider.
            if (!provider.enabled) return@runCatching

            sessions[conversationId]?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = withContext(UsageCallContext(purpose = UsagePurpose.SUGGESTION, conversationId = conversationId.toString())) { providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText(maxLength = 500) }),
                    )
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            ) }
            val suggestions =
                result.message.toText().split("\n").map { it.trim() }
                    .filter { it.isNotBlank() }

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessions[conversationId]?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            // Suggestion generation is auxiliary — log only, don't push onto the
            // user-facing error stream (mirrors the generateTitle failure handling).
            Log.w(TAG, "generateSuggestion failed", it)
        }
    }

    // ---- 压缩对话历史 ----

    /**
     * Keep the persisted compaction summary request-only, but show the user the automatic
     * compression as an executed tool on the assistant message that triggered it.
     */
    private fun attachAutomaticCompactionPresentation(
        conversationId: Uuid,
        messageId: Uuid,
        compaction: ConversationCompaction,
    ) {
        updateConversationState(conversationId) { current ->
            ContextCompactionPresentation.attachToMessage(
                conversation = current,
                messageId = messageId,
                tool = ContextCompactionPresentation.createTool(compaction),
            )
        }
    }

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> {
        val releaseForegroundWork = foregroundWorkTracker.acquire()
        return runCatching {
            awaitForegroundWorkReady()
            compactionCoordinator.compress(
                conversationId, conversation, additionalPrompt, targetTokens, keepRecentMessages,
            )
        }.also {
            releaseForegroundWork()
        }
    }

    private suspend fun awaitForegroundWorkReady() {
        // Reassert the service for every model round. This is cheap when it is already running,
        // and recovers when an OEM reclaimed it between a tool result and the next request.
        ChatGenerationForegroundService.start(context)
        if (!ChatGenerationForegroundService.awaitReady()) {
            // Keep the operation usable on devices that reject FGS promotion, while making the
            // degraded path explicit in logs. Normal interactive sends wait only until the
            // service confirms startForeground() and wake-lock acquisition.
            Log.w(TAG, "Chat foreground service was not ready before work started")
        }
    }

    /**
     * Manual compression can outlive the chat screen. Keep it on [AppScope] so removing the
     * activity from recents does not cancel an in-progress multi-pass compression request.
     */
    fun compressConversationAsync(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32,
    ): Deferred<Result<Unit>> = appScope.async {
        compressConversation(
            conversationId = conversationId,
            conversation = conversation,
            additionalPrompt = additionalPrompt,
            targetTokens = targetTokens,
            keepRecentMessages = keepRecentMessages,
        ).onFailure {
            addError(
                it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_compress_conversation),
            )
        }
    }

    /**
     * T-02 / ② — model-initiated compaction, driven by the optional `compact_context` tool.
     *
     * A deliberately thin wrapper over [compressConversation] so the summarisation logic keeps
     * living in exactly one place ([ConversationCompactionGenerator.generate] → `ContextCompactionPlanner`).
     * Nothing here re-implements, re-prompts or post-processes the summary.
     *
     * Failure model: this runs inside a tool call, so expected failures (no compression model
     * configured, empty conversation) come back as `compacted = false` with a readable note
     * rather than as an exception — the model should learn why nothing happened and can decide
     * to continue. Only genuinely unexpected errors propagate; GenerationLoop turns those into a
     * `tool_failed` envelope. Cancellation is always rethrown so "user pressed stop" is not
     * mistaken for a compact failure.
     */
    private suspend fun compactConversationFromTool(
        conversationId: Uuid,
        conversation: Conversation,
        settings: Settings,
        model: Model,
        instructions: String?,
    ): CompactionToolResult {
        val targetTokens = settings.getContextCompactionTargetTokens(
            compactionContextLength(settings, model),
        )
        val viewBefore = compactionCoordinator.loadCompactedMessageView(conversation)
        val tokensBefore = ContextBudgetPlanner.estimateContextTokens(viewBefore.messages)

        val result = compressConversation(
            conversationId = conversationId,
            conversation = conversation,
            additionalPrompt = instructions.orEmpty(),
            targetTokens = targetTokens,
            keepRecentMessages = COMPACTION_TOOL_KEEP_RECENT_MESSAGES,
        )
        result.exceptionOrNull()?.let { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w(TAG, "compact_context: failed for $conversationId: ${error.message}")
            return CompactionToolResult(
                compacted = false,
                tokensBefore = tokensBefore,
                tokensAfter = tokensBefore,
                summaryChars = 0,
                note = "Compaction did not run (${error.message ?: error::class.simpleName}). " +
                    "The context is unchanged.",
            )
        }

        val latest = getConversationFlow(conversationId).value
        val tokensAfter = ContextBudgetPlanner.estimateContextTokens(
            compactionCoordinator.loadCompactedMessageView(latest).messages,
        )
        val summaryChars = conversationRepo.getCompaction(conversationId)?.summary?.length ?: 0
        Log.i(
            TAG,
            "compact_context: $conversationId ${tokensBefore} -> ${tokensAfter} estimated tokens, " +
                "summary ${summaryChars} chars",
        )
        return CompactionToolResult(
            compacted = true,
            tokensBefore = tokensBefore,
            tokensAfter = tokensAfter,
            summaryChars = summaryChars,
            // Say this explicitly: the in-flight turn already holds its own copy of the
            // pre-compaction messages, so a model that immediately re-reads the "history"
            // would see the short version while this turn still sends the long one.
            note = "Compaction stored. It applies from the NEXT turn; this turn still carries the " +
                "raw history. Original messages remain stored and are not deleted.",
        )
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.state.value = conversation
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        // Atomic compare-and-set via StateFlow.update so two concurrent writers can't
        // race on read-modify-write (each reading the SAME pre-state and overwriting
        // each other). Also routes through checkFilesDelete so attached files keep
        // being garbage-collected when removed from the conversation.
        val session = getOrCreateSession(conversationId)
        session.state.update { current ->
            val next = update(current)
            if (next.id != conversationId) current
            else {
                checkFilesDelete(next, current)
                next
            }
        }
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     *
     * 若该会话当前有活跃 session（正在查看或后台生成），先同步内存态再落库：
     * 否则仅改数据库 folder_id，而内存里那份 Conversation 仍是旧 folderId，
     * 后续任意 saveConversation(id, state.value) 会用整对象把 folder_id 覆盖回旧值，导致移动丢失。
     * 先改内存可确保这段窗口内的整对象保存也带上新 folderId。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessions.containsKey(conversationId)) {
            updateConversationState(conversationId) { it.copy(folderId = folderId) }
        }
        conversationRepo.updateConversationFolderId(conversationId, folderId)
    }

    /**
     * 重命名会话。若该会话当前有活跃 session，先同步内存态再落库：
     * 否则仅改数据库标题，内存里那份 Conversation 仍是旧标题，
     * 后续任意 saveConversation(id, state.value) 会用整对象把标题覆盖回旧值，导致重命名丢失。
     */
    suspend fun renameConversation(conversationId: Uuid, title: String) {
        if (sessions.containsKey(conversationId)) {
            updateConversationState(conversationId) { it.copy(title = title) }
        }
        conversationRepo.renameConversation(conversationId, title)
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessions.values.any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     *
     * 先把内存中归属该文件夹的活跃 session folderId 置空，再删库：
     * 否则 clearFolder 只改了数据库，而活跃 session 内存态仍指向该文件夹，
     * 后续整对象保存会写回一个已被删除的 folder_id，导致会话在列表中悬空。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessions.values
            .filter { it.state.value.folderId == folderId }
            .forEach { updateConversationState(it.id) { c -> c.copy(folderId = null) } }
        folderRepository.deleteFolder(folderId)
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val session = sessions[newConversation.id]
        val queuedFiles = (session?.messageQueue?.state?.value?.messages.orEmpty() +
                listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val newFiles = newConversation.files + queuedFiles
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    private suspend fun flushStreamingPersistence() {
        streamingPersistence.flush(sessions.values.filter { it.isGenerating }.map { it.id })
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        streamingPersistence.withLock(conversationId) {
            val updatedConversation = conversation.copy()
            if (!persistConversationSnapshot(conversationId, updatedConversation)) return@withLock
            updateConversation(conversationId, updatedConversation)
        }
    }

    /** Room write shared by explicit saves and stream snapshots. Caller holds the persistence mutex. */
    private suspend fun persistConversationSnapshot(
        conversationId: Uuid,
        conversation: Conversation,
        updateSearchIndex: Boolean = true,
    ): Boolean {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return false // 新会话且为空时不保存
        }
        // Refuse to overwrite a non-empty stored row with an empty in-memory snapshot.
        // This is the silent-data-loss guard: handleToolApproval / stopGeneration / etc.
        // could be called against an unhydrated session (post-restart), build an empty
        // updatedConversation, and call saveConversation. Without this guard we'd wipe
        // the Pending tool the user was trying to approve.
        if (exists && conversation.messageNodes.isEmpty()) {
            val storedHasContent = runCatching {
                conversationRepo.getConversationById(conversation.id)?.messageNodes?.isNotEmpty() == true
            }.getOrDefault(false)
            if (storedHasContent) {
                Log.w(TAG, "saveConversation: refusing to overwrite non-empty $conversationId with empty snapshot — likely an unhydrated session")
                return false
            }
        }

        if (!exists) {
            conversationRepo.insertConversation(
                conversation = conversation,
                updateSearchIndex = updateSearchIndex,
            )
        } else {
            conversationRepo.updateConversation(
                conversation = conversation,
                updateSearchIndex = updateSearchIndex,
            )
        }

        // 删除消息或切换分支也可能解除工具审批阻塞，保存成功后重新检查队列。
        // 调度器仍会检查当前生成任务、待审批工具、暂停状态及编辑占位。
        dispatchNextQueuedMessage(conversationId)
        return true
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                translationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = resolveConversationChatModel(settings, currentConversation)
            ?.let { settings.assistantForModel(it) }
            ?: AssistantResolver.forConversation(settings, currentConversation.assistantId).assistant
        val processedParts = preprocessUserInputParts(parts, assistant)
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = processedParts,
                ),
                selectIndex = node.messages.size
            )
        }

        if (!edited) return

        compactionCoordinator.clearCompactionIfPrefixChanged(
            conversationId,
            before = currentConversation.messageNodes,
            after = updatedNodes,
            reason = "edit message",
        )
        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val forkConversation = createForkConversation(currentConversation, copiedNodes)

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        compactionCoordinator.clearCompactionIfPrefixChanged(
            conversationId,
            before = currentConversation.messageNodes,
            after = updatedNodes,
            reason = "select branch",
        )
        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        compactionCoordinator.clearCompactionIfPrefixChanged(
            conversationId,
            before = currentConversation.messageNodes,
            after = updatedConversation.messageNodes,
            reason = "delete message",
        )
        saveConversation(conversationId, updatedConversation)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        // Cancel BEFORE the mutex so the cancelled coroutines can drain their own writes
        // (which may try to acquire the same mutex via their save path). Also pause the
        // message queue so nothing auto-dispatches into the conversation we're stopping.
        val session = getOrCreateSession(conversationId)
        val jobs = synchronized(session) {
            session.messageQueue.pause()
            session.cancelJobs()
        }
        jobs.forEach { it.join() }

        val convMutex = mutexFor(conversationId)
        convMutex.withLock {
            // Hydrate from disk so we mark Pending tools cancelled even when the user
            // hits /stop after a process restart (sessions map is empty post-restart;
            // the old code returned early on the !sessions[id]?.getJob() check, leaving
            // the persisted Pending tool stranded forever).
            ensureHydrated(conversationId)

            val currentConversation = getConversationFlow(conversationId).value
            // Walk EVERY node, not just the last — Pending tools can appear on a non-last
            // node after branching / regenerate. finishPendingTools is now scoped to
            // tools that are NOT already in a terminal state, so a hardline-blocked
            // Denied tool keeps its original reason rather than being relabeled as
            // "cancelled by user".
            var changed = false
            val updatedNodes = currentConversation.messageNodes.map { node ->
                node.copy(
                    messages = node.messages.map { msg ->
                        val updated = msg.finishPendingTools(::cancelToolByUser)
                        if (updated !== msg) changed = true
                        updated
                    }
                )
            }
            if (!changed) return@withLock

            val updatedConversation = currentConversation.copy(messageNodes = updatedNodes)
            saveConversation(conversationId, updatedConversation)
        }
    }
}
