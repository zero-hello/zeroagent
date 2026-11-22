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
fun embeddedTerminalTool(settings: SettingsStore, invocation: ToolInvocationContext, root: me.rerere.zeroagent.data.root.RootAccessManager): Tool = Tool(
    name = "termux_run_command",
    description = "Run a command using Android /system/bin/sh with the saved permission mode (App UID, or Root only when enabled and granted). The working directory is the same private home as the built-in terminal. No Linux distribution, PRoot, external Termux, apt or pip is provided. Root uses the same saved grant as the interactive terminal; Android and the external superuser manager still control availability. Use only commands actually installed on this device. Use the built-in terminal for interactive sessions; Android and SELinux access restrictions still apply.",
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
        AndroidShellEnvironment.prepare(home)
        val temporary = File(home, ".tmp").apply { mkdirs() }
        val directory = AndroidShellEnvironment.workingDirectory(home, request.cwd)
        val useRoot = root.enabled
        if (useRoot) {
            check(!invocation.isHeadless) { "Root 指令不允許在背景無人值守流程執行。" }
            check(root.ensureAvailable()) { "Root 授權已失效，請到設定重新檢查。" }
            val result = root.run(AndroidShellEnvironment.command(home, directory, request.command),
                (request.timeoutMillis / 1000).toInt().coerceIn(1, 60))
            return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("exit_code", result.exitCode); put("output", result.output)
                put("environment", "android_system_shell"); put("privilege", "root")
                put("working_directory", directory.absolutePath)
            }.toString()))
        }
        val result = runInterruptible(Dispatchers.IO) {
            val builder = ProcessBuilder(AndroidShellEnvironment.SHELL, "-c", AndroidShellEnvironment.command(home, directory, request.command))
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
