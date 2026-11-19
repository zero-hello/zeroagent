package me.rerere.zeroagent.data.ai.tools

/** Live approval policy. Root requires the saved, verified Root grant; headless execution
 * and ask_user retain their own rules. The command hardline is enforced by GenerationLoop. */
object ToolApprovalPolicy {

    /** The one local tool that is a request for input rather than a capability. */
    const val ASK_USER_TOOL_NAME: String = "ask_user"

    /**
     * True when [toolName] may run without prompting.
     *
     * Pure: every input is a parameter, so callers own the (async) lookups and the function
     * itself never touches Android, DataStore or globals beyond the constant floor.
     */
    fun shouldAutoApprove(
        toolName: String,
        blacklist: Set<String>,
        yolo: Boolean,
        headless: Boolean,
        allowedForChat: Boolean,
        hardFloor: Set<String> = ToolApprovalDefaults.NO_ALWAYS_ALLOW,
        isMcpRelayed: Boolean = toolName.startsWith(ToolApprovalDefaults.MCP_TOOL_PREFIX),
        accessMode: ConversationAccessMode? = null,
        rootAccessGranted: Boolean = false,
    ): Boolean {
        if (toolName == "run_root_command") return rootAccessGranted && !headless
        if (toolName == "termux_run_command" && rootAccessGranted) return !headless
        if (toolName == ASK_USER_TOOL_NAME) return headless
        if (!headless && accessMode != null) return when (accessMode) {
            ConversationAccessMode.ASK -> allowedForChat && !isMcpRelayed && toolName !in hardFloor
            // A tool name, YOLO toggle, or previous grant is never a review verdict.
            // The independent, argument-aware reviewer runs in the generation loop.
            ConversationAccessMode.ASSISTED -> false
            ConversationAccessMode.FULL -> true
        }
        if (yolo) return true
        if (headless) return true
        if (isMcpRelayed || toolName in hardFloor) return false
        if (allowedForChat) return true
        return toolName !in blacklist
    }
}
