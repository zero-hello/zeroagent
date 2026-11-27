package me.rerere.zeroagent.data.ai.tools

import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.zeroagent.data.ai.mcp.McpTool
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.subagent.SubAgentSurface
import kotlin.uuid.Uuid

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean =
    assistant.enableWebSearch && BuiltInTools.Search !in model.tools

class InvalidMcpServerNamesException(val names: List<String>) :
    IllegalStateException("Invalid MCP server names: ${names.joinToString(", ")}")

/** Owns assembly only; conversation state, activation lifetime and compaction stay with the caller. */
class ChatToolFactory(private val sources: ChatToolSources) {
    suspend fun validateMcpServerNames(): List<Triple<Uuid, String, McpTool>> =
        sources.mcpTools(null).also(::validateNames)

    suspend fun createForGeneration(
        context: ChatToolContext,
        useExternalWebSearch: Boolean,
        activation: () -> ToolActivationState,
        onCompact: suspend (String?) -> CompactionToolResult,
    ): List<Tool> = createGenerationSurface(context, useExternalWebSearch, activation, onCompact = onCompact)()

    internal suspend fun createGenerationSurface(
        context: ChatToolContext,
        useExternalWebSearch: Boolean,
        activation: () -> ToolActivationState,
        pendingTools: List<String> = emptyList(),
        onCompact: suspend (String?) -> CompactionToolResult,
    ): () -> List<Tool> = assemble(context, GenerationOptions(useExternalWebSearch, activation, pendingTools, onCompact))

    suspend fun createForRerun(context: ChatToolContext): List<Tool> = assemble(context, null)()

    private data class GenerationOptions(
        val externalSearch: Boolean,
        val activation: () -> ToolActivationState,
        val pendingTools: List<String>,
        val onCompact: suspend (String?) -> CompactionToolResult,
    )

    private suspend fun assemble(context: ChatToolContext, generation: GenerationOptions?): () -> List<Tool> {
        val surfaceAssistant = SubAgentSurface.assistantFor(context.conversationId) ?: context.assistant
        val publicData = if (HeadlessConversations.isHeadless(context.conversationId)) emptyList() else sources.publicDataTools()
        var activatedTools: () -> List<Tool> = { emptyList() }
        val tools = buildList {
            // Generation uses the caller's model-aware search decision; rerun historically does not.
            if (generation?.externalSearch ?: surfaceAssistant.enableWebSearch) addAll(sources.searchTools(context.settings))
            addAll(sources.localTools(context.copy(assistant = surfaceAssistant)))
            addAll(sources.workspaceTools(surfaceAssistant, context.workspaceCwd))
            addAll(sources.coldMemoryTools(surfaceAssistant))
            if (surfaceAssistant.enabledSkills.isNotEmpty()) addAll(sources.skillTools(surfaceAssistant))
            if (generation != null && surfaceAssistant.enableCompactContextTool) {
                addAll(buildCompactionTools(generation.onCompact))
            }
            val remote = sources.mcpTools(if (surfaceAssistant === context.assistant) null else surfaceAssistant)
            // Rerun intentionally has no generation-only name validation or catalog indirection.
            if (generation != null) validateNames(remote)
            if (generation != null) {
                val deferredMcp = if (surfaceAssistant.toolSurfaceMode == ToolSurfaceMode.PROGRESSIVE_CATALOG) remote else emptyList()
                if (deferredMcp.isEmpty()) remote.forEach { (id, name, tool) -> add(buildMcpTool(id, name, tool)) }
                val catalog = ToolCatalog(PersonalUtilityTools.entries + publicData + deferredMcp.map { (id, name, tool) ->
                    ToolCatalogEntry(
                        name = me.rerere.zeroagent.data.ai.mcp.buildMcpToolName(id, name, tool.name),
                        summary = mcpCatalogSummary(tool),
                        source = ToolCatalogSource.MCP,
                        tool = buildMcpTool(id, name, tool),
                    )
                })
                val activation = generation.activation()
                activation.retain(catalog.entries.mapTo(mutableSetOf()) { it.name })
                activation.restorePending(catalog, generation.pendingTools)
                addAll(buildToolCatalogTools(catalog, activation, replaceOldest = true))
                activatedTools = { activation.active().mapNotNull { name -> catalog.entry(name)?.tool } }
            } else {
                addAll((PersonalUtilityTools.entries + publicData).map { it.tool })
                remote.forEach { (id, name, tool) -> add(buildMcpTool(id, name, tool)) }
            }
        }
        return { SubAgentSurface.apply(context.conversationId, tools + activatedTools()) }
    }

    private fun validateNames(tools: List<Triple<Uuid, String, McpTool>>) {
        val invalidNames = tools.map { it.second }.distinct().filter { name ->
            name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        }
        if (invalidNames.isNotEmpty()) throw InvalidMcpServerNamesException(invalidNames)
    }

    private fun buildMcpTool(
        serverId: Uuid,
        serverName: String,
        tool: me.rerere.zeroagent.data.ai.mcp.McpTool,
    ): Tool {
        // Namespace MCP tools by a server-id slug so two enabled servers that each expose a tool
        // of the same name don't collide (which would 400 or mis-route to whichever server
        // registered last). Built by the same buildMcpToolName helper mcp_list_tools uses to
        // advertise this name to the model (#88), so the two can never drift. The execute lambda
        // below still calls callTool with the REAL tool.name, since the namespacing exists only
        // on the model-facing surface.
        val mcpToolName = me.rerere.zeroagent.data.ai.mcp.buildMcpToolName(serverId, serverName, tool.name)
        return Tool(
            name = mcpToolName,
            description = tool.description ?: "",
            parameters = { tool.inputSchema },
            // MCP servers' tool surfaces are opaque to us - we can't tell read from write or safe
            // from destructive - so every MCP call asks by default. `mcp__*` is on the
            // non-waivable floor (ToolApprovalDefaults.isHardFloor), so this holds regardless of
            // the user's blacklist; only YOLO sits above it. The HARDLINE floor still applies via
            // HardlineCommandGuard's `mcp__*` branch, which scans every string arg for
            // shell-content patterns (rm -rf /, mkfs, shutdown, encoded payloads).
            needsApproval = { true },
            execute = {
                sources.callMcpTool(serverId, tool.name, it.jsonObject)
            },
        )
    }

    /**
     * Catalog summary for one MCP tool. Kept short on purpose: the catalog exists to avoid
     * shipping full schemas, so a summary that grew to schema size would defeat it. The
     * `[untrusted]` marker matters - an MCP server's description is data we did not author, and
     * it is now the only thing the model sees before deciding to open the tool.
     */
    private fun mcpCatalogSummary(tool: me.rerere.zeroagent.data.ai.mcp.McpTool): String {
        val firstLine = tool.description
            .orEmpty()
            .lineSequence()
            .firstOrNull { line -> line.isNotBlank() }
            .orEmpty()
            .trim()
        if (firstLine.isEmpty()) {
            return "[untrusted] External MCP tool with no description."
        }
        return "[untrusted] " + firstLine.take(TOOL_CATALOG_MAX_SUMMARY_CHARS)
    }


}
