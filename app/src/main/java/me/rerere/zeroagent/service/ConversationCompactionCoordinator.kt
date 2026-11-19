package me.rerere.zeroagent.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.provider.Model
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.ContextBudgetPlanner
import me.rerere.zeroagent.data.ai.ContextCompactionPlanner
import me.rerere.zeroagent.data.ai.CompactedMessageView
import me.rerere.zeroagent.data.ai.ContextCompactionView
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.AutoCompactionThresholdMode
import me.rerere.zeroagent.data.datastore.getContextCompactionTargetTokens
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.model.ConversationCompaction
import me.rerere.zeroagent.data.model.MessageNode
import me.rerere.zeroagent.data.repository.ConversationRepository
import kotlin.uuid.Uuid

private const val TAG = "ChatService"
/**
 * Cap the automatic-compaction raw tail at half of the trigger threshold (after reserving room
 * for the summary itself). Landing right at the trigger means the very next tool result crosses
 * it again, forcing a brand-new summary - a new request prefix and therefore a full cache miss -
 * on almost every turn. Leaving this much headroom lets several turns reuse the same cached
 * prefix before compaction has to fire again.
 */

private const val COMPACTION_TAIL_BUDGET_PERCENT = 50
private const val MIN_AUTOMATIC_TAIL_BUDGET_TOKENS = 512

