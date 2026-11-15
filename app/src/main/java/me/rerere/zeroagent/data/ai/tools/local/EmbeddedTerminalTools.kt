package me.rerere.zeroagent.data.ai.tools.local

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.ai.tools.ToolInvocationContext
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.preferences.TermuxRuntime
import me.rerere.zeroagent.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.java.KoinJavaComponent.getKoin

/** Legacy identity, executed only in the calling assistant's embedded workspace. */
fun embeddedTerminalTool(settings: SettingsStore, invocation: ToolInvocationContext): Tool = Tool(
    name = "termux_run_command",
    description = "Run a command in the calling assistant's embedded Linux workspace. No external Termux app is used. PRoot uses the normal App UID, not Android Root. Use workspace_run_background for persistent jobs and the built-in terminal UI for interactive sessions. External Termux private files and packages require explicit migration.",
    parameters = {
        InputSchema.Obj(properties = buildJsonObject {
            put("command", buildJsonObject { put("type", "string") })
            put("working_dir", buildJsonObject {
                put("type", "string")
                put("description", "A relative path within /workspace, or /workspace itself")
            })
            put("timeout_seconds", buildJsonObject { put("type", "integer") })
        }, required = listOf("command"))
    },
    needsApproval = { true },
    execute = { input ->
        val args = input.jsonObject
        val request = parseEmbeddedTerminalRequest(args, TermuxRuntime.commandTimeoutMs)
        val caller = settings.settingsFlow.first { !it.init }.assistants.firstOrNull { it.id.toString() == invocation.callerAssistantId }
            ?: error("Cannot identify the calling assistant. Reopen the conversation before running commands.")
        val workspaceId = caller.workspaceId?.toString()
            ?: error("Bind a workspace to this assistant and prepare its Linux environment first.")
        val repository = getKoin().get<WorkspaceRepository>()
        require(repository.getById(workspaceId)?.shellStatus == WorkspaceShellStatus.READY.name) {
            "The bound workspace Linux environment is not ready. Open the workspace and prepare it first."
        }
        val result = repository.executeCommand(workspaceId, request.command, request.cwd, request.timeoutMillis)
        listOf(UIMessagePart.Text(buildJsonObject {
            put("exit_code", result.exitCode)
            put("stdout", result.stdout)
            put("stderr", result.stderr)
            put("timed_out", result.timedOut)
            put("truncated", result.truncated)
            put("environment", "embedded_workspace")
            put("privilege", "app_uid_proot")
        }.toString()))
    },
)

internal data class EmbeddedTerminalRequest(val command: String, val cwd: String, val timeoutMillis: Long)

internal fun parseEmbeddedTerminalRequest(args: JsonObject, defaultTimeoutMillis: Long): EmbeddedTerminalRequest {
    val command = args["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
    require(command.isNotBlank()) { "command is required; migrate executable-only legacy calls to command." }
    require(args["interactive"]?.jsonPrimitive?.booleanOrNull != true && args["background"]?.jsonPrimitive?.booleanOrNull != true) {
        "Use the built-in terminal for interactive commands or workspace_run_background for persistent jobs."
    }
    val rawCwd = args["working_dir"]?.jsonPrimitive?.contentOrNull ?: "/workspace"
    require(!rawCwd.startsWith("/") || rawCwd == "/workspace" || rawCwd.startsWith("/workspace/")) {
        "working_dir must be within /workspace. External Termux private paths cannot be reused."
    }
    val cwd = if (rawCwd.startsWith("/")) rawCwd.removePrefix("/workspace").trimStart('/') else rawCwd
    require(cwd.split('/').none { it == ".." }) { "working_dir cannot escape /workspace." }
    val seconds = args["timeout_seconds"]?.jsonPrimitive?.longOrNull
    val timeout = if (seconds == null || seconds == 0L) defaultTimeoutMillis.coerceIn(1000L, 600000L)
        else seconds.coerceIn(1L, 600L) * 1000L
    return EmbeddedTerminalRequest(command, cwd, timeout)
}
