package me.rerere.zeroagent.data.chat

enum class ChatSlashCommand(val command: String) {
    COMPACT("compact"),
    PLAN("plan"),
    GOAL("goal"),
    IMAGE("image"),
    VOICE("voice"),
    ACCESS("access"),
    HELP("help"),
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
