package me.rerere.zeroagent.ui.pages.setting.doctor

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.first
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.BuildConfig
import me.rerere.zeroagent.data.ai.mcp.McpManager
import me.rerere.zeroagent.data.ai.mcp.McpStatus
import me.rerere.zeroagent.data.ai.tools.LocalToolOption
import me.rerere.zeroagent.data.ai.tools.local.AccessibilityServiceHandle
import me.rerere.zeroagent.data.ai.tools.local.PermissionHelper
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.files.SkillManager
import me.rerere.zeroagent.shizuku.ShizukuManager
import me.rerere.zeroagent.shizuku.ShizukuStatus
import me.rerere.zeroagent.subagent.SubAgentModelResolver
import me.rerere.zeroagent.data.agentdef.AgentDefinition
import me.rerere.zeroagent.data.agentdef.AgentDefinitionRepository
import me.rerere.zeroagent.browser.BrowserPreferences
import me.rerere.zeroagent.browser.BrowserToolDefaults
import java.io.File
import me.rerere.zeroagent.R

internal class DoctorToolChecker(
    context: Context,
    private val settingsStore: SettingsStore,
    private val browserPreferences: BrowserPreferences?,
    private val skillManager: SkillManager?,
    private val mcpManager: McpManager?,
    private val agentDefinitionRepository: AgentDefinitionRepository?,
) : DoctorCheckResources(context) {
    // ----- Active assistant ------------------------------------------------------------

    /**
     * Informational section. All rows are [Severity.INFO] — these are status rows, not
     * problem rows. The single "default assistant" row surfaces the assistant that:
         *   - Cron jobs run as (their assistantId is locked at job creation time, but new jobs
     *     inherit from the Settings default).
     *   - New in-app chats default to.
     *
     * A WARN row fires when the global assistant list is empty — that's a sign the settings
     * store was corrupted or a migration wiped the assistants list.
     *
     * No remote-chat configuration is consulted.
     */
    suspend fun assistantChecks(): List<DoctorCheck> = buildList {
        runCatching {
            val settings = settingsStore.settingsFlow.first { !it.init }
            val assistants = settings.assistants
            val defaultAssistant = settings.getCurrentAssistant()

            // Row 1: default assistant name + id
            add(
                DoctorCheck(
                    id = "assistant.default",
                    category = DoctorCategory.AssistantInfo,
                    label = text(R.string.doctor_check_default_assistant),
                    detail = if (assistants.isEmpty())
                        text(R.string.doctor_detail_no_assistants)
                    else
                        "\"${defaultAssistant.name.ifBlank { text(R.string.doctor_unnamed_paren) }}\" " +
                        "(id: ${defaultAssistant.id.toString().take(8)}…). " +
                        text(R.string.doctor_detail_default_assistant_suffix),
                    severity = if (assistants.isEmpty()) Severity.WARN else Severity.INFO,
                    fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_assistants), AppRouteKey.Assistant),
                )
            )

            // Row 2: total assistant count
            add(
                DoctorCheck(
                    id = "assistant.count",
                    category = DoctorCategory.AssistantInfo,
                    label = text(R.string.doctor_check_assistant_count),
                    detail = text(R.string.doctor_detail_assistant_count, assistants.size),
                    severity = Severity.INFO,
                    fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_assistants), AppRouteKey.Assistant),
                )
            )

            // Row 4: experts whose configured model no longer resolves. This is the #28
            // failure class made visible: an expert with a stale/deleted model id used to fall
            // back to inheriting the parent's model with no indication anything was wrong.
            // Reuses SubAgentModelResolver so the Doctor can't drift from the actual
            // dispatch-time resolution logic. P2-06b: the roster lives in the expert library
            // now, so the row reads its in-memory snapshot instead of Settings.subAgents.
            val subAgentStatus = subAgentProfileStatus(
                agentDefinitionRepository?.snapshotBlocking().orEmpty(),
                settings.providers,
            )
            add(
                DoctorCheck(
                    id = "assistant.subagent_profiles",
                    category = DoctorCategory.AssistantInfo,
                    label = text(R.string.doctor_check_subagent_profiles),
                    detail = when {
                        subAgentStatus.total == 0 -> text(R.string.doctor_detail_no_subagents)
                        subAgentStatus.broken.isEmpty() ->
                            text(R.string.doctor_detail_subagents_ok, subAgentStatus.total)
                        else ->
                            "${subAgentStatus.total} profile(s) configured. ${subAgentStatus.broken.size} " +
                                "reference a model that no longer resolves to a chat model of an enabled " +
                                "provider: ${subAgentStatus.broken.joinToString(", ")}."
                    },
                    severity = if (subAgentStatus.broken.isEmpty()) Severity.INFO else Severity.WARN,
                    fix = null,
                )
            )
        }
    }

    // ----- Tool groups -------------------------------------------------------------------

    /**
     * One row per backend-dependent tool group: enabled where, what it relies on, and
     * whether that backend is ready. Disabled groups are INFO with no fix (disabling is
     * a user choice); enabled groups with a missing backend are WARN with the fix.
     */
    suspend fun toolGroupChecks(
        enabled: Set<LocalToolOption>,
        assistants: List<me.rerere.zeroagent.data.model.Assistant>,
    ): List<DoctorCheck> = buildList {
        fun enabledBy(option: LocalToolOption): String {
            val names = assistants.filter { option in it.localTools }.map { it.name.ifBlank { text(R.string.doctor_unnamed) } }
            return when {
                names.isEmpty() -> text(R.string.doctor_detail_tools_disabled_all)
                names.size <= 3 -> text(R.string.doctor_detail_tools_enabled_for, names.joinToString(", "))
                else -> text(R.string.doctor_detail_tools_enabled_count, names.size, assistants.size)
            }
        }

        fun groupRow(
            id: String,
            option: LocalToolOption,
            label: String,
            reliesOn: String,
            backendReady: Boolean?,
            backendDetail: String,
            fix: FixAction?,
        ): DoctorCheck {
            val on = option in enabled
            val severity = when {
                !on -> Severity.INFO
                backendReady == false -> Severity.WARN
                else -> Severity.OK
            }
            val detail = buildString {
                append(enabledBy(option))
                append(" Relies on: ").append(reliesOn).append(".")
                if (on) append(" ").append(backendDetail)
            }
            return DoctorCheck(
                id = id,
                category = DoctorCategory.ToolGroups,
                label = label,
                detail = detail,
                severity = severity,
                fix = if (on && backendReady == false) fix else null,
            )
        }

        val accBound = AccessibilityServiceHandle.isRunning()
        add(
            groupRow(
                id = "tools.screen_automation",
                option = LocalToolOption.ScreenAutomation,
                label = text(R.string.doctor_check_screen_automation),
                reliesOn = text(R.string.doctor_relies_on_acc_overlay),
                backendReady = accBound,
                backendDetail = if (accBound) text(R.string.doctor_detail_backend_acc_bound)
                else text(R.string.doctor_detail_backend_acc_not_bound),
                fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
            )
        )
        add(
            groupRow(
                id = "tools.app_launcher",
                option = LocalToolOption.AppLauncher,
                label = text(R.string.doctor_check_app_launcher),
                reliesOn = text(R.string.doctor_relies_on_launch),
                backendReady = true,
                backendDetail = if (accBound) text(R.string.doctor_detail_backend_launch_acc_available)
                else text(R.string.doctor_detail_backend_launch_no_acc),
                fix = null,
            )
        )
        val kbInstalled = me.rerere.zeroagent.data.keyboard.KeyboardApiClient(context).isKeyboardInstalled()
        val kbIme = me.rerere.zeroagent.data.keyboard.KeyboardApiClient.isEnabledAsIme(context)
        add(
            groupRow(
                id = "tools.keyboard",
                option = LocalToolOption.KeyboardControl,
                label = text(R.string.doctor_check_agent_keyboard),
                reliesOn = text(R.string.doctor_relies_on_keyboard),
                backendReady = kbInstalled && kbIme,
                backendDetail = when {
                    !kbInstalled -> text(R.string.doctor_detail_kb_not_installed)
                    !kbIme -> text(R.string.doctor_detail_kb_installed_not_enabled)
                    else -> text(R.string.doctor_detail_kb_ok)
                },
                fix = FixAction.OpenIntent(
                    text(R.string.doctor_fix_open_keyboard_settings),
                    android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS),
                ),
            )
        )
        // Notification listener / Termux / Shizuku / Files / Browser rows reuse the
        // readiness probes the existing category checks already compute, rather than
        // duplicating logic inline.
        add(
            groupRow(
                id = "tools.notification_listener",
                option = LocalToolOption.NotificationListener,
                label = text(R.string.doctor_check_notif_listener),
                reliesOn = text(R.string.doctor_relies_on_notification_listener),
                backendReady = PermissionHelper.hasNotificationListener(context),
                backendDetail = if (PermissionHelper.hasNotificationListener(context))
                    text(R.string.doctor_detail_nl_granted)
                else
                    text(R.string.doctor_detail_backend_nl_not_granted),
                fix = FixAction.OpenIntent(
                    text(R.string.doctor_fix_open_settings),
                    PermissionHelper.notificationListenerSettingsIntent(),
                ),
            )
        )
        val termuxInstalled = isEmbeddedTerminalReady()
        add(
            groupRow(
                id = "tools.termux",
                option = LocalToolOption.Termux,
                label = text(R.string.embedded_terminal_title),
                reliesOn = text(R.string.embedded_terminal_description),
                backendReady = termuxInstalled,
                backendDetail = if (termuxInstalled) text(R.string.embedded_terminal_ready)
                else text(R.string.embedded_terminal_not_ready),
                fix = null,
            )
        )
        val shizukuStatus = ShizukuManager.status(context)
        add(
            groupRow(
                id = "tools.shizuku",
                option = LocalToolOption.Shizuku,
                label = text(R.string.doctor_check_shizuku),
                reliesOn = text(R.string.doctor_relies_on_shizuku),
                backendReady = shizukuStatus == ShizukuStatus.READY,
                backendDetail = when (shizukuStatus) {
                    ShizukuStatus.READY -> text(R.string.doctor_detail_shizuku_ready)
                    ShizukuStatus.NOT_INSTALLED -> text(R.string.doctor_detail_shizuku_not_installed)
                    ShizukuStatus.NOT_RUNNING -> text(R.string.doctor_detail_shizuku_not_running)
                    ShizukuStatus.PERMISSION_DENIED -> text(R.string.doctor_detail_shizuku_no_permission)
                },
                fix = if (shizukuStatus != ShizukuStatus.READY)
                    FixAction.OpenAppRoute(text(R.string.doctor_fix_open_shizuku), AppRouteKey.SettingShizuku)
                else null,
            )
        )
        val filesReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || PermissionHelper.hasAllFilesAccess(context)
        add(
            groupRow(
                id = "tools.files",
                option = LocalToolOption.Files,
                label = text(R.string.doctor_check_files),
                reliesOn = text(R.string.doctor_relies_on_all_files),
                backendReady = filesReady,
                backendDetail = if (filesReady) text(R.string.doctor_detail_files_ok)
                else text(R.string.doctor_detail_files_restricted),
                fix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    FixAction.OpenIntent(text(R.string.doctor_fix_open_settings), PermissionHelper.allFilesAccessIntent(context))
                else null,
            )
        )
        val browserReady = isBrowserProfileDirReady()
        add(
            groupRow(
                id = "tools.browser",
                option = LocalToolOption.Browser,
                label = text(R.string.doctor_check_browser),
                reliesOn = text(R.string.doctor_relies_on_browser_profile),
                backendReady = browserReady,
                backendDetail = if (browserReady) text(R.string.doctor_detail_browser_ok)
                else text(R.string.doctor_detail_browser_fail),
                fix = FixAction.AutoFix(
                    label = text(R.string.doctor_fix_create_dir),
                    run = {
                        val dir = browserProfileDir()
                        val created = runCatching { dir.mkdirs() }.getOrDefault(false)
                        val nowOk = dir.exists() && dir.canWrite()
                        AutoFixResult(
                            ok = nowOk,
                            message = if (nowOk) text(R.string.doctor_fix_browser_created, dir.absolutePath)
                            else if (created) text(R.string.doctor_fix_browser_created_not_writable)
                            else text(R.string.doctor_fix_browser_mkdirs_failed),
                        )
                    },
                ),
            )
        )
    }

    /** Shared with [browserChecks] so the profile-directory path is defined once. */
    private fun browserProfileDir(): File = File(context.filesDir, "browser-profile")

    /** Shared with [browserChecks] so the readiness probe is computed once. */
    private fun isBrowserProfileDirReady(): Boolean {
        val dir = browserProfileDir()
        val exists = runCatching { dir.exists() && dir.isDirectory }.getOrDefault(false)
        return exists && runCatching { dir.canWrite() }.getOrDefault(false)
    }

    // ----- MCP servers ----------------------------------------------------------------------

    /**
     * Doctor refresh: read-only summary of configured MCP servers against
     * [McpManager.syncingStatus]: the live in-memory connection state cache. Never
     * initiates a connection; a server the app hasn't tried to sync yet just reads as "not
     * connected" here, same as one that failed.
     */
    suspend fun mcpChecks(): List<DoctorCheck> = buildList {
        val manager = mcpManager ?: return@buildList
        runCatching {
            val settings = settingsStore.settingsFlow.first { !it.init }
            val statuses = manager.syncingStatus.value
            val rows = settings.mcpServers.map { server ->
                val name = server.commonOptions.name.ifBlank { server.id.toString().take(8) }
                Triple(name, server.commonOptions.enable, statuses[server.id] is McpStatus.Connected)
            }
            val summary = mcpServerSummary(rows)
            add(
                DoctorCheck(
                    id = "service.mcp_servers",
                    category = DoctorCategory.Services,
                    label = text(R.string.doctor_check_mcp_servers),
                    detail = when {
                        summary.configured == 0 -> text(R.string.doctor_detail_no_mcp_servers)
                        summary.enabledNotConnected.isEmpty() ->
                            "${summary.configured} configured, ${summary.enabled} enabled, ${summary.connected} connected."
                        else ->
                            "${summary.configured} configured, ${summary.enabled} enabled, ${summary.connected} connected. " +
                                text(R.string.doctor_detail_mcp_not_connected, summary.enabledNotConnected.joinToString(", "))
                    },
                    severity = if (summary.enabledNotConnected.isEmpty()) Severity.INFO else Severity.WARN,
                    fix = if (summary.configured > 0)
                        FixAction.OpenAppRoute(text(R.string.doctor_fix_open_mcp), AppRouteKey.SettingMcp)
                    else null,
                )
            )
        }.onFailure {
            add(
                DoctorCheck(
                    id = "service.mcp_servers",
                    category = DoctorCategory.Services,
                    label = text(R.string.doctor_check_mcp_servers),
                    detail = text(R.string.doctor_detail_probe_failed, it::class.simpleName ?: "?", it.message ?: "?"),
                    severity = Severity.WARN,
                )
            )
        }
    }

    // ----- Skills ---------------------------------------------------------------------------

    /**
     * Doctor refresh: installed-skill count (bundled vs user-added, told apart by the
     * `.seeded` sentinel [SkillManager.seedDefaultSkillsIfNeeded] writes) plus bundled-seed
     * health, whether the on-disk `.core-bundled-hash` sentinel still matches what the
     * currently-installed APK would seed. A stale sentinel is what froze bundled skill
     * updates before; it means seeding failed silently rather than that a re-seed is merely
     * pending, since [me.rerere.zeroagent.ZeroAgentApp] runs the seed pass on every launch.
     */
    fun skillsChecks(): List<DoctorCheck> = buildList {
        val mgr = skillManager ?: return@buildList
        val skillsResult = runCatching { mgr.listSkills() }
        val skills = skillsResult.getOrNull()
        if (skills == null) {
            val err = skillsResult.exceptionOrNull()
            val detail = text(R.string.doctor_detail_probe_failed, err?.let { it::class.simpleName } ?: "?", err?.message ?: "?")
            add(DoctorCheck("skills.installed", DoctorCategory.Database, "Installed skills", detail, Severity.WARN))
            add(DoctorCheck("skills.seed", DoctorCategory.Database, "Bundled skill seed health", detail, Severity.WARN))
            return@buildList
        }

        runCatching {
            val bundledCount = skills.count { it.skillDir.resolve(".seeded").exists() }
            val userCount = skills.size - bundledCount
            add(
                DoctorCheck(
                    id = "skills.installed",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_skills_installed),
                    detail = text(R.string.doctor_detail_skills_count, skills.size, bundledCount, userCount),
                    severity = Severity.INFO,
                    fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_skills), AppRouteKey.Skills),
                )
            )
        }.onFailure {
            add(
                DoctorCheck(
                    id = "skills.installed",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_skills_installed),
                    detail = text(R.string.doctor_detail_probe_failed, it::class.simpleName ?: "?", it.message ?: "?"),
                    severity = Severity.WARN,
                )
            )
        }

        runCatching {
            val bundledNames = mgr.bundledSkillNames()
            // Bundled-name match alone isn't ownership: a user can delete a bundled skill and
            // recreate a same-named one via saveSkill without writing .seeded/.core-bundled-hash.
            // Mirror decideSeedAction's ownedByUs rule here: core skills (autoLoad) are
            // unconditionally ours, non-core skills are ours only if we actually seeded them.
            val entries = skills.filter { skill ->
                skill.skillDir.name in bundledNames &&
                    (skill.autoLoad || skill.skillDir.resolve(".seeded").exists())
            }.map { skill ->
                val hashFile = skill.skillDir.resolve(".core-bundled-hash")
                val stored = if (hashFile.exists())
                    runCatching { hashFile.readText().trim() }.getOrNull()
                else null
                val current = runCatching { mgr.bundledSkillAssetHash(skill.skillDir.name) }.getOrNull()
                SkillSeedEntry(skill.skillDir.name, isBundled = true, storedHash = stored, currentHash = current)
            }
            val stale = staleSeedSkillNames(entries)
            add(
                DoctorCheck(
                    id = "skills.seed",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_skills_seed),
                    detail = if (stale.isEmpty())
                        text(R.string.doctor_detail_skills_seed_ok)
                    else
                        text(R.string.doctor_detail_skills_seed_stale, stale.joinToString(", ")),
                    severity = if (stale.isEmpty()) Severity.OK else Severity.WARN,
                    fix = if (stale.isNotEmpty())
                        FixAction.OpenAppRoute(text(R.string.doctor_fix_open_skills), AppRouteKey.Skills)
                    else null,
                )
            )
        }.onFailure {
            add(
                DoctorCheck(
                    id = "skills.seed",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_skills_seed),
                    detail = text(R.string.doctor_detail_probe_failed, it::class.simpleName ?: "?", it.message ?: "?"),
                    severity = Severity.WARN,
                )
            )
        }
    }

    // ----- Browser (Pass 3) ------------------------------------------------------------

    /**
     * Pass 3: Doctor rows for the in-app browser feature.
     *  - `browser.profile_dir_writable` — the WebView profile lives at
     *    `${filesDir}/browser-profile/`. The directory MUST exist + be writable for cookies
     *    to persist across app restarts. AutoFix re-creates it on demand.
     *  - `browser.write_tools_status` — informational live count of which write-tools the
     *    user has switched on. Lets a user spot-check at a glance whether `browser_type`
     *    is unintentionally enabled. INFO severity, no fix action.
     *
     * The category is [DoctorCategory.Permissions] per the spec ("Permissions / Services").
     * Both rows are emitted regardless of master Browser-toggle state, but their severity
     * downgrades to INFO when no assistant has [LocalToolOption.Browser] enabled (matches
     * the existing capability-aware pattern used throughout the file).
     */
    fun browserChecks(enabled: Set<LocalToolOption>): List<DoctorCheck> = buildList {
        val needers = requirersOf(DoctorCapability.Browser, enabled)
        val browserNeeded = needers.isNotEmpty()

        // Row 1: profile dir writable (with AutoFix to mkdirs).
        val profileDir = browserProfileDir()
        val exists = runCatching { profileDir.exists() && profileDir.isDirectory }.getOrDefault(false)
        val writable = exists && runCatching { profileDir.canWrite() }.getOrDefault(false)
        val ok = exists && writable
        add(
            DoctorCheck(
                id = "browser.profile_dir_writable",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_browser_profile),
                detail = when {
                    ok && browserNeeded -> "${profileDir.absolutePath} exists and is writable — cookies persist."
                    ok -> "${profileDir.absolutePath} exists. Not required by any enabled tool."
                    !exists && browserNeeded -> text(R.string.doctor_detail_browser_not_exist)
                    !exists -> "Directory does not exist. Not required by any enabled tool."
                    !writable && browserNeeded -> text(R.string.doctor_detail_browser_not_writable)
                    else -> text(R.string.doctor_detail_browser_not_writable_generic)
                },
                severity = when {
                    ok -> Severity.OK
                    browserNeeded -> Severity.WARN
                    else -> Severity.INFO
                },
                fix = if (!ok && browserNeeded) FixAction.AutoFix(
                    label = text(R.string.doctor_fix_create_dir),
                    run = {
                        val created = runCatching { profileDir.mkdirs() }.getOrDefault(false)
                        val nowOk = profileDir.exists() && profileDir.canWrite()
                        AutoFixResult(
                            ok = nowOk,
                            message = if (nowOk) text(R.string.doctor_fix_browser_created_2, profileDir.absolutePath)
                            else if (created) text(R.string.doctor_fix_browser_not_writable_2)
                            else text(R.string.doctor_fix_browser_mkdirs_failed),
                        )
                    },
                ) else null,
            )
        )

        // Row 2: write-tools live count (INFO only). Skipped silently if BrowserPreferences
        // wasn't injected — the row is purely informational and the test harness paths
        // that don't construct prefs shouldn't fail.
        val prefs = browserPreferences
        if (prefs != null) {
            val snapshot = runCatching { prefs.snapshotBlocking() }.getOrDefault(BrowserToolDefaults.DEFAULT_ENABLED)
            val onWriteTools = BrowserToolDefaults.WRITE_TOOLS.filter { snapshot[it] == true }
            val detail = if (onWriteTools.isEmpty())
                "Live count of side-effecting browser tools enabled: 0. None of the write tools are switched on."
            else
                "Live count of side-effecting browser tools enabled: ${onWriteTools.size} (${onWriteTools.joinToString(", ") { it.removePrefix("browser_") }})."
            add(
                DoctorCheck(
                    id = "browser.write_tools_status",
                    category = DoctorCategory.Permissions,
                    label = text(R.string.doctor_check_browser_write_tools),
                    detail = detail,
                    severity = Severity.INFO,
                )
            )
        }
    }

    // ----- Diagnostics summary ---------------------------------------------------------

    fun diagnosticsChecks(enabled: Set<LocalToolOption>): List<DoctorCheck> = listOf(
        DoctorCheck(
            id = "diag.app",
            category = DoctorCategory.Diagnostics,
            label = text(R.string.doctor_check_app_build),
            detail = text(R.string.doctor_detail_app_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.DEBUG.toString()),
            severity = Severity.INFO,
        ),
        DoctorCheck(
            id = "diag.android",
            category = DoctorCategory.Diagnostics,
            label = text(R.string.doctor_check_android),
            detail = text(R.string.doctor_detail_android, Build.VERSION.SDK_INT, Build.VERSION.RELEASE, Build.MANUFACTURER, Build.MODEL),
            severity = Severity.INFO,
        ),
        DoctorCheck(
            id = "diag.runtime",
            category = DoctorCategory.Diagnostics,
            label = text(R.string.doctor_check_runtime),
            detail = run {
                val rt = Runtime.getRuntime()
                val freeMb = rt.freeMemory() / (1024 * 1024)
                val totalMb = rt.totalMemory() / (1024 * 1024)
                val maxMb = rt.maxMemory() / (1024 * 1024)
                text(R.string.doctor_detail_heap, freeMb, totalMb, maxMb)
            },
            severity = Severity.INFO,
        ),
        DoctorCheck(
            id = "diag.enabled_tools",
            category = DoctorCategory.Diagnostics,
            label = text(R.string.doctor_check_enabled_tools),
            detail = if (enabled.isEmpty()) "No local tools enabled — agentic features won't work."
            else "${enabled.size} tool group(s) enabled.",
            severity = if (enabled.isEmpty()) Severity.WARN else Severity.INFO,
        ),
    )
}

