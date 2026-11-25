package me.rerere.zeroagent.data.ai.tools

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.ai.ToolSurfaceResolver
import me.rerere.zeroagent.data.ai.mcp.McpManager
import me.rerere.zeroagent.data.ai.mcp.McpTool
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.files.SkillManager
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import kotlin.uuid.Uuid

data class ChatToolContext(
    val settings: Settings,
    val assistant: Assistant,
    val conversationId: Uuid,
    val model: Model,
    val workspaceCwd: String?,
)

/** Live tool dependencies; fakes can exercise assembly without Android, storage or a server. */
interface ChatToolSources {
    fun publicDataTools(): List<ToolCatalogEntry> = emptyList()
    fun searchTools(settings: Settings): List<Tool>
    suspend fun localTools(context: ChatToolContext): List<Tool>
    suspend fun workspaceTools(assistant: Assistant, cwd: String?): List<Tool>
    suspend fun coldMemoryTools(assistant: Assistant): List<Tool>
    suspend fun skillTools(assistant: Assistant): List<Tool>
    suspend fun mcpTools(assistant: Assistant?): List<Triple<Uuid, String, McpTool>>
    suspend fun callMcpTool(id: Uuid, name: String, args: JsonObject): List<UIMessagePart>
}

class DefaultChatToolSources(
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
    context: android.content.Context,
) : ChatToolSources {
    private val worldBank = WorldBankTools(readCatalog = {
        context.assets.open("world_bank_indicators.tsv").bufferedReader().use { it.readLines() }
    })

    override fun publicDataTools() = worldBank.entries

    override fun searchTools(settings: Settings) = createSearchTools(settings).toList()

    override suspend fun localTools(context: ChatToolContext): List<Tool> = ToolSurfaceResolver.resolve(
        localTools = localTools,
        assistant = context.assistant,
        context = ToolSurfaceResolver.chatContext(
            assistant = context.assistant,
            conversationId = context.conversationId,
            model = context.model,
            isHeadless = HeadlessConversations.isHeadless(context.conversationId),
            appPlaybook = coldMemoryPlaybookReader(context.assistant),
        ),
    )

    override suspend fun workspaceTools(assistant: Assistant, cwd: String?) =
        createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), cwd)

    override suspend fun coldMemoryTools(assistant: Assistant) = createColdMemoryToolsIfConfigured(assistant)

    override suspend fun skillTools(assistant: Assistant) = createSkillTools(
        enabledSkills = assistant.enabledSkills,
        allSkills = skillManager.listSkills(),
        skillManager = skillManager,
    )

    // null deliberately means the current global assistant, matching inherited child runs.
    override suspend fun mcpTools(assistant: Assistant?) =
        if (assistant == null) mcpManager.getAllAvailableTools() else mcpManager.getAllAvailableTools(assistant)

    override suspend fun callMcpTool(id: Uuid, name: String, args: JsonObject) = mcpManager.callTool(id, name, args)

    private suspend fun createWorkspaceToolsIfReady(workspaceId: String?, cwd: String? = null): List<Tool> {
        if (workspaceId.isNullOrBlank()) return emptyList()
        val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        return createWorkspaceTools(workspaceId, workspaceRepository, cwd)
    }

    /**
     * T-06 / (7) - cold memory (Markdown knowledge base). Built only when the assistant turned
     * the feature on AND bound a workspace AND picked a directory inside it; otherwise this
     * returns an empty list and the tool surface is byte-for-byte what it was before T-06.
     *
     * The directory is read through WorkspaceRepository, NOT through the workspace shell tools:
     * cold memory is plain file IO, so it keeps working even when the workspace rootfs was
     * never installed (the `workspace_*` family deliberately requires a READY rootfs).
     */
    private suspend fun createColdMemoryToolsIfConfigured(assistant: Assistant): List<Tool> {
        if (!assistant.coldMemoryEnabled) return emptyList()
        val workspaceId = assistant.workspaceId?.toString() ?: return emptyList()
        val dir = ColdMemoryRules.normalizeDir(assistant.coldMemoryDir) ?: return emptyList()
        if (workspaceRepository.getById(workspaceId) == null) return emptyList()

        fun childPath(name: String): String = if (dir.isEmpty()) name else "$dir/$name"

        suspend fun readFile(path: String): String? = try {
            workspaceRepository.readText(workspaceId, path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "coldMemory: cannot read '$path': ${e.message}")
            null
        }

        return buildColdMemoryTools(
            dirLabel = if (dir.isEmpty()) {
                ColdMemoryRules.WORKSPACE_PREFIX
            } else {
                "${ColdMemoryRules.WORKSPACE_PREFIX}/$dir"
            },
            listDocs = {
                try {
                    workspaceRepository.listFiles(workspaceId, WorkspaceStorageArea.FILES, dir)
                        .filter { !it.isDirectory }
                        .map { ColdMemoryDoc(it.name, it.sizeBytes) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The directory may not exist yet (cold memory is usable the moment a path
                    // is picked; the first write creates it). Report an empty knowledge base
                    // rather than failing the whole memory_index call.
                    Log.d(TAG, "coldMemory: cannot list '$dir': ${e.message}")
                    emptyList()
                }
            },
            readDoc = { name -> readFile(childPath(name)) },
            writeDoc = { name, content, append ->
                val path = childPath(name)
                val text = if (append) {
                    val previous = workspaceRepository.readReviewBase(workspaceId, path)
                    (previous.orEmpty() + content).also {
                        workspaceRepository.writeReviewedText(workspaceId, path, previous, it)
                    }
                } else {
                    workspaceRepository.writeText(workspaceId, path, content, overwrite = true)
                    content
                }
                ColdMemoryWriteResult(
                    fileName = name,
                    mode = if (append) "append" else "overwrite",
                    totalChars = text.length,
                )
            },
        )
    }

    /**
     * Screen-automation experience memory — the READ half of the per-app playbook.
     *
     * Returns a reader for one app's cold-memory playbook document (`app-<package>.md`), or `null`
     * when cold memory is off / unconfigured, in which case the screen tools add nothing. It
     * resolves the directory exactly like [createColdMemoryToolsIfConfigured], so a playbook is
     * always read from the same place the agent's `memory_write` persists it.
     */
    private suspend fun coldMemoryPlaybookReader(assistant: Assistant): (suspend (String) -> String?)? {
        if (!assistant.coldMemoryEnabled) return null
        val workspaceId = assistant.workspaceId?.toString() ?: return null
        val dir = ColdMemoryRules.normalizeDir(assistant.coldMemoryDir) ?: return null
        if (workspaceRepository.getById(workspaceId) == null) return null

        fun childPath(name: String): String = if (dir.isEmpty()) name else "$dir/$name"

        return { fileName ->
            try {
                workspaceRepository.readText(workspaceId, childPath(fileName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A missing (or unreadable) playbook is simply "no note yet".
                Log.d(TAG, "appPlaybook: cannot read '$fileName': ${e.message}")
                null
            }
        }
    }

}

private const val TAG = "ChatToolSources"
