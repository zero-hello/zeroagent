package me.rerere.zeroagent.ui.pages.setting.doctor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.zeroagent.data.ai.mcp.McpManager
import me.rerere.zeroagent.data.ai.tools.LocalToolOption
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.files.SkillManager
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.data.repository.ScheduledJobRepository
import me.rerere.zeroagent.data.repository.ScheduledJobRunRepository
import me.rerere.zeroagent.data.agentdef.AgentDefinitionRepository
import me.rerere.zeroagent.workflow.repository.WorkflowRepository
import me.rerere.zeroagent.browser.BrowserPreferences

/**
 * Run every diagnostic check. Returns the flat list — the Doctor screen groups by
 * [DoctorCheck.category].
 *
 * Most checks are cheap (Settings.Secure reads, package manager queries, in-memory state)
 * but a few do I/O (DB integrity PRAGMA, DNS resolve). Run on Dispatchers.IO at the call
 * site; the function itself is suspending so individual probes can withTimeoutOrNull.
 *
 * Adding a new check: append to the appropriate checker. Each helper function
 * returns either a single check or a list. Keep checks short — one concern per row.
 */
class DoctorChecks(
    context: Context,
    private val settingsStore: SettingsStore,
    workflowRepository: WorkflowRepository,
    scheduledJobRepository: ScheduledJobRepository,
    scheduledJobRunRepository: ScheduledJobRunRepository,
    conversationRepository: ConversationRepository,
    database: AppDatabase,
    // Pass 3: per-tool browser toggle store. Used by the browser write-tools-enabled INFO
    // row so the user can spot-check which side-effecting tools are currently switched on.
    // Optional + nullable so callers that don't construct this DoctorChecks via the DI
    // graph (a few legacy tests) keep compiling — the row is silently skipped when null.
    browserPreferences: BrowserPreferences? = null,
    // Phase 25 — SAF tree-grant store, backs the "granted directories" Doctor row.
    // Nullable + defaulted so legacy test paths that don't build the full DI graph compile.
    storageVolumeGrantStore: me.rerere.zeroagent.data.storage.StorageVolumeGrantStore? = null,
    // Surface the persisted LiteRT accelerator decision so the user can see whether their
    // local models actually engaged GPU/NPU or silently fell back to CPU.
    // Nullable + defaulted same as the others above for legacy test path compatibility.
    localRuntimePreferences: me.rerere.locallm.LocalRuntimePreferences? = null,
    // Doctor refresh: backs the skills.* rows. Nullable + defaulted same as the others.
    skillManager: SkillManager? = null,
    // Doctor refresh: backs the service.mcp_servers row. Nullable + defaulted same as the others.
    mcpManager: McpManager? = null,
    // P2-06b — the expert library, now the source of truth for the assistant.subagent_profiles
    // row. Nullable + defaulted like the fields above so legacy test paths that build a
    // DoctorChecks without the full DI graph keep compiling; an absent store reads as "no
    // experts configured", which is exactly what the row then reports.
    agentDefinitionRepository: AgentDefinitionRepository? = null,
) {
    private val deviceChecker = DoctorDeviceChecker(context)
    private val networkChecker = DoctorNetworkChecker(context, settingsStore)
    private val storageChecker = DoctorStorageChecker(context, database, localRuntimePreferences)
    private val dataChecker = DoctorDataChecker(
        context, settingsStore, workflowRepository, scheduledJobRepository, scheduledJobRunRepository,
        conversationRepository, database, storageVolumeGrantStore,
    )
    private val toolChecker = DoctorToolChecker(
        context, settingsStore, browserPreferences, skillManager, mcpManager, agentDefinitionRepository,
    )

    suspend fun runAll(): List<DoctorCheck> = withContext(Dispatchers.IO) {
        val settings = runCatching { settingsStore.settingsFlow.first { !it.init } }.getOrNull()
        val assistants = settings?.assistants.orEmpty()
        // A capability is in use if any assistant enables a tool that relies on it.
        val enabled = assistants.flatMap { it.localTools }.toSet()
        buildList {
            for (check in checksInDisplayOrder(assistants, enabled)) addAll(check())
        }
    }

    private fun checksInDisplayOrder(
        assistants: List<me.rerere.zeroagent.data.model.Assistant>,
        enabled: Set<LocalToolOption>,
    ): List<suspend () -> List<DoctorCheck>> = listOf<suspend () -> List<DoctorCheck>>(
        { deviceChecker.permissionChecks(enabled) },
        { deviceChecker.serviceChecks(enabled) },
        toolChecker::assistantChecks,
        { toolChecker.toolGroupChecks(enabled, assistants) },
        { dataChecker.databaseChecks(enabled) },
        networkChecker::providerChecks,
        storageChecker::localModelChecks,
        networkChecker::dnsChecks,
        { deviceChecker.termuxChecks(enabled) },
        { deviceChecker.shizukuChecks(enabled) },
        { toolChecker.browserChecks(enabled) },
        toolChecker::mcpChecks,
        { toolChecker.skillsChecks() },
        storageChecker::storageChecks,
        { storageChecker.maintenanceChecks() },
        dataChecker::compactionChecks,
        { toolChecker.diagnosticsChecks(enabled) },
    )
}
