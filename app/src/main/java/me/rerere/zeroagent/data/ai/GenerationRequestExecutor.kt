package me.rerere.zeroagent.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.zeroagent.data.memory.lastMemoryTurns
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.handleTextGenerationResult
import me.rerere.ai.ui.limitContext
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.data.lab.ReplyContextEntry
import me.rerere.zeroagent.data.lab.ReplyContextStore
import me.rerere.zeroagent.data.lab.collectReplyContext
import me.rerere.zeroagent.data.lab.replyAttachmentKey
import me.rerere.zeroagent.data.productivity.ProjectStore
import me.rerere.zeroagent.data.ai.transformers.MessageTransformer
import me.rerere.zeroagent.data.ai.transformers.transforms
import me.rerere.zeroagent.data.ai.transformers.DocumentReadStatus
import me.rerere.zeroagent.data.ai.transformers.withReadStatus
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.AssistantMemory
import java.io.IOException
import kotlin.uuid.Uuid
import me.rerere.zeroagent.data.usage.UsageCallContext
import me.rerere.zeroagent.data.usage.UsageRunContexts
import me.rerere.zeroagent.data.usage.UsagePurpose

private const val TAG = "GenerationHandler"
private const val IMAGE_KEEP_LAST_N_TOOL_RESULTS = 2

/**
 * Replace older tool-result `Image` parts with a small text elision so the same JPEGs
 * aren't re-encoded into base64 on every subsequent step. We keep the
 * [IMAGE_KEEP_LAST_N_TOOL_RESULTS] most-recent tool-result-bearing assistant messages
 * verbatim and elide everything older. User uploads (`role=USER`) are NEVER elided —
 * those are real input the model needs to reason over. Assistant-generated images
 * (model image-gen output) are also kept verbatim as those are visible product, not
 * intermediate reasoning state.
 */
private fun List<UIMessage>.ageOldToolImages(): List<UIMessage> {
    var toolResultsWithImagesSeen = 0
    return this.asReversed().map { msg ->
        if (msg.role == MessageRole.USER) return@map msg
        val hasImageInTool = msg.parts.any { p ->
            p is UIMessagePart.Tool && p.output.any { it is UIMessagePart.Image }
        }
        if (!hasImageInTool) return@map msg
        toolResultsWithImagesSeen++
        if (toolResultsWithImagesSeen <= IMAGE_KEEP_LAST_N_TOOL_RESULTS) return@map msg
        val newParts = msg.parts.map { part ->
            if (part is UIMessagePart.Tool) {
                val newOutput = part.output.map { o ->
                    if (o is UIMessagePart.Image) {
                        UIMessagePart.Text(
                            "[image elided — original at ${o.url}; superseded by newer screenshots]"
                        )
                    } else o
                }
                part.copy(output = newOutput)
            } else part
        }
        msg.copy(parts = newParts)
    }.asReversed()
}

