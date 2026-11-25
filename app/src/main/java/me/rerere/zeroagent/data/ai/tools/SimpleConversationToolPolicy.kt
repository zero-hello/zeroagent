package me.rerere.zeroagent.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.UIMessagePart

/** Limits automatic execution to local computations and explicitly additive or scoped memory calls. */
internal fun isSimpleConversationTool(tool: UIMessagePart.Tool): Boolean {
    if (ToolApprovalDefaults.isHardFloor(tool.toolName)) return false
    val args = runCatching { Json.parseToJsonElement(tool.input) as? JsonObject }.getOrNull() ?: return false
    if (tool.toolName in PersonalUtilityTools.names) return true
    fun text(key: String): String? = (args[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun hasShortContent(): Boolean = text("content")?.let { it.isNotBlank() && it.length <= 4_096 } == true
    return when (tool.toolName) {
        "memory_index", "memory_read", "tool_search", "tool_open" -> true
        "memory_tool" -> args.keys == setOf("action", "content") && text("action") == "create" && hasShortContent()
        "memory_write" -> args.keys.all { it in setOf("file", "content", "mode") } &&
            ("mode" !in args || text("mode") == "append") && hasShortContent() &&
            text("file")?.let { file ->
                ColdMemoryRules.isValidWriteName(file) && file.trim().let { name ->
                    !name.startsWith('.') && !name.startsWith("app-", ignoreCase = true) &&
                        !name.equals("AGENTS.md", ignoreCase = true) && !name.equals("CLAUDE.md", ignoreCase = true)
                }
            } == true
        else -> false
    }
}
