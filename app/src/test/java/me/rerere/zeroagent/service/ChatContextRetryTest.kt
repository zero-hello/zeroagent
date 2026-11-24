package me.rerere.zeroagent.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextRetryTest {
    private val overflow = IllegalStateException("Request failed", IllegalArgumentException("context_length_exceeded"))

    @Test
    fun `nested provider overflow permits one retry from user turn`() {
        assertTrue(shouldRetryContextLimit(true, false, true, overflow, UIMessage(role = MessageRole.USER, parts = emptyList())))
        assertFalse(shouldRetryContextLimit(false, false, true, overflow, null))
        assertFalse(shouldRetryContextLimit(true, true, true, overflow, null))
        assertFalse(shouldRetryContextLimit(true, false, false, overflow, null))
        assertFalse(shouldRetryContextLimit(true, false, true, IllegalStateException("Network unavailable"), null))
    }

    @Test
    fun `partial text is preserved and completed tool rounds can continue`() {
        val partial = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("Already streamed")))
        assertFalse(shouldRetryContextLimit(true, false, true, overflow, partial))
        val pendingTool = UIMessagePart.Tool(toolCallId = "test-tool", toolName = "read", input = "{}")
        assertFalse(shouldRetryContextLimit(true, false, true, overflow, partial.copy(parts = listOf(pendingTool))))
        val tool = pendingTool.copy(output = listOf(UIMessagePart.Text("Test result")))
        assertTrue(shouldRetryContextLimit(true, false, true, overflow, partial.copy(parts = listOf(tool))))
    }
}
