package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.core.InputSchema
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.ai.mcp.McpTool
import me.rerere.zeroagent.data.ai.mcp.buildMcpToolName
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.subagent.SubAgentSurface
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ChatToolFactoryTest {
    @Test fun `generation preserves category order and MCP execution and approval`() = runBlocking {
        val sources = Sources()
        val id = Uuid.random()
        val schema = InputSchema.Obj(JsonObject(emptyMap()))
        sources.remote = listOf(Triple(id, "server", McpTool(name = "read", description = "Read data", inputSchema = schema, needsApproval = false)))
        val tools = generation(sources)
        assertEquals(listOf("search", "local", "workspace", "memory", "skill", buildMcpToolName(id, "server", "read")), tools.map { it.name })
        assertTrue(tools.last().needsApproval(JsonNull))
        assertSame(schema, tools.last().parameters())
        assertEquals("Read data", tools.last().description)
        val args = JsonObject(emptyMap())
        tools.last().execute(args)
        assertEquals(Triple(id, "read", args), sources.invocation)
        assertNull(sources.scopedAssistant)
    }

    @Test fun `rerun keeps its own search rule and never enables compaction or catalog`() = runBlocking {
        val sources = Sources()
        val context = context(Assistant(enableWebSearch = true, enableCompactContextTool = true, toolSurfaceMode = ToolSurfaceMode.PROGRESSIVE_CATALOG))
        sources.remote = listOf(Triple(Uuid.random(), "server", McpTool(name = "read")))
        assertEquals(listOf("local", "workspace", "memory", "skill"), generation(sources, context, search = false).map { it.name }.take(4))
        val rerun = ChatToolFactory(sources).createForRerun(context)
        assertEquals("search", rerun.first().name)
        assertFalse(rerun.any { it.name in setOf("compact_context", "tool_search", "tool_open") })
        assertTrue(rerun.last().name.startsWith("mcp__"))
    }

    @Test fun `generation validates names while rerun retains the existing permissive path`() = runBlocking {
        val sources = Sources()
        sources.remote = listOf(Triple(Uuid.random(), "bad-name", McpTool(name = "read")))
        try { generation(sources); fail("Generation must reject the invalid name") }
        catch (error: InvalidMcpServerNamesException) { assertEquals(listOf("bad-name"), error.names) }
        assertTrue(ChatToolFactory(sources).createForRerun(context()).last().name.startsWith("mcp__"))
    }

    @Test fun `catalog shares activation across turns and drops tools removed from the server`() = runBlocking {
        val sources = Sources()
        val id = Uuid.random()
        sources.remote = listOf(Triple(id, "server", McpTool(name = "read", description = "Read\nMore")))
        val context = context(Assistant(toolSurfaceMode = ToolSurfaceMode.PROGRESSIVE_CATALOG))
        val activation = ToolActivationState()
        val first = generation(sources, context, activation = activation)
        assertEquals(listOf("tool_search", "tool_open"), first.takeLast(2).map { it.name })
        val name = buildMcpToolName(id, "server", "read")
        activation.activate(ToolCatalog(listOf(ToolCatalogEntry(name, "Read", ToolCatalogSource.MCP, tool(name)))), listOf(name))
        assertEquals(name, generation(sources, context, activation = activation).last().name)
        sources.remote = emptyList()
        generation(sources, context, activation = activation)
        assertTrue(activation.active().isEmpty())
    }

    @Test fun `same named MCP servers remain distinct and precheck still uses global scope`() = runBlocking {
        val sources = Sources()
        sources.remote = List(2) { Triple(Uuid.random(), "same", McpTool(name = "read")) }
        val factory = ChatToolFactory(sources)
        assertEquals(2, factory.validateMcpServerNames().size)
        assertNull(sources.scopedAssistant)
        assertEquals(2, generation(sources).filter { it.name.startsWith("mcp__") }.map { it.name }.distinct().size)
    }

    @Test fun `precheck rejects blank non ASCII and punctuated names in encounter order`() = runBlocking {
        val sources = Sources()
        sources.remote = listOf("", "中文", "bad_name", "中文").map { Triple(Uuid.random(), it, McpTool(name = "read")) }
        try { ChatToolFactory(sources).validateMcpServerNames(); fail("Names must be rejected") }
        catch (error: InvalidMcpServerNamesException) { assertEquals(listOf("", "中文", "bad_name"), error.names) }
    }

    @Test fun `child assistant context and final restrictions apply to every tool category`() = runBlocking {
        val sources = Sources()
        val parent = Assistant(enableWebSearch = true)
        val child = parent.copy(name = "Child")
        val context = context(parent)
        SubAgentSurface.freeze(context.conversationId, child, requested = listOf("local", "memory"))
        try {
            val tools = generation(sources, context)
            assertSame(child, sources.scopedAssistant)
            assertSame(child, sources.localContext!!.assistant)
            assertEquals(context.conversationId, sources.localContext!!.conversationId)
            assertEquals(listOf("local", "memory"), tools.map { it.name })
        } finally { SubAgentSurface.release(context.conversationId) }
    }

    @Test fun `unavailable categories stay absent and disabled skills are not loaded`() = runBlocking {
        val sources = Sources()
        sources.available = false
        val tools = generation(sources, context().copy(assistant = Assistant()), search = false)
        assertEquals(listOf("local"), tools.map { it.name })
        assertEquals(0, sources.skillLoads)
    }

    @Test fun `compaction callback belongs only to opted in generation`() = runBlocking {
        val sources = Sources()
        val context = context(Assistant(enableCompactContextTool = true))
        var compacted = false
        val tools = ChatToolFactory(sources).createForGeneration(context, false, { error("Direct mode must not allocate activation") }) {
            compacted = true
            CompactionToolResult(true, 100, 50, 20)
        }
        assertFalse(compacted)
        tools.single { it.name == "compact_context" }.execute(JsonObject(emptyMap()))
        assertTrue(compacted)
        assertFalse(ChatToolFactory(sources).createForRerun(context).any { it.name == "compact_context" })
    }

    private fun context(assistant: Assistant = Assistant(enableWebSearch = true)) = ChatToolContext(
        Settings(), assistant.copy(enabledSkills = setOf("test")), Uuid.random(), Model(modelId = "test"), "/project",
    )

    private suspend fun generation(sources: Sources, context: ChatToolContext = context(), search: Boolean = true, activation: ToolActivationState = ToolActivationState()) =
        ChatToolFactory(sources).createForGeneration(context, search, { activation }) { CompactionToolResult(false, 0, 0, 0) }

    private class Sources : ChatToolSources {
        var remote = emptyList<Triple<Uuid, String, McpTool>>()
        var scopedAssistant: Assistant? = null
        var localContext: ChatToolContext? = null
        var invocation: Triple<Uuid, String, JsonObject>? = null
        var available = true
        var skillLoads = 0
        override fun searchTools(settings: Settings) = listOf(tool("search"))
        override suspend fun localTools(context: ChatToolContext): List<Tool> { localContext = context; return listOf(tool("local")) }
        override suspend fun workspaceTools(assistant: Assistant, cwd: String?) = if (available) listOf(tool("workspace")) else emptyList()
        override suspend fun coldMemoryTools(assistant: Assistant) = if (available) listOf(tool("memory")) else emptyList()
        override suspend fun skillTools(assistant: Assistant): List<Tool> { skillLoads++; return if (available) listOf(tool("skill")) else emptyList() }
        override suspend fun mcpTools(assistant: Assistant?): List<Triple<Uuid, String, McpTool>> { scopedAssistant = assistant; return remote }
        override suspend fun callMcpTool(id: Uuid, name: String, args: JsonObject): List<UIMessagePart> { invocation = Triple(id, name, args); return emptyList() }
    }

    companion object { private fun tool(name: String) = Tool(name, name, execute = { emptyList() }) }
}
