package me.rerere.zeroagent.data.lab

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.ai.buildMemoryPrompt
import me.rerere.zeroagent.data.ai.continuationRequestMessages
import me.rerere.zeroagent.data.model.AssistantMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyContextHandoffTest {
    @Test
    fun `replaced or stripped context does not appear in the reply receipt`() {
        val replacedProject = ReplyContextEntry("project", "Project", "Original project instructions")
        val keptMemory = AssistantMemory(12, "Keep the \"quoted\" text.\nAnd this line.")
        val removedMemory = ReplyContextEntry("memory:1", "Memory", keptMemory.content)
        val memory = ReplyContextEntry("memory:12", "Memory", keptMemory.content)
        val request = listOf(message(MessageRole.SYSTEM, "Custom prompt" + buildMemoryPrompt(listOf(keptMemory)), synthetic = true))

        assertEquals(listOf(memory), collectReplyContext(request, listOf(replacedProject, removedMemory, memory), null))
    }

    @Test
    fun `reconnect only attributes context present in its own input`() {
        val summary = message(MessageRole.USER, "Old conversation summary")
        val project = ReplyContextEntry("project", "Project", "Scoped background")
        val firstRequest = listOf(message(MessageRole.SYSTEM, project.text, synthetic = true), summary)
        val reconnect = continuationRequestMessages(firstRequest.dropLast(1), listOf(message(MessageRole.ASSISTANT, "The partial reply")))

        assertEquals(listOf("project", "summary"), collectReplyContext(firstRequest, listOf(project), summary.id).map { it.key })
        assertEquals(listOf(project), collectReplyContext(reconnect, listOf(project), summary.id))
    }

    private fun message(role: MessageRole, text: String, synthetic: Boolean = false): UIMessage =
        UIMessage(role = role, parts = listOf(UIMessagePart.Text(text)), isSynthetic = synthetic)
}