/** Builds request-only compaction views; the service supplies the existing per-session lock. */
internal class ConversationCompactionCoordinator(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val compactionGenerator: ConversationCompactionGenerator,
    private val currentConversation: (Uuid) -> Conversation,
    private val compactionMutexFor: (Uuid) -> Mutex,
) {
    suspend fun prepareMessagesForGeneration(
        conversation: Conversation,
        settings: Settings,
        assistant: Assistant,
        model: Model,
        processingStatus: MutableStateFlow<String?>,
        force: Boolean = false,
        compactEntireContext: Boolean = false,
    ): CompactedMessageView {
        var view = loadCompactedMessageView(conversation)
        if (!settings.enableAutoCompaction) {
            return if (view.compaction?.isAuto == true) {
                ContextCompactionView.build(conversation, null)
            } else {
                view
            }
        }

        // Automatic compaction is deliberately driven by provider-reported usage after a
        // tool result, or by an actual context-limit error. Do not estimate the history here
        // and compact before the model has had a chance to execute its tools.
        if (!force) return view

        Log.i(
            TAG,
            "Auto compaction starting for ${conversation.id}: " +
                "provider usage reached the configured threshold or context limit, " +
                "modelContext=${model.contextLength}",
        )
        processingStatus.value = context.getString(R.string.chat_page_compressing)
        try {
            var newlyCreatedAutoCompaction: ConversationCompaction? = null
            val compaction = compactionMutexFor(conversation.id).withLock {
                val latestConversation = currentConversation(conversation.id)
                view = loadCompactedMessageView(latestConversation)
                // A previous compaction may already cover every raw message. This can
                // happen when the provider reports another boundary before a new message
                // is appended. There is no further source material to summarize, so keep
                // the existing summary and continue with the compacted request view.
                if (
                    view.compaction != null &&
                    view.rawTailStartIndex >= latestConversation.messageNodes.size
                ) {
                    Log.i(TAG, "Auto compaction skipped: no new raw messages after existing summary")
                    view.compaction
                } else {
                    val targetTokens = settings.getContextCompactionTargetTokens(
                        compactionContextLength(settings, model),
                    )
                    val triggerTokens = automaticCompactionTriggerTokens(settings, model)
                    // Choose the tail boundary so the post-compaction request already lands
                    // comfortably below the trigger, instead of accepting a tail that sits right
                    // at it and re-summarizing on the next turn (see COMPACTION_TAIL_BUDGET_PERCENT).
                    val maxTailTokens = triggerTokens?.let {
                        (it * COMPACTION_TAIL_BUDGET_PERCENT / 100 - targetTokens)
                            .coerceAtLeast(MIN_AUTOMATIC_TAIL_BUDGET_TOKENS)
                    }
                    val firstCompaction = recordAutoCompactionIfCreated(
                        createAutomaticCompaction(
                            conversation = latestConversation,
                            currentView = view,
                            settings = settings,
                            targetTokens = targetTokens,
                            // A provider-reported context overflow means the local estimate
                            // missed provider overhead (often a very large tool result or
                            // schema). Drop the raw tail for this recovery pass so the
                            // continuation cannot immediately submit the same oversized request
                            // again. The deterministic tool ledger and the prose summary
                            // preserve the completed execution history, while the original
                            // messages remain stored in the conversation.
                            keepRecentToolCalls = if (compactEntireContext) {
                                0
                            } else {
                                settings.autoCompactionKeepRecentToolCalls
                            },
                            maxTailTokens = maxTailTokens,
                        ),
                    ) { newlyCreatedAutoCompaction = it }
                    val firstView = firstCompaction?.let {
                        ContextCompactionView.build(latestConversation, it)
                    } ?: view
                    if (
                        triggerTokens != null &&
                        firstView.rawTailStartIndex < latestConversation.messageNodes.size &&
                        ContextBudgetPlanner.estimateContextTokens(firstView.messages) >= triggerTokens
                    ) {
                        Log.i(
                            TAG,
                            "Automatic compaction tail still exceeds threshold; compacting the full active context",
                        )
                        recordAutoCompactionIfCreated(
                            createAutomaticCompaction(
                                conversation = latestConversation,
                                currentView = firstView,
                                settings = settings,
                                targetTokens = targetTokens,
                                keepRecentToolCalls = 0,
                            ),
                        ) { newlyCreatedAutoCompaction = it } ?: firstView.compaction
                    } else {
                        firstCompaction ?: view.compaction
                    }
                }
            }
            val latestConversation = currentConversation(conversation.id)
            return ContextCompactionView.build(latestConversation, compaction).copy(
                newlyCreatedAutoCompaction = newlyCreatedAutoCompaction,
            )
        } finally {
            processingStatus.value = null
        }
    }

    suspend fun loadCompactedMessageView(conversation: Conversation): CompactedMessageView {
        val compaction = conversationRepo.getCompaction(conversation.id)
            ?: return CompactedMessageView(
                messages = conversation.currentMessages,
                compaction = null,
                rawTailStartIndex = 0,
            )
        val view = ContextCompactionView.build(conversation, compaction)
        if (view.compaction == null) {
            Log.w(
                TAG,
                "Compaction for ${conversation.id} no longer resolves " +
                    "(sourceEnd=${compaction.sourceEndNodeId}, tailStart=${compaction.tailStartNodeId}, " +
                    "nodes=${conversation.messageNodes.size}); clearing",
            )
            conversationRepo.clearCompaction(conversation.id)
        }
        return view
    }

    /**
     * Drops the stored compaction only when [after] no longer carries the compacted prefix
     * that [before] had. Mutations confined to the raw tail keep the compaction.
     */
    suspend fun clearCompactionIfPrefixChanged(
        conversationId: Uuid,
        before: List<MessageNode>,
        after: List<MessageNode>,
        reason: String,
    ) {
        val compaction = conversationRepo.getCompaction(conversationId) ?: return
        if (ContextCompactionView.compactedPrefixUnchanged(compaction, before, after)) return
        Log.i(TAG, "Clearing compaction for $conversationId: compacted prefix changed ($reason)")
        conversationRepo.clearCompaction(conversationId)
    }

    /**
     * Returns the freshly created compaction, or `null` when the tail boundary did not
     * advance (a no-op -- see the check below). `null` lets callers tell a no-op apart from a
     * real compaction, which matters for [CompactedMessageView.newlyCreatedAutoCompaction]:
     * that field must stay unset for a no-op, or the UI attaches a "context compacted" card
     * for a compaction that never happened.
     */
    private suspend fun createAutomaticCompaction(
        conversation: Conversation,
        currentView: CompactedMessageView,
        settings: Settings,
        targetTokens: Int,
        keepRecentToolCalls: Int,
        maxTailTokens: Int? = null,
    ): ConversationCompaction? {
        if (conversation.messageNodes.size < 2) {
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        }

        val rawTailStartIndex = ContextCompactionPlanner.automaticTailStartIndex(
            messages = conversation.currentMessages,
            rawTailStartIndex = currentView.rawTailStartIndex,
            keepRecentToolCalls = keepRecentToolCalls,
            maxTailTokens = maxTailTokens,
        )

        // A subsequent compaction may legitimately consume the final raw tail message,
        // leaving the persisted summary as the only request-context prefix. The summary
        // entity uses a null tailStartNodeId to represent this boundary at messageNodes.size.
        if (rawTailStartIndex !in 1..conversation.messageNodes.size) {
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        }
        // The boundary can fail to advance even after the skip-guard one level up in
        // handleAutomaticCompaction (e.g. a provider-reported boundary racing a tool result
        // that has not been appended yet). That is a no-op, not a failure: keep the existing
        // compaction unchanged instead of aborting the whole turn with an exception that
        // propagates out of onAfterToolExecution. automaticCompactionNoOpResult still throws
        // when there is no existing compaction to fall back to -- unreachable in practice (it
        // would require rawTailStartIndex == 0 in a conversation that already passed the
        // messageNodes.size < 2 guard above), but the return type demands a decision. Return
        // null rather than that existing compaction: the caller cannot otherwise tell this
        // no-op apart from a freshly created compaction (see the kdoc above).
        if (rawTailStartIndex <= currentView.rawTailStartIndex) {
            Log.i(
                TAG,
                "Automatic compaction no-op for ${conversation.id}: rawTailStartIndex=$rawTailStartIndex " +
                    "did not advance past currentView.rawTailStartIndex=${currentView.rawTailStartIndex}",
            )
            automaticCompactionNoOpResult(currentView.compaction)
            return null
        }

        val messagesToCompress = buildList {
            currentView.compaction?.let { add(ContextCompactionView.summaryMessage(it)) }
            addAll(
                conversation.currentMessages.subList(
                    currentView.rawTailStartIndex,
                    rawTailStartIndex,
                )
            )
        }
        return compactionGenerator.generate(
            conversation = conversation,
            settings = settings,
            messagesToCompress = messagesToCompress,
            rawTailStartIndex = rawTailStartIndex,
            additionalPrompt = "",
            targetTokens = targetTokens.coerceAtLeast(1),
            isAuto = true,
        )
    }

    fun automaticCompactionTriggerTokens(settings: Settings, model: Model): Int? =
        when (settings.autoCompactionThresholdMode) {
            AutoCompactionThresholdMode.PERCENT -> model.contextLength
                ?.takeIf { it > 0 }
                ?.let { length ->
                    (length.toLong() * settings.autoCompactionThresholdPercent
                        .coerceIn(5, 95) / 100L)
                        .coerceAtLeast(1L)
                        .toInt()
                }
            AutoCompactionThresholdMode.TOKENS ->
                (settings.autoCompactionThresholdTokensK
                    .coerceAtLeast(1)
                    .toLong() * 1_000L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
        }

    suspend fun compress(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int,
    ) {
        compactionMutexFor(conversationId).withLock {
            val latestConversation = currentConversation(conversationId)
                .takeIf { it.messageNodes.isNotEmpty() }
                ?: conversation
            val allMessages = latestConversation.currentMessages
            if (allMessages.isEmpty()) {
                throw IllegalStateException(
                    context.getString(R.string.chat_page_compress_not_enough_messages)
                )
            }

            val currentView = loadCompactedMessageView(latestConversation)
            // The retained-message setting is an upper bound. If a short conversation has
            // fewer nodes than the requested tail but still overflowed because of a huge tool
            // result, compact every node instead of rejecting the manual operation.
            val requestedTailStart = if (keepRecentMessages in 1 until allMessages.size) {
                allMessages.size - keepRecentMessages
            } else {
                allMessages.size
            }
            // Existing compactions already cover the raw prefix. Advance their boundary only;
            // regenerating from all raw history is both needlessly expensive and can overflow
            // the compression model before it gets a chance to summarize anything.
            val rawTailStartIndex = maxOf(
                currentView.rawTailStartIndex,
                requestedTailStart,
            )
            val messagesToCompress = buildList {
                currentView.compaction?.let { add(ContextCompactionView.summaryMessage(it)) }
                addAll(
                    allMessages.subList(
                        currentView.rawTailStartIndex,
                        rawTailStartIndex,
                    )
                )
            }

            compactionGenerator.generate(
                conversation = latestConversation,
                settings = settingsStore.settingsFlow.first(),
                messagesToCompress = messagesToCompress,
                rawTailStartIndex = rawTailStartIndex,
                additionalPrompt = additionalPrompt,
                targetTokens = targetTokens.coerceAtLeast(1),
                isAuto = false,
            )
            Unit
        }
    }
}
