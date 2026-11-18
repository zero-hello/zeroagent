package me.rerere.zeroagent.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.zeroagent.data.ai.ContextBudgetPlanner
import me.rerere.zeroagent.data.ai.ContextCompactionPlanner
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.AutoCompactionThresholdMode
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.model.ConversationCompaction
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.utils.applyPlaceholders
import java.time.Instant
import java.util.Locale
import kotlin.uuid.Uuid
import me.rerere.zeroagent.data.usage.UsageCallContext
import me.rerere.zeroagent.data.usage.UsagePurpose

private const val COMPACTION_REQUEST_TIMEOUT_MS = 3 * 60_000L
private const val COMPACTION_TOTAL_TIMEOUT_MS = 8 * 60_000L
private const val COMPACTION_MAX_REQUEST_OUTPUT_TOKENS = 16_384
private const val MAX_PARALLEL_COMPACTION_REQUESTS = 4
private const val MAX_FULL_CONTEXT_MAP_GROUPS = 8

private const val TAG = "ChatService"

/** Generates and stores a summary only after checking the live source boundary. */
internal class ConversationCompactionGenerator(
    private val providerManager: ProviderManager,
    private val conversationRepo: ConversationRepository,
    private val currentConversation: (Uuid) -> Conversation,
) {
    suspend fun generate(
        conversation: Conversation,
        settings: Settings,
        messagesToCompress: List<UIMessage>,
        rawTailStartIndex: Int,
        additionalPrompt: String,
        targetTokens: Int,
        isAuto: Boolean,
    ): ConversationCompaction = withTimeout(COMPACTION_TOTAL_TIMEOUT_MS) {
        require(messagesToCompress.isNotEmpty()) { "No messages selected for compression" }
        require(rawTailStartIndex in 1..conversation.messageNodes.size) {
            "Invalid compaction boundary"
        }

        val model = resolveCompressionModel(settings)
            ?: throw IllegalStateException(compressionModelUnavailableMessage())
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")
        // Same defence as handleLlmTurn — refuse to compress against a disabled provider.
        // resolveCompressionModel already skips disabled providers, so this only guards against
        // a provider being disabled between resolution and this point.
        if (!provider.enabled) {
            throw IllegalStateException(
                "Provider '${provider.name}' is disabled — cannot compress. " +
                    "Re-enable it in Settings → Providers, or set a different compression model."
            )
        }

        val providerHandler = providerManager.getProviderByType(provider)
        // In token-threshold mode the user has supplied an explicit request-size ceiling for
        // this model family. Prefer it over missing/stale provider metadata. In percent mode we
        // still use the model's advertised context, falling back to the planner's conservative
        // default when a provider does not publish one.
        val compressionContextLength = compactionContextLength(settings, model)
        val hasExplicitCompressionContext = settings.autoCompactionThresholdMode ==
            AutoCompactionThresholdMode.TOKENS
        val inputBudgetTokens = ContextCompactionPlanner.inputBudgetTokens(
            contextLength = compressionContextLength,
            targetTokens = targetTokens,
            allowFullContext = hasExplicitCompressionContext,
        )
        val mapInputBudgetTokens = ContextCompactionPlanner.mapInputBudgetTokens(
            inputBudgetTokens = inputBudgetTokens,
            allowLargeContext = hasExplicitCompressionContext,
        )
        val rawContextRetentionReport = ContextCompactionPlanner.rawContextRetentionReport(
            conversation.currentMessages.drop(rawTailStartIndex)
        )
        // Reserve roughly one third of the configured target for a deterministic ledger of
        // completed tool results. This remains in the request even if the model's prose summary
        // ignores a tool record.
        val toolDigest = ContextCompactionPlanner.mandatoryToolExecutionDigest(
            messages = messagesToCompress,
            maxTokens = (targetTokens / 3).coerceAtLeast(1),
        )
        // The deterministic tool digest is appended after the model response. It does not
        // consume the model's output budget, so keep the configured prose target intact.
        val modelSummaryTargetTokens = targetTokens.coerceAtLeast(1)

        suspend fun compressSources(
            sources: List<String>,
            requestedTargetTokens: Int,
        ): String {
            val contentToCompress = sources.joinToString("\n\n")
            val prompt = buildString {
                // Put this before the editable prompt and the source material so it remains a
                // top-level instruction even when a user wrote a minimal custom template.
                append(ContextCompactionPlanner.requiredToolRetentionInstructions())
                if (rawContextRetentionReport.isNotBlank()) {
                    appendLine()
                    appendLine()
                    appendLine(
                        "SCOPE NOTE: This request summarizes only a prefix of the conversation. " +
                            "Completed tool calls remain verbatim after the generated summary. " +
                            "Do not claim that the whole conversation has no tool calls."
                    )
                }
                appendLine()
                appendLine()
                append(
                    settings.compressPrompt.applyPlaceholders(
                        "content" to contentToCompress,
                        "target_tokens" to requestedTargetTokens.toString(),
                        "additional_context" to if (additionalPrompt.isNotBlank()) {
                            "Additional instructions from user: $additionalPrompt"
                        } else "",
                        "locale" to Locale.getDefault().displayName
                    )
                )
            }

            val result = withTimeout(COMPACTION_REQUEST_TIMEOUT_MS) {
                withContext(UsageCallContext(purpose = UsagePurpose.COMPACTION, conversationId = conversation.id.toString())) { providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = backgroundTextGenerationParams(model, conversation.id).copy(
                        maxTokens = requestedTargetTokens,
                    ),
                ) }
            }

            return result.message.toText().trim()
                .takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        // Ordinary compression is a map pass over a small number of large groups. The full
        // source is used whenever it fits within a reasonable request count. Providers that omit
        // context metadata can otherwise turn one large tool result into dozens of tiny serial
        // requests, so switch to the bounded per-tool preview only for that pathological plan.
        val fullSources = messagesToCompress.map(ContextCompactionPlanner::sourceText)
        val fullSourceGroups = ContextCompactionPlanner.partitionSources(
            sources = fullSources,
            maxInputTokens = mapInputBudgetTokens,
        )
        var sourceGroups = fullSourceGroups
        var usingMapPreviews = false
        if (fullSourceGroups.size > MAX_FULL_CONTEXT_MAP_GROUPS) {
            val previewSources = messagesToCompress.map(ContextCompactionPlanner::mapSourceText)
            val previewGroups = ContextCompactionPlanner.partitionSources(
                sources = previewSources,
                maxInputTokens = mapInputBudgetTokens,
            )
            if (previewGroups.size < fullSourceGroups.size) {
                sourceGroups = previewGroups
                usingMapPreviews = true
            }
        }
        check(sourceGroups.isNotEmpty()) { "No usable messages selected for compression" }
        Log.i(
            TAG,
            "Compaction plan: model=${model.modelId}, contextLimit=" +
                "${compressionContextLength ?: "default"}, inputBudget=$mapInputBudgetTokens, " +
                "fullGroups=${fullSourceGroups.size}, selectedGroups=${sourceGroups.size}, " +
                "parallelism=$MAX_PARALLEL_COMPACTION_REQUESTS, mapPreviews=$usingMapPreviews",
        )

        suspend fun compressGroups(
            groups: List<List<String>>,
            requestedTargetTokens: Int,
        ): List<String> = groups
            .chunked(MAX_PARALLEL_COMPACTION_REQUESTS)
            .flatMap { batch ->
                coroutineScope {
                    batch.map { group ->
                        async(Dispatchers.IO) {
                            compressSources(group, requestedTargetTokens)
                        }
                    }.awaitAll()
                }
            }

        var reductionPasses = 0
        var finalSummary: String? = null
        while (finalSummary == null) {
            val passTargetTokens = if (sourceGroups.size == 1) {
                modelSummaryTargetTokens.coerceAtMost(COMPACTION_MAX_REQUEST_OUTPUT_TOKENS)
            } else {
                ContextCompactionPlanner.mapOutputTargetTokens(
                    finalTargetTokens = ContextCompactionPlanner.intermediateTargetTokens(
                        finalTargetTokens = modelSummaryTargetTokens,
                        inputBudgetTokens = mapInputBudgetTokens,
                    ),
                    groupCount = sourceGroups.size,
                    maxPerRequestTokens = COMPACTION_MAX_REQUEST_OUTPUT_TOKENS,
                )
            }
            Log.i(
                TAG,
                "Compaction pass ${reductionPasses + 1}: groups=${sourceGroups.size}, " +
                    "targetTokens=$passTargetTokens",
            )
            val summaries = compressGroups(sourceGroups, passTargetTokens)
            val combinedSummary = summaries.joinToString("\n\n")
            if (
                summaries.size == 1 ||
                ContextCompactionPlanner.estimateTokens(combinedSummary) <= mapInputBudgetTokens
            ) {
                // Preserve group order. For the common two-group case this is the final result,
                // so no extra reduce request is needed.
                finalSummary = combinedSummary
                continue
            }

            sourceGroups = ContextCompactionPlanner.partitionSources(
                sources = summaries,
                maxInputTokens = mapInputBudgetTokens,
            )
            reductionPasses++
            check(reductionPasses <= 12) {
                "Compression model did not reduce the conversation enough to merge its summaries"
            }
        }

        val expectedBoundary = conversation.messageNodes
            .take(rawTailStartIndex)
            .map { node -> node.id to node.currentMessage.id }
        val latestBoundary = currentConversation(conversation.id).messageNodes
            .take(rawTailStartIndex)
            .map { node -> node.id to node.currentMessage.id }
        check(expectedBoundary == latestBoundary) {
            "Conversation changed while context was being compressed"
        }

        val compaction = ConversationCompaction(
            conversationId = conversation.id,
            summary = listOfNotNull(
                finalSummary,
                toolDigest.takeIf { it.isNotBlank() },
                rawContextRetentionReport.takeIf { it.isNotBlank() },
            )
                .joinToString("\n\n"),
            tailStartNodeId = conversation.messageNodes.getOrNull(rawTailStartIndex)?.id,
            sourceEndNodeId = conversation.messageNodes[rawTailStartIndex - 1].id,
            summaryModelId = model.id,
            isAuto = isAuto,
            sourceTokenEstimate = ContextBudgetPlanner.estimateInputTokens(messagesToCompress),
            createdAt = Instant.now(),
        )
        conversationRepo.upsertCompaction(compaction)
        compaction
    }

}
