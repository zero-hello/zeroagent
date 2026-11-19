package me.rerere.zeroagent.data.ai.tools

import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

internal class ConversationToolApproval(
    private val conversationId: Uuid?,
    private val needsApproval: (UIMessagePart.Tool) -> Boolean,
    private val isToolAutoApproved: suspend (String) -> Boolean,
    private val canReview: suspend (String) -> Boolean,
    private val review: suspend (UIMessagePart.Tool) -> ToolReviewResult,
) {
    private data class Receipt(val name: String, val input: String, val access: ConversationAccessSnapshot)
    private val receipts = mutableMapOf<String, Receipt>()

    suspend fun resolve(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        if (tool.approvalState != ToolApprovalState.Auto || tool.isExecuted) return tool
        HardlineCommandGuard.checkTool(tool.toolName, tool.input)?.let { reason ->
            return tool.copy(approvalState = ToolApprovalState.Denied("blocked by safety floor (hardline): $reason"))
        }
        val access = conversationId?.let(ConversationAccess::snapshot)
        val rootApproved = tool.toolName in setOf("run_root_command", "termux_run_command") && isToolAutoApproved(tool.toolName)
        val assisted = access?.mode == ConversationAccessMode.ASSISTED &&
            !HeadlessConversations.isHeadless(conversationId!!) && !rootApproved
        if (assisted && tool.toolName != ToolApprovalPolicy.ASK_USER_TOOL_NAME) {
            if (ToolApprovalDefaults.isHardFloor(tool.toolName) || !canReview(tool.toolName)) {
                return withReview(tool, ToolReviewResult(false, ""), "manual")
            }
            val verdict = review(tool)
            // A completed network request cannot override a revocation made while waiting.
            if (!canReview(tool.toolName) || ConversationAccess.snapshot(conversationId!!) != access) {
                return withReview(tool, ToolReviewResult(false, ""), "changed")
            }
            if (verdict.allowed) receipts[tool.toolCallId] = Receipt(tool.toolName, tool.input, access!!)
            return withReview(tool, verdict)
        }
        if (needsApproval(tool) || ConversationAccess.requiresApproval(conversationId, tool.toolName)) {
            val allowed = if (tool.toolName == "run_root_command") rootApproved else isToolAutoApproved(tool.toolName)
            if (conversationId != null && ConversationAccess.snapshot(conversationId) != access) {
                return withReview(tool, ToolReviewResult(false, ""), "changed")
            }
            if (!allowed) return if (TOOL_REVIEW_METADATA in tool.metadata.orEmpty()) {
                withReview(tool, ToolReviewResult(false, ""), "changed")
            } else tool.copy(approvalState = ToolApprovalState.Pending)
        }
        // An unexecuted call may have a persisted review from an earlier mode/session.
        // Once the user chooses another mode, don't display that as a fresh approval.
        return if (TOOL_REVIEW_METADATA in tool.metadata.orEmpty()) {
            tool.copy(metadata = JsonObject(tool.metadata.orEmpty() - TOOL_REVIEW_METADATA))
        } else tool
    }

    suspend fun recheck(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        if (tool.approvalState != ToolApprovalState.Auto || tool.isExecuted) return tool
        val receipt = receipts[tool.toolCallId]
        // Receipts are transient, exact-call evidence, never inferred from stored metadata.
        if (conversationId != null && receipt != null && receipt.name == tool.toolName &&
            receipt.input == tool.input && canReview(tool.toolName) &&
            receipt.access == ConversationAccess.snapshot(conversationId)
        ) return tool
        return resolve(tool)
    }

    private fun withReview(tool: UIMessagePart.Tool, result: ToolReviewResult, status: String? = null): UIMessagePart.Tool = tool.copy(
        approvalState = if (result.allowed) ToolApprovalState.Auto else ToolApprovalState.Pending,
        metadata = JsonObject(tool.metadata.orEmpty() + (TOOL_REVIEW_METADATA to buildJsonObject {
            put("status", status ?: if (result.allowed) "allow" else if (result.reason.isBlank()) "unavailable" else "ask")
            put("reason", result.reason)
        })),
    )
}

internal const val TOOL_REVIEW_METADATA = "zeroagent.approval_review"