/**
 * Pure decision logic backing the "assistant.subagent_profiles" row: which stored experts have
 * a `modelId` that no longer resolves to a chat model of an enabled provider (the #28 failure
 * class; it used to fail silently at dispatch time). Reuses [SubAgentModelResolver.resolve]
 * itself rather than re-deriving model lookup; an expert's `modelId` is the canonical `Uuid`
 * **string** (P2-06a stores ids as TEXT so the schema carries no experimental uuid type), so it
 * is handed straight to the resolver as its string input, exactly like a `subagent_dispatch`
 * caller would.
 */
internal data class SubAgentProfileStatus(val total: Int, val broken: List<String>)

internal fun subAgentProfileStatus(
    definitions: List<AgentDefinition>,
    providers: List<ProviderSetting>,
): SubAgentProfileStatus = SubAgentProfileStatus(
    total = definitions.size,
    broken = definitions.filter { definition ->
        val modelId = definition.modelId ?: return@filter false
        SubAgentModelResolver.resolve(modelId, providers) is SubAgentModelResolver.Result.Failed
    }.map { it.name },
)

/**
 * Pure decision logic backing the "service.mcp_servers" row: given each configured
 * server's (name, enabled, connected) triple, report the configured/enabled/connected
 * counts and which enabled servers are not currently connected.
 */
