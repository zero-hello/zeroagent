package me.rerere.zeroagent.data.ai

import android.util.Log
import me.rerere.zeroagent.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.util.redactSecrets
import me.rerere.zeroagent.data.ai.limits.ToolRuntimeLimits
import me.rerere.zeroagent.data.ai.tools.ConversationToolApproval
import me.rerere.zeroagent.data.ai.tools.ToolExecutionRetryPolicy
import me.rerere.zeroagent.data.ai.tools.truncateToolResult
import me.rerere.zeroagent.data.model.Assistant
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"
private const val MAX_TOOL_OUTPUT_CHARS = 32 * 1024
private const val TOOL_OUTPUT_PREVIEW_CHARS = 4 * 1024

internal data class GenerationToolBatchResult(
    val messages: List<UIMessage>,
    val executedTools: List<UIMessagePart.Tool>,
    val waitingForFreshApproval: Boolean,
    val loopGuardTripCount: Int,
)

/** No independent scope or approval state: execute within the caller's turn and run control. */
internal class GenerationToolExecutor(
    private val outputDirectory: () -> File,
    private val json: Json,
    private val platformEffects: GenerationPlatformEffects,
) {
    suspend fun execute(
        resolvedTools: List<UIMessagePart.Tool>,
        initialMessages: List<UIMessage>,
        toolsInternal: List<Tool>,
        approval: ConversationToolApproval,
        assistant: Assistant,
        conversationId: Uuid?,
        turnStartMs: Long,
        initialLoopGuardTripCount: Int,
        runControl: AgentRunControl,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
    ): GenerationToolBatchResult {
        var messages = initialMessages
        var loopGuardTripCount = initialLoopGuardTripCount
        val executedTools = arrayListOf<UIMessagePart.Tool>()
        var waitingForFreshApproval = false
        resolvedTools.forEach { proposedTool ->
            var tool = proposedTool
            if (waitingForFreshApproval) return@forEach
            // A preceding long-running tool may outlive a mode change or blacklist edit.
            val checked = approval.recheck(tool)
            if (checked.isPending) {
                executedTools += checked
                waitingForFreshApproval = true
                return@forEach
            }
            tool = checked
            when (tool.approvalState) {
                is ToolApprovalState.Denied -> {
                    // Tool was denied by user
                    val reason = (tool.approvalState as ToolApprovalState.Denied).reason
                    executedTools += tool.copy(
                        output = listOf(
                            UIMessagePart.Text(
                                json.encodeToString(
                                    buildJsonObject {
                                        put(
                                            "error",
                                            JsonPrimitive("Tool execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}")
                                        )
                                    }
                                )
                            )
                        )
                    )
                }

                is ToolApprovalState.Answered -> {
                    // Tool was answered by user (e.g., ask_user tool)
                    val answer = (tool.approvalState as ToolApprovalState.Answered).answer
                    executedTools += tool.copy(
                        output = listOf(
                            UIMessagePart.Text(answer)
                        )
                    )
                }

                is ToolApprovalState.Pending -> {
                    // Should not reach here, but just in case
                }

                else -> {
                    // Auto or Approved - execute the tool.
                    //
                    // Defence-in-depth HARDLINE re-check: the primary check at line ~442
                    // only runs when approvalState is Auto (the generation step that just
                    // proposed the tool). On the resume path (pendingTools branch above)
                    // tools arrive here with state=Approved and skip that block entirely.
                    // Re-check here so that a hardline-matched tool persisted in Approved
                    // state from an old DB row (pre-hardline schema, direct DB edit) can
                    // never execute via the resume path.
                    val resumeHardlineReason = me.rerere.zeroagent.data.ai.tools
                        .HardlineCommandGuard.checkTool(tool.toolName, tool.input)
                    if (resumeHardlineReason != null) {
                        Log.w(TAG, "generateText: resume-path hardline re-check blocked ${tool.toolName}: $resumeHardlineReason")
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(
                                    json.encodeToString(buildJsonObject {
                                        put("error", JsonPrimitive(
                                            "blocked by safety floor (hardline): $resumeHardlineReason. " +
                                                "This command cannot run via the agent under any circumstances."
                                        ))
                                    })
                                )
                            )
                        )
                        return@forEach
                    }

                    // T-10 / (9) — headless auto-approval floor. A conversation registered
                    // via HeadlessConversations.mark() (cron / sub-agent / skill-tester /
                    // external-automation) has NO approval channel, so ChatService's
                    // isToolAutoApproved hands it every tool. That must not extend to the
                    // tools the user reserved for a per-call confirmation, nor to the ones
                    // that capture the user's surroundings. The refusal cannot be a plain
                    // "not approved": that flips the tool to Pending and the loop below
                    // breaks waiting for an answer that can never come. Emit an envelope
                    // instead — the same shape as the hardline floor above — so the model
                    // sees a structured refusal and can pivot. Sitting here rather than in
                    // the approval lookup is deliberate: it also covers the resume path,
                    // where tools arrive already Approved.
                    val headlessRefusal = me.rerere.zeroagent.data.ai.tools.HeadlessToolApprovalPolicy
                        .refusalEnvelopeFor(
                            toolName = tool.toolName,
                            headless = conversationId?.let { id ->
                                me.rerere.zeroagent.data.ai.tools.HeadlessConversations
                                    .shouldAutoApprove(id)
                            } ?: false,
                        )
                    if (headlessRefusal != null) {
                        Log.w(TAG, "generateText: ${tool.toolName} refused — headless conversation has no approval channel")
                        executedTools += tool.copy(
                            output = listOf(UIMessagePart.Text(headlessRefusal))
                        )
                        return@forEach
                    }

                    // Loop-guard: check whether the model has already called this exact
                    // tool with the same args multiple times in this turn. Refuse a
                    // repeat run and inject a "loop_detected" envelope so the model has
                    // to pivot to a different approach. Cost safety net.
                    val signature = tool.toolName + "::" + tool.input
                    // "This turn" = since the most recent user message. Earlier
                    // identical calls in PREVIOUS turns aren't the model flailing
                    // now — they're history, and counting them produces a confusing
                    // "you already called this 3 times in this turn" envelope after
                    // a single fresh call.
                    val turnStartIndex = messages.indexOfLast { it.role == MessageRole.USER }
                    val turnSlice = messages.subList(
                        (turnStartIndex + 1).coerceAtLeast(0),
                        messages.size
                    )
                    // Flatten this turn's executed tool calls in chronological order. The
                    // epoch ms (for the freshness-TTL bypass) comes from the parent
                    // message's finish/create time, matching the prior inline behaviour.
                    val priorCalls = turnSlice.flatMap { msg ->
                        val epochMs = (msg.finishedAt ?: msg.createdAt)
                            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                        msg.parts.filterIsInstance<UIMessagePart.Tool>()
                            .filter { it.isExecuted }
                            .map { PriorToolCall(it.toolName, it.toolName + "::" + it.input, epochMs) }
                    }
                    val loopDecision = LoopGuard.evaluate(
                        priorCalls = priorCalls,
                        toolName = tool.toolName,
                        signature = signature,
                        nowMs = System.currentTimeMillis(),
                    )
                    val priorOccurrences = loopDecision.priorOccurrences
                    if (loopDecision.block) {
                        loopGuardTripCount++
                        Log.w(TAG, "generateText: loop-guard tripped on $signature (${priorOccurrences + 1} repeat, trip #$loopGuardTripCount this turn); injecting bail-out envelope")
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("error", JsonPrimitive("loop_detected"))
                                            put(
                                                "recovery", JsonPrimitive(
                                                    "You have already called ${tool.toolName} with identical arguments " +
                                                        "${priorOccurrences} time(s) in this turn without making progress. " +
                                                        "Stop retrying. Either: (a) change the args meaningfully, (b) try a " +
                                                        "different tool that addresses the underlying request, or (c) hand " +
                                                        "back to the user with what you have so far. Examples: for 'search " +
                                                        "X in chrome' use open_url(\"https://www.google.com/search?q=X\") " +
                                                        "instead of fighting Chrome's URL bar via set_text; for terminal " +
                                                        "tasks use termux_run_command instead of typing into Termux."
                                                )
                                            )
                                        }
                                    )
                                )
                            )
                        )
                        // Skip the actual execution. The next generation step will see
                        // this envelope and (if the model is well-prompted by the skill
                        // docs) will pivot to a different approach.
                        return@forEach
                    }
                    // Pre-parse args BEFORE the runCatching block so we can surface a
                    // clean structured envelope when the LLM provider truncates the
                    // streaming response mid-string (max_tokens hit, network drop, etc.).
                    // Without this, kotlinx.serialization's raw exception message —
                    // which includes the entire failed input — lands in the LLM-facing
                    // `detail` field, can be thousands of tokens, and the model often
                    // retries the same too-big call.
                    val parsedArgs = runCatching {
                        json.parseToJsonElement(tool.input.ifBlank { "{}" })
                    }
                    if (parsedArgs.isFailure) {
                        val cause = parsedArgs.exceptionOrNull()
                        Log.w(TAG, "tool ${tool.toolName} args failed to parse (likely truncated stream)", cause)
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(
                                    json.encodeToString(buildJsonObject {
                                        put("error", JsonPrimitive("invalid_tool_args"))
                                        put(
                                            "detail",
                                            JsonPrimitive(
                                                (cause?.message ?: cause?.javaClass?.simpleName ?: "json_parse_failed")
                                                    .take(200)
                                            ),
                                        )
                                        put(
                                            "recovery",
                                            JsonPrimitive(
                                                "Tool args JSON failed to parse — most often the provider's " +
                                                    "stream was cut off mid-string by max_tokens or a network drop. " +
                                                    "Retry with a shorter call. For long payloads (e.g. a 4000-char " +
                                                    "message), split into multiple smaller tool calls or shrink the " +
                                                    "content."
                                            ),
                                        )
                                        put(
                                            "exception",
                                            JsonPrimitive(cause?.javaClass?.simpleName ?: "JsonParseException"),
                                        )
                                    })
                                )
                            )
                        )
                        return@forEach
                    }
                    // Resolve the tool def BEFORE the runCatching block, same reason as
                    // parsedArgs above: this is not a random tool-body throw, so it gets
                    // its own structured envelope naming exactly what was called and what
                    // was actually available — rather than the generic 500-char-capped
                    // exception message the runCatching/.onFailure below produces (#88:
                    // this is the self-diagnosing surface for a server that connects and
                    // lists tools but contributes zero entries to the dispatch list, e.g.
                    // a newly mcp_add-ed server never enabled for this assistant).
                    val toolDef = toolsInternal.find { toolDef -> toolDef.name == tool.toolName }
                    if (toolDef == null) {
                        Log.w(TAG, "tool ${tool.toolName} not found among ${toolsInternal.size} tools available this turn")
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(
                                    json.encodeToString(buildJsonObject {
                                        put("error", JsonPrimitive("tool_not_found"))
                                        put(
                                            "detail",
                                            JsonPrimitive("Tool '${tool.toolName}' was called but is not among the tools available this turn."),
                                        )
                                        put(
                                            "tools_available_this_turn",
                                            JsonPrimitive(toolsInternal.joinToString(", ") { it.name }.take(1500)),
                                        )
                                    })
                                )
                            )
                        )
                        return@forEach
                    }
                    runCatching {
                        val args = parsedArgs.getOrThrow()
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "generateText: executing tool ${toolDef.name} with args: ${redactSecrets(args)}")
                        }
                        // Mark the tool as "execution started" BEFORE actually running.
                        // ChatService persists this when it sees the chunk so a process
                        // kill between mark-and-output leaves a clear breadcrumb on disk:
                        // on replay we'll see Approved + executionStartedAt + empty output
                        // and refuse to silently re-run. The mark survives via the
                        // existing emit-and-persist plumbing — see ChatService chunk
                        // handler's needsImmediatePersist branch.
                        val markedTool = tool.copy(executionStartedAt = System.currentTimeMillis())
                        run {
                            val lastMsg = messages.lastOrNull()
                            if (lastMsg != null) {
                                val markedParts = lastMsg.parts.map { p ->
                                    if (p is UIMessagePart.Tool && p.toolCallId == tool.toolCallId) markedTool else p
                                }
                                messages = messages.dropLast(1) + lastMsg.copy(parts = markedParts)
                                onUpdateMessages(messages)
                            }
                        }
                        // Hard-cap individual tool execution at the remaining wall-clock
                        // budget so a single tool with its OWN long timeout (camera 5min,
                        // ssh_exec timeout_seconds=300) can't carry the turn past the
                        // global ${ToolRuntimeLimits.turnBudgetMs}ms cap. If the budget is
                        // already blown when we start the tool, return a structured
                        // wall-clock envelope instead of even attempting.
                        ensureAgentExecutionActive()
                        platformEffects.onToolExecution(toolDef.name, runControl, runControl::pause)
                        val remainingMs = ToolRuntimeLimits.turnBudgetMs -
                            (android.os.SystemClock.elapsedRealtime() - turnStartMs)
                        val result = if (remainingMs <= 0L) {
                            Log.w(TAG, "generateText: ${toolDef.name} skipped — wall-clock budget already exceeded")
                            listOf(UIMessagePart.Text(json.encodeToString(buildJsonObject {
                                put("error", JsonPrimitive("tool_cancelled_wall_clock"))
                                put("detail", JsonPrimitive("turn budget exceeded before tool started"))
                            })))
                        } else if (assistant.enableToolExecutionRetry &&
                            ToolExecutionRetryPolicy.isIdempotentReadOnly(toolDef.name, args)
                        ) {
                            executeReadOnlyToolWithRetry(
                                toolDef = toolDef,
                                args = args,
                                turnStartMs = turnStartMs,
                                firstAttemptRemainingMs = remainingMs,
                            )
                        } else {
                            withTimeoutOrNull(remainingMs) { ensureAgentExecutionActive(); toolDef.execute(args) }
                                ?: run {
                                    Log.w(TAG, "generateText: ${toolDef.name} cancelled — wall-clock budget exhausted mid-execution")
                                    listOf(UIMessagePart.Text(json.encodeToString(buildJsonObject {
                                        put("error", JsonPrimitive("tool_cancelled_wall_clock"))
                                        put(
                                            "detail",
                                            JsonPrimitive("tool execution exceeded the ${ToolRuntimeLimits.turnBudgetMs / 1000}s turn budget")
                                        )
                                    })))
                                }
                        }
                        // Upstream tool-output truncation: when the workspace shell is
                        // available, oversized text output is spilled to /tool_outputs/
                        // and replaced with a preview + read/grep instructions so the
                        // model can pull the full payload on demand instead of burning
                        // the context window.
                        val hasShellAccess = toolsInternal.any { it.name == "workspace_shell" }
                        executedTools += markedTool.copy(
                            output = maybeTruncateToolOutput(
                                toolCallId = tool.toolCallId,
                                output = result,
                                hasShellAccess = hasShellAccess,
                                maxTokens = assistant.toolResultMaxTokens,
                            )
                        )
                    }.onFailure {
                        // runCatching also captures CancellationException (e.g. the user
                        // pressed stop mid tool execution). That must propagate, not turn
                        // into a tool_failed envelope: a swallowed cancellation leaves a
                        // misleading tool_failed result instead of the clean
                        // cancelled-by-user one from cancelToolByUser, and the loop may
                        // wrongly continue to the next sibling tool. Same pattern as the
                        // catch above this runCatching block.
                        if (it is CancellationException) throw it
                        // Stack trace stays in logcat for debugging; the JSON envelope
                        // sent BACK to the LLM gets just the exception's message and a
                        // short class hint. Stuffing the full multi-frame R8-obfuscated
                        // trace into `error` (the prior behaviour) burned hundreds of
                        // tokens per failure, confused the model, and surfaced
                        // user-visible "java.lang.IllegalStateException at ..." walls
                        // for what was usually a one-line "name is required" problem.
                        Log.w(TAG, "tool ${tool.toolName} threw", it)
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("error", JsonPrimitive("tool_failed"))
                                            put(
                                                "detail",
                                                // Cap at 500 chars so a tool that throws with
                                                // a giant message (e.g. an OkHttp body dump or
                                                // an echoed input arg) doesn't ship 8000+
                                                // tokens back to the LLM on every failure.
                                                JsonPrimitive((it.message ?: it.javaClass.simpleName).take(500)),
                                            )
                                            // Class name as a separate hint so the LLM can
                                            // distinguish validation (IllegalStateException /
                                            // IllegalArgumentException) from runtime issues.
                                            put(
                                                "exception",
                                                JsonPrimitive(it.javaClass.simpleName),
                                            )
                                        }
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }

        return GenerationToolBatchResult(messages, executedTools, waitingForFreshApproval, loopGuardTripCount)
    }

    /**
     * T-05 / (3) — run an idempotent READ-ONLY tool with a bounded number of retries when an
     * attempt fails transiently.
     *
     * Only reached when the assistant opted in via
     * [me.rerere.zeroagent.data.model.Assistant.enableToolExecutionRetry] AND
     * [ToolExecutionRetryPolicy.isIdempotentReadOnly] accepts the call — for `web_fetch` that
     * means its arguments were inspected and the verb is GET/HEAD (T-11). With the flag off the
     * caller's single-attempt path stays byte-identical. Two failure shapes are retried:
     *  - a thrown transient exception (socket timeout / IO / retryable HTTP status), and
     *  - a returned transient error envelope — most local network tools swallow the failure
     *    into `{"error":"timeout"|"network_error"}` instead of throwing, so an exception-only
     *    retry would never fire for the very tools this exists for.
     * Cancellation always propagates, and the wall-clock turn budget still bounds every attempt,
     * so retries can never carry a turn past its budget.
     */
    private suspend fun executeReadOnlyToolWithRetry(
        toolDef: Tool,
        args: kotlinx.serialization.json.JsonElement,
        turnStartMs: Long,
        firstAttemptRemainingMs: Long,
    ): List<UIMessagePart> {
        val outcome = ToolExecutionRetryPolicy.runWithRetries(
            firstAttemptRemainingMs = firstAttemptRemainingMs,
            remainingMsProvider = {
                ToolRuntimeLimits.turnBudgetMs -
                    (android.os.SystemClock.elapsedRealtime() - turnStartMs)
            },
            execute = { budgetMs -> withTimeoutOrNull(budgetMs) { ensureAgentExecutionActive(); toolDef.execute(args) } },
            onRetry = { attemptNumber, failure ->
                Log.w(
                    TAG,
                    "generateText: ${toolDef.name} transient failure, retrying " +
                        "($attemptNumber/${ToolExecutionRetryPolicy.MAX_RETRIES})",
                    failure,
                )
            },
        )
        return when (outcome) {
            is ToolExecutionRetryPolicy.RetryOutcome.Completed -> outcome.output
            is ToolExecutionRetryPolicy.RetryOutcome.Failed -> throw outcome.failure
            is ToolExecutionRetryPolicy.RetryOutcome.TimedOut -> {
                Log.w(TAG, "generateText: ${toolDef.name} cancelled — wall-clock budget exhausted mid-execution")
                listOf(UIMessagePart.Text(json.encodeToString(buildJsonObject {
                    put("error", JsonPrimitive("tool_cancelled_wall_clock"))
                    put(
                        "detail",
                        JsonPrimitive("tool execution exceeded the ${ToolRuntimeLimits.turnBudgetMs / 1000}s turn budget")
                    )
                })))
            }
            is ToolExecutionRetryPolicy.RetryOutcome.BudgetExhausted -> {
                Log.w(TAG, "generateText: ${toolDef.name} skipped — wall-clock budget already exceeded")
                listOf(UIMessagePart.Text(json.encodeToString(buildJsonObject {
                    put("error", JsonPrimitive("tool_cancelled_wall_clock"))
                    put("detail", JsonPrimitive("turn budget exceeded before tool started"))
                })))
            }
        }
    }

    private fun maybeTruncateToolOutput(
        toolCallId: String,
        output: List<UIMessagePart>,
        hasShellAccess: Boolean,
        maxTokens: Int? = null,
    ): List<UIMessagePart> {
        val textParts = output.filterIsInstance<UIMessagePart.Text>()
        val nonTextParts = output.filter { it !is UIMessagePart.Text }
        val totalChars = textParts.sumOf { it.text.length }

        // Phase 17 (⑥): optional per-tool-result token budget. Off by default (null = the
        // char-based gate below, i.e. upstream behaviour untouched). When it IS set it can only
        // ever tighten that gate, never loosen it: output that fits the token budget still has
        // to clear the character gate.
        if (maxTokens != null) {
            val fullText = textParts.joinToString("\n") { it.text }
            val outcome = truncateToolResult(text = fullText, maxTokens = maxTokens)
            if (outcome.truncated) {
                Log.i(
                    TAG,
                    "maybeTruncateToolOutput: tool $toolCallId trimmed to the ${maxTokens}-token budget " +
                        "(${outcome.originalTokens} -> ${outcome.resultTokens} tokens, " +
                        "${outcome.elidedChars} chars elided)"
                )
                val fileName = spillToolOutput(toolCallId, fullText)
                return listOf(
                    UIMessagePart.Text(
                        buildBudgetedTruncationNotice(
                            totalChars = totalChars,
                            fileName = fileName,
                            showShellHints = hasShellAccess,
                            budgetTokens = maxTokens,
                            body = outcome.text,
                        )
                    )
                ) + nonTextParts
            }
        }

        if (totalChars <= MAX_TOOL_OUTPUT_CHARS || !hasShellAccess) return output

        Log.i(TAG, "maybeTruncateToolOutput: truncating tool $toolCallId output ($totalChars chars)")

        val fullText = textParts.joinToString("\n") { it.text }
        val preview = fullText.take(TOOL_OUTPUT_PREVIEW_CHARS)

        val fileName = "${toolCallId}.txt"
        val outputDir = outputDirectory().apply { mkdirs() }
        File(outputDir, fileName).writeText(fullText)

        return listOf(
            UIMessagePart.Text(
                buildString {
                    appendLine("[Tool output truncated: $totalChars characters total]")
                    appendLine("Full output saved to: /tool_outputs/$fileName")
                    appendLine("Use shell to read: `cat /tool_outputs/$fileName`")
                    appendLine("Use shell to search: `grep \"pattern\" /tool_outputs/$fileName`")
                    appendLine()
                    append(preview)
                }
            )
        ) + nonTextParts
    }

    /**
     * Spills the full tool output to `/tool_outputs/<toolCallId>.txt` and returns the file name,
     * or null when the write fails (read-only storage, disk full). Callers degrade to truncating
     * without a spill — losing the tail of a log must never fail the tool call itself.
     */
    private fun spillToolOutput(toolCallId: String, fullText: String): String? = runCatching {
        val fileName = "${toolCallId}.txt"
        val outputDir = outputDirectory().apply { mkdirs() }
        File(outputDir, fileName).writeText(fullText)
        fileName
    }.getOrNull()

    /**
     * Header for a budget-trimmed tool result. The `cat`/`grep` hints are only advertised when a
     * workspace shell is actually on the table this turn — pointing the model at a path it cannot
     * read is worse than saying nothing.
     */
    private fun buildBudgetedTruncationNotice(
        totalChars: Int,
        fileName: String?,
        showShellHints: Boolean,
        budgetTokens: Int,
        body: String,
    ): String = buildString {
        appendLine("[Tool output truncated to fit the ${budgetTokens}-token per-result budget]")
        appendLine("[Original size: $totalChars characters]")
        if (fileName != null) {
            appendLine("Full output saved to: /tool_outputs/$fileName")
            if (showShellHints) {
                appendLine("Use shell to read: `cat /tool_outputs/$fileName`")
                appendLine("Use shell to search: `grep \"pattern\" /tool_outputs/$fileName`")
            }
        } else {
            appendLine("Full output could not be saved to disk; the trimmed text below is all that remains.")
        }
        appendLine()
        append(body)
    }

}
