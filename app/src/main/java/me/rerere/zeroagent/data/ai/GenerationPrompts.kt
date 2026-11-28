package me.rerere.zeroagent.data.ai

import java.util.Locale
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.AssistantMemory
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.utils.JsonInstantPretty
import me.rerere.zeroagent.utils.toLocalDate

/** Bounded local recall. Recent memories fill a small baseline and relevant matches take priority. */
internal fun selectPromptMemories(memories: List<AssistantMemory>, query: String): List<AssistantMemory> {
    val terms = memorySearchTerms(query.take(4_000))
    val scores = memories.map { memory ->
        val termsInMemory = memorySearchTerms(memory.content)
        memory to terms.count(termsInMemory::contains)
    }
    val matching = scores.filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<AssistantMemory, Int>> { it.second }.thenByDescending { it.first.id })
        .take(16).map { it.first }
    return (matching + memories.sortedByDescending { it.id }.take(8)).distinctBy { it.id }
}

private val memoryWords = Regex("[\\p{L}\\p{N}]+")
private val continuousScript = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]+")

private fun memorySearchTerms(text: String): Set<String> = buildSet {
    val lower = text.lowercase(Locale.ROOT)
    memoryWords.findAll(lower).forEach { token ->
        if (token.value.length >= 3) add(token.value)
    }
    continuousScript.findAll(lower).forEach { token -> addAll(token.value.windowed(2)) }
}
internal fun buildMemoryPrompt(memories: List<AssistantMemory>) =
    buildString {
        appendLine()
        append("**Memories**")
        appendLine()
        append("These are memories stored via the memory_tool that you can reference in future conversations.")
        appendLine()
        val json = buildJsonArray {
            memories.forEach { memory ->
                add(buildJsonObject {
                    put("id", memory.id)
                    put("content", memory.content)
                })
            }
        }
        append(JsonInstantPretty.encodeToString(json))
        appendLine()
    }

internal suspend fun buildRecentChatsPrompt(
    assistant: Assistant,
    conversationRepo: ConversationRepository
): String {
    val recentConversations = conversationRepo.getRecentConversations(
        assistantId = assistant.id,
        limit = 10,
    )
    if (recentConversations.isNotEmpty()) {
        return buildString {
            appendLine()
            append("**Recent Chats**")
            appendLine()
            append("These are some of the user's recent conversations. You can use them to understand user preferences:")
            appendLine()
            val json = buildJsonArray {
                recentConversations.forEach { conversation ->
                    add(buildJsonObject {
                        put("title", conversation.title)
                        put("last_chat", conversation.updateAt.toLocalDate())
                    })
                }
            }
            append(JsonInstantPretty.encodeToString(json))
            appendLine()
        }
    }
    return ""
}
