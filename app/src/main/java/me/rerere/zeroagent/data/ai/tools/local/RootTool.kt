package me.rerere.zeroagent.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.root.RootAccessManager
import me.rerere.zeroagent.data.ai.tools.HardlineCommandGuard

fun rootCommandTool(context: Context) = Tool(
    name = "run_root_command",
    description = "Run a command on this rooted phone as superuser. Can read or change protected phone files. Only available after the user enables Root in Settings. Uses the saved verified Root permission without repeated app prompts; no unattended runs. Respect the granted scope and explain destructive operations. Output is limited to 64 KiB; timeout 1–60 seconds.",
    parameters = { InputSchema.Obj(properties = buildJsonObject {
        put("command", buildJsonObject { put("type", "string"); put("description", "The exact Android shell command to execute within the granted scope.") })
        put("timeout_seconds", buildJsonObject { put("type", "integer"); put("description", "Timeout in seconds, 1–60. Default 30.") })
    }, required = listOf("command")) },
    execute = {
        val p = it.jsonObject
        val command = p["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val blocked = HardlineCommandGuard.checkCommand(command)
        val result = if (blocked != null) me.rerere.zeroagent.data.root.RootResult(-1, "Command blocked: $blocked")
            else RootAccessManager(context).run(command, p["timeout_seconds"]?.jsonPrimitive?.intOrNull ?: 30)
        listOf(UIMessagePart.Text(buildJsonObject {
            put("ok", result.exitCode == 0); put("exit_code", result.exitCode); put("output", result.output)
        }.toString()))
    },
)
