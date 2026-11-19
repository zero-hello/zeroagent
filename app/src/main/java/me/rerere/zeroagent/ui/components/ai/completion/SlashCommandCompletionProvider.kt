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
        if (context.hasSelection || context.cursor != context.text.length || !context.text.startsWith("/") || context.text.any { it.isWhitespace() }) return null
        val query = context.text.drop(1)
        val items = ChatSlashCommand.entries.filter { it.command.startsWith(query, ignoreCase = true) }.map {
            ChatCompletionItem("/" + it.command, "/" + it.command + " ", this.context.getString(it.description), sortScore = 100)
        }
        return ChatCompletionList(id, TextRange(0, context.text.length), items)
    }
}
