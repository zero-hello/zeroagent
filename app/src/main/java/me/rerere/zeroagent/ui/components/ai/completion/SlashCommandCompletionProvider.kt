package me.rerere.zeroagent.ui.components.ai.completion

import android.content.Context
import androidx.compose.ui.text.TextRange
import me.rerere.zeroagent.R

enum class ChatSlashCommand(val command: String, val description: Int) {
    COMPACT("compact", R.string.za8_cmd_compact), PLAN("plan", R.string.za8_cmd_plan),
    GOAL("goal", R.string.za8_cmd_goal), IMAGE("image", R.string.za8_cmd_image),
    VOICE("voice", R.string.za8_cmd_voice), ACCESS("access", R.string.za8_cmd_access), HELP("help", R.string.za8_cmd_help)
}
internal data class ParsedSlashCommand(val command: ChatSlashCommand, val argument: String)
internal fun parseSlashCommand(text: String): ParsedSlashCommand? {
    if (!text.startsWith("/") || text.contains('\n')) return null
    val name = text.substringBefore(' ').drop(1).lowercase()
    val command = ChatSlashCommand.entries.firstOrNull { it.command == name } ?: return null
    return ParsedSlashCommand(command, text.substringAfter(' ', "").trim())
}
internal fun updateChatDirective(prompt: String?, key: String, text: String?): String? {
    require(key in setOf("plan", "goal"))
    val block = Regex("(?:\\n\\n)?\\[zeroagent:$key]\\n.*?\\n\\[/zeroagent:$key]", RegexOption.DOT_MATCHES_ALL)
    val rest = prompt.orEmpty().replace(block, "")
    return listOf(rest, text?.let { "[zeroagent:$key]\n$it\n[/zeroagent:$key]" }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n").ifBlank { null }
}
class SlashCommandCompletionProvider(private val context: Context) : ChatCompletionProvider {
    override val id = "zeroagent-slash"
    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection || context.cursor != context.text.length || !context.text.startsWith("/") || context.text.any { it.isWhitespace() }) return null
        val query = context.text.drop(1)
        val items = ChatSlashCommand.entries.filter { it.command.startsWith(query, ignoreCase = true) }.map {
            ChatCompletionItem("/" + it.command, "/" + it.command + " ", this.context.getString(it.description), sortScore = 100)
        }
        return ChatCompletionList(id, TextRange(0, context.text.length), items)
    }
}
