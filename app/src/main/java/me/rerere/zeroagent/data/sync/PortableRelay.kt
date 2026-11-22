package me.rerere.zeroagent.data.sync

import kotlinx.serialization.Serializable
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.model.ConversationCompaction
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
internal data class PortableRelay(
    val summary: String,
    val sourceEndNodeId: String,
    val tailStartNodeId: String?,
    val summaryModelId: String,
    val isAuto: Boolean,
    val sourceTokenEstimate: Int,
    val createdAtMs: Long,
) {
    fun validateAgainst(conversation: Conversation) {
        val nodes = conversation.messageNodes
        val source = nodes.indexOfFirst { it.id.toString() == sourceEndNodeId }
        val next = nodes.getOrNull(source + 1)?.id?.toString()
        require(source >= 0 && (tailStartNodeId == null || tailStartNodeId == next) &&
            summary.isNotBlank() && summary.length <= 100_000 && sourceTokenEstimate >= 0 &&
            runCatching { Uuid.parse(summaryModelId) }.isSuccess) {
            "接力摘要的來源訊息或內容不符。"
        }
    }

    fun toCompaction(conversationId: Uuid): ConversationCompaction = ConversationCompaction(
        conversationId = conversationId,
        summary = summary,
        tailStartNodeId = tailStartNodeId?.let(Uuid::parse),
        sourceEndNodeId = Uuid.parse(sourceEndNodeId),
        summaryModelId = Uuid.parse(summaryModelId),
        isAuto = isAuto,
        sourceTokenEstimate = sourceTokenEstimate,
        createdAt = Instant.ofEpochMilli(createdAtMs),
    )

    companion object {
        fun from(compaction: ConversationCompaction, conversation: Conversation): PortableRelay {
            val nodes = conversation.messageNodes
            val source = nodes.indexOfFirst { it.id == compaction.sourceEndNodeId }
            return PortableRelay(
                summary = compaction.summary,
                sourceEndNodeId = compaction.sourceEndNodeId.toString(),
                tailStartNodeId = nodes.getOrNull(source + 1)?.id?.toString(),
                summaryModelId = compaction.summaryModelId.toString(),
                isAuto = compaction.isAuto,
                sourceTokenEstimate = compaction.sourceTokenEstimate,
                createdAtMs = compaction.createdAt.toEpochMilli(),
            ).also { it.validateAgainst(conversation) }
        }
    }
}

/** IDs in an archive cannot take ownership of rows or search results from another local chat. */
internal fun restorePortableConversation(
    conversation: Conversation,
    targetId: Uuid,
    relay: PortableRelay?,
): Pair<Conversation, ConversationCompaction?> {
    relay?.validateAgainst(conversation)
    val nodeIds = conversation.messageNodes.associate { it.id to Uuid.random() }
    require(nodeIds.size == conversation.messageNodes.size) { "備份含重複的訊息節點。" }
    val restoredNodes = conversation.messageNodes.map { node ->
        node.copy(id = nodeIds.getValue(node.id), messages = node.messages.map { it.copy(id = Uuid.random()) })
    }
    val restoredRelay = relay?.toCompaction(targetId)?.let { compaction ->
        compaction.copy(
            sourceEndNodeId = nodeIds.getValue(compaction.sourceEndNodeId),
            tailStartNodeId = compaction.tailStartNodeId?.let(nodeIds::getValue),
        )
    }
    return conversation.copy(id = targetId, messageNodes = restoredNodes) to restoredRelay
}
