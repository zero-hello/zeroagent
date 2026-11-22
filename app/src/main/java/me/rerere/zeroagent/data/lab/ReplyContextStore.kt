package me.rerere.zeroagent.data.lab

import me.rerere.ai.core.MessageRole
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

internal data class ReplyContextEntry(val key: String, val title: String, val text: String)
internal data class ReplyContextSnapshot(val conversationId: Uuid, val entries: List<ReplyContextEntry>)

/** Kept only while the app process runs, with a bound on recent replies and stored characters. */
internal object ReplyContextStore {
    private val replies = LinkedHashMap<Uuid, ReplyContextSnapshot>()

    @Synchronized
    fun record(conversationId: Uuid, replyId: Uuid, entries: List<ReplyContextEntry>) {
        val previous = replies.remove(replyId)?.entries.orEmpty()
        replies[replyId] = ReplyContextSnapshot(conversationId, (previous + entries)
            .distinctBy { it.key to it.text }
            .takeLast(40)
            .map { it.copy(text = it.text.take(8_000)) })
        while (replies.size > 24) replies.remove(replies.keys.first())
    }

    @Synchronized
    fun get(replyId: Uuid): ReplyContextSnapshot? = replies[replyId]

    @Synchronized
    fun clear() = replies.clear()

}

internal const val DOCUMENT_CONTEXT_KEY = "zeroagent_document_context_key"
internal const val DOCUMENT_CONTEXT_NAME = "zeroagent_document_context_name"

internal fun replyAttachmentKey(message: UIMessage, part: UIMessagePart): String? = when (part) {
    is UIMessagePart.Document -> "attachment:${message.id}:${part.url.hashCode()}"
    is UIMessagePart.Image -> "attachment:${message.id}:${part.url.hashCode()}"
    else -> null
}

/** Select categories from the transformed messages at the provider handoff. */
internal fun collectReplyContext(
    request: List<UIMessage>,
    candidates: List<ReplyContextEntry>,
    summaryId: Uuid?,
): List<ReplyContextEntry> = buildList {
    val systemTexts = request.filter { it.role == MessageRole.SYSTEM && it.isSynthetic }
        .flatMap { it.parts.filterIsInstance<UIMessagePart.Text>() }
        .map { it.text }
    candidates.forEach { entry ->
        if (entry.text.isBlank()) return@forEach
        val inSystem = if (entry.key.startsWith("memory:")) {
            val id = entry.key.removePrefix("memory:").toIntOrNull() ?: return@forEach
            val encodedContent = Regex.escape(JsonPrimitive(entry.text).toString())
            val memoryObject = Regex("""\{\s*"id"\s*:\s*$id\s*,\s*"content"\s*:\s*$encodedContent\s*\}""")
            systemTexts.any { memoryObject.containsMatchIn(it) }
        } else {
            systemTexts.any { entry.text in it }
        }
        if (inSystem) add(entry)
    }
    request.firstOrNull { it.id == summaryId }?.let { summary ->
        val text = summary.toText()
        if (text.isNotBlank()) add(ReplyContextEntry("summary", "對話摘要", text))
    }
    request.forEach { message ->
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    val key = (part.metadata?.get(DOCUMENT_CONTEXT_KEY) as? JsonPrimitive)?.content
                    val name = (part.metadata?.get(DOCUMENT_CONTEXT_NAME) as? JsonPrimitive)?.content
                    if (key != null && name != null) add(ReplyContextEntry(key, "附件節錄 · $name", part.text))
                }
                is UIMessagePart.Image -> replyAttachmentKey(message, part)?.let { key ->
                    add(ReplyContextEntry(key, "圖片附件", "此回覆的模型輸入包含圖片。"))
                }
                else -> Unit
            }
        }
    }
}
