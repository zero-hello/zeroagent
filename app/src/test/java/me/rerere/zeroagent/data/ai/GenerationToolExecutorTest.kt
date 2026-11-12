package me.rerere.zeroagent.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.ai.tools.ConversationToolApproval
import me.rerere.zeroagent.data.ai.tools.ToolReviewResult
import me.rerere.zeroagent.data.model.Assistant
import org.junit.Assert.*
import org.junit.Test

class GenerationToolExecutorTest {
    private val executor = GenerationToolExecutor(
        outputDirectory = { error("No tool output should be spilled") },
        json = Json,
        platformEffects = object : GenerationPlatformEffects {
            override fun onTurnStart() = Unit
            override fun onTurnComplete(owner: Any) = Unit
            override fun onToolExecution(name: String, owner: Any, onPause: () -> Unit) {
                error("No tool body should run")
            }
        },
    )

    private fun tool(id: String, state: ToolApprovalState) = UIMessagePart.Tool(
        toolCallId = id, toolName = "get_battery_info", input = "{}", approvalState = state,
    )

    private fun gate(autoApprove: suspend (String) -> Boolean = { false }) = ConversationToolApproval(
        conversationId = null,
        needsApproval = { true },
        isToolAutoApproved = autoApprove,
        canReview = { false },
        review = { ToolReviewResult(false, "No reviewer") },
    )

    private suspend fun execute(tools: List<UIMessagePart.Tool>, gate: ConversationToolApproval = gate()): GenerationToolBatchResult =
        executor.execute(
            resolvedTools = tools,
            initialMessages = listOf(UIMessage(role = MessageRole.ASSISTANT, parts = tools)),
            toolsInternal = emptyList(), approval = gate, assistant = Assistant(),
            conversationId = null, turnStartMs = 0, initialLoopGuardTripCount = 2,
            runControl = AgentRunControl(),
            onUpdateMessages = { error("Denied or answered tools must not publish an execution-start marker") },
        )

    @Test
    fun `denied and answered calls retain order and produce results without executing tools`() = runBlocking {
        val calls = listOf(
            tool("denied", ToolApprovalState.Denied("user declined")),
            tool("answered", ToolApprovalState.Answered("user answer")),
        )
        val batch = execute(calls)
        assertEquals(listOf("denied", "answered"), batch.executedTools.map { it.toolCallId })
        assertTrue(batch.executedTools.first().output.toString().contains("user declined"))
        assertEquals(listOf(UIMessagePart.Text("user answer")), batch.executedTools.last().output)
        assertFalse(batch.waitingForFreshApproval)
        assertEquals(2, batch.loopGuardTripCount)
        assertTrue(batch.executedTools.all { it.executionStartedAt == null })
    }

    @Test
    fun `execution recheck can stop a batch and leave its remaining siblings for resume`() = runBlocking {
        val calls = listOf(
            tool("answered", ToolApprovalState.Answered("done")),
            tool("revoked", ToolApprovalState.Auto),
            tool("sibling", ToolApprovalState.Answered("later")),
        )
        val batch = execute(calls)
        assertEquals(listOf("answered", "revoked"), batch.executedTools.map { it.toolCallId })
        assertEquals(ToolApprovalState.Pending, batch.executedTools.last().approvalState)
        assertTrue(batch.waitingForFreshApproval)
        assertTrue(batch.executedTools.last().output.isEmpty())
        assertEquals(calls, batch.messages.single().getTools())
    }

    @Test
    fun `cancellation during execution recheck propagates as the original cancellation`() = runBlocking {
        val cancellation = CancellationException("user stopped")
        var caught: Throwable? = null
        try {
            execute(listOf(tool("cancelled", ToolApprovalState.Auto)), gate { throw cancellation })
        } catch (failure: Throwable) {
            caught = failure
        }
        assertSame(cancellation, caught)
    }
}
