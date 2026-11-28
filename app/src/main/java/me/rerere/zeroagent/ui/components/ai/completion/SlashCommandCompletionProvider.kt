package me.rerere.zeroagent.ui.components.ai.completion

import android.content.Context
import androidx.compose.ui.text.TextRange
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.chat.ChatSlashCommand

internal val ChatSlashCommand.description: Int
    get() = when (this) {
        ChatSlashCommand.COMPACT -> R.string.zeroagent8_cmd_compact
        ChatSlashCommand.PLAN -> R.string.zeroagent8_cmd_plan
        ChatSlashCommand.GOAL -> R.string.zeroagent8_cmd_goal
        ChatSlashCommand.IMAGE -> R.string.zeroagent8_cmd_image
        ChatSlashCommand.VOICE -> R.string.zeroagent8_cmd_voice
        ChatSlashCommand.ACCESS -> R.string.zeroagent8_cmd_access
        ChatSlashCommand.HELP -> R.string.zeroagent8_cmd_help
    }

class SlashCommandCompletionProvider(private val context: Context) : ChatCompletionProvider {
    override val id = "zeroagent-slash"
    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        val range = slashCompletionRange(context) ?: return null
        val query = context.text.substring(range.start + 1, context.cursor)
        val items = ChatSlashCommand.entries.filter { it.command.startsWith(query, ignoreCase = true) }.map {
            ChatCompletionItem("/" + it.command, "/" + it.command + " ", this.context.getString(it.description), sortScore = 100)
        }
        return ChatCompletionList(id, range, items)
    }
}

internal fun slashCompletionRange(context: ChatCompletionContext): TextRange? {
    if (context.hasSelection || context.cursor !in 1..context.text.length) return null
    val text = context.text
    var start = context.cursor
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    if (start == context.cursor || text[start] != '/') return null
    var end = context.cursor
    while (end < text.length && !text[end].isWhitespace()) end++
    if ((start + 1 until end).any { text[it] !in 'a'..'z' && text[it] !in 'A'..'Z' }) return null
    return TextRange(start, end)
}