/** One model request and its recovery attempts; updates return to the owning Flow. */
internal class GenerationRequestExecutor(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
    private val systemPromptBuilder: SystemPromptBuilder,
) {
    suspend fun execute(
        assistant: Assistant,
        contextualMemoryActive: Boolean = false,
        settings: Settings,
        systemAddendum: String? = null,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationId: Uuid? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        workspaceCwd: String? = null,
        stepIndex: Int = 0,
        excludedContext: Set<String> = emptySet(),
        recalledPrompt: String? = null,
        continuePartial: Boolean = false,
    ) {
        val userTurnId = conversationId?.let {
            messages.lastOrNull { message -> message.role == MessageRole.USER && !message.isSynthetic }?.id?.toString()
        }
        val trackContext = conversationId != null && LabPreferences.enabled(context, "reply_context")
        val summaryId = if ((trackContext || "summary" in excludedContext) && conversationId != null) {
            conversationRepo.getCompaction(conversationId)?.sourceEndNodeId
        } else null
        val project = if ((trackContext || "project" in excludedContext) && conversationId != null) {
            ProjectStore.get(context).forConversation(conversationId.toString())
        } else null
        val projectPrefix = project?.let { "\n專案：${it.name}\n" }
        val projectStart = projectPrefix?.let { assistant.systemPrompt.indexOf(it) } ?: -1
        val includedMemories = memories.filterNot { "memory:${it.id}" in excludedContext }
        val replacesAssistantPrompt = assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()
        val contextCandidates = if (trackContext) buildList {
            recalledPrompt?.let { add(ReplyContextEntry("recalled", "語氣背景", it)) }
            if (assistant.enableMemory) includedMemories.forEach {
                add(ReplyContextEntry("memory:${it.id}", "記憶", it.content))
            }
            if (!replacesAssistantPrompt && projectStart >= 0 && "project" !in excludedContext) {
                add(ReplyContextEntry("project", "專案背景與檢索片段", assistant.systemPrompt.substring(projectStart).trim()))
            }
        } else emptyList()
        val latestUserId = messages.lastOrNull { it.role == MessageRole.USER && !it.isSynthetic }?.id
        val documentOutcomes = mutableMapOf<String, DocumentReadStatus>()
        val internalMessages = buildList {
            // Conversation-level system prompt override (upstream): when the assistant
            // allows it and the conversation supplies one, it replaces the assistant prompt.
            val effectiveSystemPrompt =
                if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                    conversationSystemPrompt
                } else {
                    if (projectStart >= 0 && "project" in excludedContext) assistant.systemPrompt.take(projectStart)
                    else assistant.systemPrompt
                }
            val memoryPrompt = if (assistant.enableMemory) {
                buildMemoryPrompt(memories = includedMemories)
            } else ""
            val recentChatsPrompt = if (assistant.enableRecentChatsReference) {
                buildRecentChatsPrompt(assistant, conversationRepo)
            } else ""
            val toolPrompts = tools.map { tool -> tool.systemPrompt(model, messages) }
            // Split into stable (assistant + tools) and volatile (memory + recent chats +
            // addendum) so prompt caching survives memory injection: the stable part is the
            // cached prefix, the volatile part sits after it. See SystemPromptBuilder.
            val (stableSystem, volatileSystem) = systemPromptBuilder.buildSections(
                assistantPrompt = effectiveSystemPrompt,
                identityPrompt = if (replacesAssistantPrompt) "" else ModelPersona.resolve(settings.modelPersonaId).prompt,
                phoneGuidance = PhoneAgentGuidance.build(tools.map { it.name }.toSet()),
                memoryPrompt = memoryPrompt,
                recentChatsPrompt = recentChatsPrompt,
                toolPrompts = toolPrompts,
                systemAddendum = systemAddendum,
            )
            val systemParts = buildList {
                if (stableSystem.isNotBlank()) add(UIMessagePart.Text(stableSystem))
                if (volatileSystem.isNotBlank()) add(UIMessagePart.Text(volatileSystem))
            }
            if (systemParts.isNotEmpty()) {
                add(UIMessage(role = MessageRole.SYSTEM, parts = systemParts, isSynthetic = true))
            }
            // Keeps the fork's multi-part system assembly and tool-image ageing, on top of
            // upstream's isSynthetic marker (keeps this built-up system prompt out of
            // TemplateTransformer) and its stepped truncation (which now preserves
            // prompt caching instead of trimming one message at a time).
            val history = if (contextualMemoryActive)
                messages.lastMemoryTurns() else messages
            addAll(history.limitContext(assistant.contextMessageLimit).ageOldToolImages().mapNotNull { message ->
                if (message.id == summaryId && "summary" in excludedContext) return@mapNotNull null
                if (excludedContext.none { it.startsWith("attachment:") }) return@mapNotNull message
                val parts = message.parts.filterNot { replyAttachmentKey(message, it)?.let(excludedContext::contains) == true }
                if (parts.isEmpty()) null else if (parts == message.parts) message else message.copy(parts = parts)
            })
        }.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            processingStatus = processingStatus,
            workspaceCwd = workspaceCwd,
            onDocumentRead = { messageId, url, status ->
                if (messageId == latestUserId) documentOutcomes[url] = status
            },
        )

        // Store the read status in the original message. Extracted text stays in the request.
        val initialMessages = messages
        val preStreamMessages = if (documentOutcomes.isEmpty()) messages else messages.map { message ->
            if (message.id != latestUserId) return@map message
            val parts = message.parts.map { part ->
                if (part is UIMessagePart.Document) documentOutcomes[part.url]?.let(part::withReadStatus) ?: part else part
            }
            if (parts == message.parts) message else message.copy(parts = parts)
        }
        val firstRequest = if (continuePartial) {
            val partial = preStreamMessages.last()
            continuationRequestMessages(internalMessages.filterNot { it.id == partial.id }, preStreamMessages)
        } else internalMessages
        val priorMessageIds = preStreamMessages.mapTo(hashSetOf()) { it.id }
        var activeMessages = preStreamMessages
        var handedOffContext = emptyList<ReplyContextEntry>()
        var pendingNewReplyReceipt = false

        fun captureHandoff(request: List<UIMessage>) {
            aiLoggingManager.addLog(AILogging.Generation(request.size, tools.size, stream))
            if (!trackContext || conversationId == null || !LabPreferences.enabled(context, "reply_context")) return
            val entries = collectReplyContext(request, contextCandidates, summaryId)
            val continuingReply = activeMessages.lastOrNull()?.takeIf {
                it.role == MessageRole.ASSISTANT && (continuePartial || it.id !in priorMessageIds)
            }
            if (continuingReply != null) {
                ReplyContextStore.record(conversationId, continuingReply.id, entries)
            } else {
                handedOffContext = entries
                pendingNewReplyReceipt = true
            }
        }

        suspend fun publish(updatedMessages: List<UIMessage>) {
            activeMessages = updatedMessages
            if (pendingNewReplyReceipt && conversationId != null && LabPreferences.enabled(context, "reply_context")) {
                updatedMessages.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT && (continuePartial || it.id !in priorMessageIds) }
                    ?.let {
                        ReplyContextStore.record(conversationId, it.id, handedOffContext)
                        pendingNewReplyReceipt = false
                    }
            }
            onUpdateMessages(updatedMessages)
        }
        var messages: List<UIMessage> = preStreamMessages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            topK = model.generation.topK,
            maxTokens = assistant.maxTokens,
            // Auto-retry is forced on: retrying a transient network failure is strictly better
            // than surfacing it as a dead error, so the switch was removed and the flag ignored.
            maxStreamRetries = settings.responseStreamMaxRetries,
            tools = tools,
            textToolCallParsing = !continuePartial && settings.parseTextToolCalls,
            reasoningLevel = me.rerere.ai.core.ReasoningCapabilities.effective(model, provider, model.generation.reasoningLevel),
            customHeaders = buildList {
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(model.customBodies)
            },
            sessionId = (conversationId ?: Uuid.random()).toString(),
        )
        try {
            if (preStreamMessages != initialMessages) publish(preStreamMessages)
            if (stream) {
                var receivedMeaningfulOutput = false
                var receivedAnyChunk = false
                // Preserve visible text if a continuation fails. Never reset the response to an empty snapshot.
                var continuationsUsed = 0
                while (true) {
                    receivedMeaningfulOutput = false
                    receivedAnyChunk = false
                    val attemptMessages = if (continuationsUsed == 0) {
                        firstRequest
                    } else {
                        val base = if (continuePartial) internalMessages.filterNot { it.id == preStreamMessages.last().id }
                            else internalMessages
                        continuationRequestMessages(base, messages)
                    }
                    try {
                    val streamChunkHandler = StreamChunkHandler(model)
                    // Stream-idle watchdog: lets the chat say "still working" vs "wedged"
                    // instead of counting seconds with no explanation (see StreamIdleNotifier).
                    val idleNotifier = StreamIdleNotifier()
                    // Stream-silence watchdog (see abortIfSilent). Two phases, two settings:
                    //
                    //  - first element: a socket that is silently dead emits nothing and fails
                    //    nothing, so without this the shared client's 10-minute readTimeout is the
                    //    only thing that ever notices. Armed only when a retry is actually
                    //    available - otherwise aborting would just turn a slow-but-alive reply into
                    //    a hard failure. 0 disables it.
                    //  - mid-reply: after the stream has started, a reply can still go quiet for
                    //    minutes while the socket stays open (a stalled provider, a gateway holding
                    //    the request, a dead connection the pings don't catch). The idle notifier
                    //    only *says* "stalled"; this is what actually recovers, by failing the
                    //    stream into the existing continue / restart path. Armed whenever the user
                    //    asked for it, because the post-output recovery does not need a retry.
                    val firstOutputTimeoutMs = if (params.maxStreamRetries > 0) {
                        settings.networkSetting.streamFirstOutputTimeoutSeconds
                            .coerceIn(0, STREAM_SILENCE_MAX_SECONDS)
                            .toLong() * 1_000L
                    } else {
                        0L
                    }
                    val stallTimeoutMs = settings.networkSetting.streamStallTimeoutSeconds
                        .coerceIn(0, STREAM_SILENCE_MAX_SECONDS)
                        .toLong() * 1_000L
                    // P2-12a - a streamed call is a model round trip exactly like the non-stream
                    // one below, so it carries the same ambient context. It was previously left
                    // unwrapped, which (once the decorator started wrapping `streamText`) would have
                    // filed every streamed call as UNKNOWN with no conversation / assistant.
                    // P2-12d — a sub-agent conversation carries a run attribution registered by
                    // SubAgentEngine; interactive turns resolve to null and keep today's context.
                    val usageAttribution = conversationId?.let { UsageRunContexts.get(it.toString()) }
                        coroutineScope {
                            val idleWatchdog = launch { watchStreamIdle(context, idleNotifier, processingStatus) }
                            try {
                                withContext(
                                    UsageCallContext(
                                        purpose = usageAttribution?.purpose
                                            ?: if (stepIndex > 0) UsagePurpose.TOOL_LOOP else UsagePurpose.MAIN,
                                        conversationId = conversationId?.toString(),
                                        assistantId = assistant.id.toString(),
                                        runId = usageAttribution?.runId,
                                        parentRunId = usageAttribution?.parentRunId, userTurnId = userTurnId,
                                    )
                                ) {
                                    providerImpl.streamText(
                                        providerSetting = provider,
                                        messages = attemptMessages,
                                        params = params
                                    )
                                }.abortIfSilent(
                                    firstElementTimeoutMs = firstOutputTimeoutMs,
                                    interChunkTimeoutMs = stallTimeoutMs,
                                    noFirstElement = {
                                        // A dead socket produces neither bytes nor an error; make
                                        // it an ordinary transport failure so retryWhen re-issues
                                        // the request on a fresh connection. Only reached before
                                        // ANY output, which is exactly when a retry is safe.
                                        IOException(
                                            "Model stream produced no output within " +
                                                "${firstOutputTimeoutMs / 1000}s"
                                        )
                                    },
                                    stalled = {
                                        // Reached with the reply already under way: the retry
                                        // policy refuses to re-run the turn here (that would
                                        // duplicate visible text), so this failure is what feeds
                                        // the continue / restart path instead.
                                        IOException(
                                            "Model stream went silent for " +
                                                "${stallTimeoutMs / 1000}s after its first output"
                                        )
                                    },
                                ).onCompletion { cause ->
                                    // Some SSE implementations report an abruptly closed socket through onClosed
                                    // without an exception. Treat a clean close with no chunks at all as a transport
                                    // failure so the retry policy can recover a background continuation. A clean
                                    // close after chunks arrived but none produced parseable parts is a permanent
                                    // condition (unrecognized part shapes), not a transport hiccup, so it must not
                                    // burn retries - log it and let the generation end normally with an empty reply.
                                    if (cause == null && shouldReportEmptyGenerationStream(receivedAnyChunk)) {
                                        throw IOException("Model stream closed without meaningful output")
                                    }
                                    if (cause == null && receivedAnyChunk && !receivedMeaningfulOutput) {
                                        Log.w(
                                            TAG,
                                            "streamText: stream closed after chunks arrived but none contained " +
                                                "parseable parts; ending without retry",
                                        )
                                    }
                                }.onStart { captureHandoff(attemptMessages) }.retryWhen { cause, retryAttempt ->
                                    val shouldRetry = shouldRetryGenerationStreamFailure(
                                        failure = cause,
                                        retryAttempt = retryAttempt,
                                        maxRetries = params.maxStreamRetries,
                                        receivedMeaningfulOutput = receivedMeaningfulOutput,
                                    )
                                    if (shouldRetry) {
                                        // A new attempt is about to start collecting from scratch: reset the
                                        // per-attempt "did anything arrive" flag so onCompletion's transport-failure
                                        // check reflects this attempt, not a chunk seen in an earlier one.
                                        receivedAnyChunk = false
                                        idleNotifier.onAttemptStart()
                                        val delayMs = generationStreamRetryDelayMs(retryAttempt)
                                        processingStatus.value = retryStatusText(
                                            context = context,
                                            retryNumber = retryAttempt + 1,
                                            maxRetries = params.maxStreamRetries,
                                            failure = cause,
                                        )
                                        Log.w(
                                            TAG,
                                            "streamText: retrying after failure " +
                                                "(${retryAttempt + 1}/${params.maxStreamRetries}) in ${delayMs}ms",
                                            cause,
                                        )
                                        delay(delayMs)
                                    }
                                    shouldRetry
                                }.collect {
                                    receivedAnyChunk = true
                                    val meaningful = isMeaningfulStreamChunk(it)
                                    if (meaningful) {
                                        receivedMeaningfulOutput = true
                                        clearRetryStatus(processingStatus)
                                    }
                                    idleNotifier.onChunk(meaningful)
                                    messages = streamChunkHandler.handle(messages, it)
                                    publish(messages)
                                }
                            } finally {
                                idleWatchdog.cancel()
                            }
                        }
                        // The stream finished normally: leave the attempt loop. Without this the
                        // `while (true)` above immediately re-issues the very same request, so a
                        // plain reply never ends (and a tool-call reply never reaches the tool).
                        break
                    } catch (failure: Throwable) {
                        // Only a plain-text partial can be continued; a half-streamed tool call
                        // would produce a dangling call and no result, which providers reject.
                        val partial = messages.lastOrNull()
                            ?.takeIf { it.role == MessageRole.ASSISTANT }
                        if (canContinueFromPartial(partial) &&
                            shouldContinueGenerationStream(
                                failure = failure,
                                receivedMeaningfulOutput = receivedMeaningfulOutput,
                                continuationsUsed = continuationsUsed,
                            )
                        ) {
                            continuationsUsed++
                            processingStatus.value = continueStatusText(
                                context = context,
                                number = continuationsUsed,
                                max = MAX_STREAM_CONTINUATIONS,
                            )
                            Log.w(
                                TAG,
                                "streamText: continuing after a mid-stream failure " +
                                    "($continuationsUsed/$MAX_STREAM_CONTINUATIONS)",
                                failure,
                            )
                            continue
                        }
                        throw failure
                    }
                }
            } else {
                val result = retryGenerationTransportRequest(
                    maxRetries = params.maxStreamRetries,
                    onRetry = { retryNumber, failure ->
                        processingStatus.value = retryStatusText(
                            context = context,
                            retryNumber = retryNumber,
                            maxRetries = params.maxStreamRetries,
                            failure = failure,
                        )
                    },
                ) {
                    val usageAttribution = conversationId?.let { UsageRunContexts.get(it.toString()) }
                    withContext(
                        UsageCallContext(
                            purpose = usageAttribution?.purpose
                                ?: if (stepIndex > 0) UsagePurpose.TOOL_LOOP else UsagePurpose.MAIN,
                            conversationId = conversationId?.toString(),
                            assistantId = assistant.id.toString(),
                            runId = usageAttribution?.runId,
                            parentRunId = usageAttribution?.parentRunId, userTurnId = userTurnId,
                        )
                    ) {
                        captureHandoff(firstRequest)
                        providerImpl.generateText(
                            providerSetting = provider,
                            messages = firstRequest,
                            params = params,
                        )
                    }
                }
                messages = messages.handleTextGenerationResult(result = result, model = model)
                publish(messages)
            }
        } finally {
            processingStatus.value = null
        }
    }

}
