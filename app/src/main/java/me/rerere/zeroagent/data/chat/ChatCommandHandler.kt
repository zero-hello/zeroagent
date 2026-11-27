package me.rerere.zeroagent.data.chat

/** Presentation effects are delivered synchronously, before the input is cleared. */
internal enum class ChatCommandEffect {
    COMPACT, IMAGE, VOICE, ACCESS, DELEGATE_ACCESS, HELP, DIRECTIVE_SAVED, DIRECTIVE_REMOVED, GOAL_USAGE,
}

/** Executes command policy without Compose, Android services, or a ViewModel. */
internal class ChatCommandHandler(
    private val currentPrompt: () -> String?,
    private val resetAccessToAsk: () -> Unit,
    private val savePrompt: (String?) -> Unit,
    private val clearInput: () -> Unit,
) {
    fun handle(draft: String, onEffect: (ChatCommandEffect) -> Unit): Boolean {
        if (isDirectToolDelegation(draft)) {
            onEffect(ChatCommandEffect.DELEGATE_ACCESS)
            clearInput()
            return true
        }
        val parsed = parseSlashCommand(draft) ?: return false
        when (parsed.command) {
            ChatSlashCommand.COMPACT -> onEffect(ChatCommandEffect.COMPACT)
            ChatSlashCommand.IMAGE -> onEffect(ChatCommandEffect.IMAGE)
            ChatSlashCommand.VOICE -> onEffect(ChatCommandEffect.VOICE)
            ChatSlashCommand.ACCESS -> onEffect(ChatCommandEffect.ACCESS)
            ChatSlashCommand.HELP -> onEffect(ChatCommandEffect.HELP)
            ChatSlashCommand.PLAN, ChatSlashCommand.GOAL -> {
                val key = parsed.command.command
                val off = parsed.argument.equals("off", true)
                val content = when {
                    off -> null
                    parsed.command == ChatSlashCommand.PLAN ->
                        "Planning mode: provide a clear plan, inspect information as needed, and ask before making changes. " + parsed.argument
                    parsed.argument.isNotBlank() ->
                        "Current user goal: " + parsed.argument + ". Work toward it, report real progress, and never claim completion without evidence."
                    else -> {
                        onEffect(ChatCommandEffect.GOAL_USAGE)
                        return true
                    }
                }
                if (parsed.command == ChatSlashCommand.PLAN && !off) resetAccessToAsk()
                savePrompt(updateChatDirective(currentPrompt(), key, content))
                onEffect(if (off) ChatCommandEffect.DIRECTIVE_REMOVED else ChatCommandEffect.DIRECTIVE_SAVED)
            }
        }
        clearInput()
        return true
    }
}

/** Only the whole, newly typed message counts. Quoted instructions and attached data do not. */
internal fun isDirectToolDelegation(draft: String): Boolean =
    draft.trim().trimEnd('。', '！', '.', '!').lowercase() in setOf(
        "全程代我授權", "請全程代我授權", "全程替我授權", "請全程替我授權",
        "全程代我授权", "请全程代我授权", "全程替我授权", "请全程替我授权",
        "approve tools for me throughout this chat", "please approve tools for me throughout this chat",
    )
