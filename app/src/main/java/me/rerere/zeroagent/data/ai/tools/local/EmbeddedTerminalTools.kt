package me.rerere.zeroagent.data.ai.tools.local

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.ai.tools.ToolInvocationContext
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.preferences.TermuxRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import me.rerere.zeroagent.data.terminal.AndroidShellEnvironment
import me.rerere.workspace.readResult
import java.io.File

/** Historical tool name retained; commands run in Android shell with the App UID. */
fun embeddedTerminalTool(settings: SettingsStore, invocation: ToolInvocationContext): Tool = Tool(
    name = "termux_run_command",
    description = "Run a command using Android /system/bin/sh with normal App UID permissions. The working directory is the same private home as the built-in terminal. No Linux distribution, PRoot, external Termux, apt, pip or Android Root is provided. Use only commands actually installed on this device. Use the built-in terminal for interactive sessions; Android and SELinux access restrictions still apply.",
    parameters = {
        InputSchema.Obj(properties = buildJsonObject {
            put("command", buildJsonObject { put("type", "string") })
            put("working_dir", buildJsonObject {
                put("type", "string")
                put("description", "A relative directory within the terminal home; /workspace is a legacy alias for the home")
            })
            put("timeout_seconds", buildJsonObject { put("type", "integer") })
        }, required = listOf("command"))
    },
    needsApproval = { true },
    execute = { input ->
        val args = input.jsonObject
        val request = parseEmbeddedTerminalRequest(args, TermuxRuntime.commandTimeoutMs)
        require(settings.settingsFlow.first { !it.init }.assistants.any { it.id.toString() == invocation.callerAssistantId }) {
            "Cannot identify the calling assistant. Reopen the conversation before running commands."
        }
        val home = File(AgentWorkspace.rootPath())
        val temporary = File(home, ".tmp").apply { mkdirs() }
        val directory = AndroidShellEnvironment.workingDirectory(home, request.cwd)
        val result = runInterruptible(Dispatchers.IO) {
            val builder = ProcessBuilder(AndroidShellEnvironment.SHELL, "-c", request.command)
                .directory(directory)
                .redirectErrorStream(false)
            builder.environment().putAll(AndroidShellEnvironment.environment(home, temporary))
            builder.start().readResult(request.timeoutMillis)
        }
        listOf(UIMessagePart.Text(buildJsonObject {
            put("exit_code", result.exitCode)
            put("stdout", result.stdout)
            put("stderr", result.stderr)
            put("timed_out", result.timedOut)
            put("truncated", result.truncated)
            put("environment", "android_system_shell")
            put("privilege", "app_uid")
            put("working_directory", directory.absolutePath)
        }.toString()))
    },
)

internal data class EmbeddedTerminalRequest(val command: String, val cwd: String, val timeoutMillis: Long)

internal fun parseEmbeddedTerminalRequest(args: JsonObject, defaultTimeoutMillis: Long): EmbeddedTerminalRequest {
    val command = args["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
    require(command.isNotBlank()) { "command is required; migrate executable-only legacy calls to command." }
    require(args["interactive"]?.jsonPrimitive?.booleanOrNull != true && args["background"]?.jsonPrimitive?.booleanOrNull != true) {
        "Use the built-in terminal for interactive commands. This tool runs bounded foreground commands."
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
