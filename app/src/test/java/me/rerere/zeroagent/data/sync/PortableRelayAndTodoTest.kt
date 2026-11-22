package me.rerere.zeroagent.data.sync

import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.lab.LocalTodo
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.model.ConversationCompaction
import me.rerere.zeroagent.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class PortableRelayAndTodoTest {
    @Test
    fun mergeRetainsLocalTodoStateAndRestoresNewTodos() {
        val local = LocalTodo(Uuid.random().toString(), "本機待辦", true)
        val imported = LocalTodo(Uuid.random().toString(), "匯入待辦")
        val archived = PortableLabData(confirmedTodos = listOf(local.copy(checked = false), imported))
        val restored = Json.decodeFromString<PortableLabData>(Json.encodeToString(archived))
        restored.validate(emptySet())

        assertEquals(listOf(local, imported), mergePortableTodos(listOf(local), restored.confirmedTodos.orEmpty()))
        assertEquals(restored.confirmedTodos, mergePortableTodos(emptyList(), restored.confirmedTodos.orEmpty()))
        assertThrows(IllegalArgumentException::class.java) {
            PortableLabData(confirmedTodos = listOf(local, local)).validate(emptySet())
        }
    }

    @Test
    fun relayRemapsToImportedConversationAndMessageNodes() {
        val first = node("整理過的消息")
        val next = node("保留在後面的消息")
        val original = Conversation.ofId(Uuid.random(), messages = listOf(first, next))
        val compaction = ConversationCompaction(
            conversationId = original.id,
            summary = "已完成前一階段。",
            tailStartNodeId = next.id,
            sourceEndNodeId = first.id,
            summaryModelId = Uuid.random(),
            isAuto = false,
            sourceTokenEstimate = 400,
            createdAt = Instant.ofEpochMilli(1234),
        )
        val relays = mapOf(original.id.toString() to PortableRelay.from(compaction, original))
        val archive = Json.decodeFromString<PortableLabData>(Json.encodeToString(PortableLabData(relays = relays)))
        archive.validate(setOf(original.id.toString()))
        assertThrows(IllegalArgumentException::class.java) { archive.validate(emptySet()) }

        val target = Uuid.random()
        val (restored, relay) = restorePortableConversation(original, target, archive.relays?.getValue(original.id.toString()))
        requireNotNull(relay)
        assertEquals(target, restored.id)
        assertNotEquals(first.id, restored.messageNodes.first().id)
        assertNotEquals(first.currentMessage.id, restored.messageNodes.first().currentMessage.id)
        assertEquals(target, relay.conversationId)
        assertEquals(restored.messageNodes.first().id, relay.sourceEndNodeId)
        assertEquals(restored.messageNodes.last().id, relay.tailStartNodeId)
        assertEquals(compaction.summary, relay.summary)
    }

    @Test
    fun invalidRelayAnchorCannotAttachToDifferentHistory() {
        val first = node("第一則")
        val other = node("不相鄰的消息")
        val conversation = Conversation.ofId(Uuid.random(), messages = listOf(first, node("第二則"), other))
        val wrong = PortableRelay("摘要", first.id.toString(), other.id.toString(), Uuid.random().toString(), false, 500, 1234)
        assertThrows(IllegalArgumentException::class.java) { wrong.validateAgainst(conversation) }
        assertThrows(IllegalArgumentException::class.java) {
            restorePortableConversation(conversation.copy(messageNodes = listOf(first, first)), Uuid.random(), null)
        }
    }

    private fun node(text: String) = MessageNode(messages = listOf(UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(text)))))
}