internal data class McpServerSummary(
    val configured: Int,
    val enabled: Int,
    val connected: Int,
    val enabledNotConnected: List<String>,
)

internal fun mcpServerSummary(servers: List<Triple<String, Boolean, Boolean>>): McpServerSummary =
    McpServerSummary(
        configured = servers.size,
        enabled = servers.count { (_, enabled, _) -> enabled },
        connected = servers.count { (_, _, connected) -> connected },
        enabledNotConnected = servers.filter { (_, enabled, connected) -> enabled && !connected }
            .map { (name, _, _) -> name },
    )

/**
 * Pure decision logic backing the "skills.seed" row: a bundled skill's on-disk
 * `.core-bundled-hash` sentinel is stale when it's missing, unreadable, or doesn't match
 * the hash of what the app would currently seed. Non-bundled (user-added) entries are
 * never flagged: [isBundled] gates them out entirely, mirroring
 * [me.rerere.zeroagent.data.files.decideSeedAction]'s "never touch a directory we didn't
 * create" rule.
 */
internal data class SkillSeedEntry(
    val name: String,
    val isBundled: Boolean,
    val storedHash: String?,
    val currentHash: String?,
)

internal fun staleSeedSkillNames(entries: List<SkillSeedEntry>): List<String> =
    entries.filter { it.isBundled && it.storedHash != it.currentHash }.map { it.name }
