package me.rerere.zeroagent.ui.pages.setting.doctor

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.zeroagent.data.ai.tools.LocalToolOption
import me.rerere.zeroagent.data.datastore.AutoCompactionThresholdMode
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.data.repository.ScheduledJobRepository
import me.rerere.zeroagent.data.repository.ScheduledJobRunRepository
import me.rerere.zeroagent.workflow.repository.WorkflowRepository
import me.rerere.zeroagent.R

internal class DoctorDataChecker(
    context: Context,
    private val settingsStore: SettingsStore,
    private val workflowRepository: WorkflowRepository,
    private val scheduledJobRepository: ScheduledJobRepository,
    private val scheduledJobRunRepository: ScheduledJobRunRepository,
    private val conversationRepository: ConversationRepository,
    private val database: AppDatabase,
    private val storageVolumeGrantStore: me.rerere.zeroagent.data.storage.StorageVolumeGrantStore?,
) : DoctorCheckResources(context) {
    // ----- Database --------------------------------------------------------------------

    suspend fun databaseChecks(enabled: Set<LocalToolOption>): List<DoctorCheck> = buildList {
        // Migration version
        val version = runCatching { database.openHelper.readableDatabase.version }.getOrDefault(-1)
        add(
            DoctorCheck(
                id = "db.version",
                category = DoctorCategory.Database,
                label = text(R.string.doctor_check_db_version),
                // Room refuses to open the DB unless the stored version matches the compiled schema;
                // if we got here, version is the live schema version (migrations ran successfully).
                detail = if (version > 0) text(R.string.doctor_detail_db_version_ok, version)
                else text(R.string.doctor_detail_db_version_fail),
                severity = if (version > 0) Severity.OK else Severity.WARN,
            )
        )
        // Integrity check
        val integrity = runCatching {
            withTimeoutOrNull(5_000L) {
                database.openHelper.readableDatabase
                    .query("PRAGMA integrity_check;")
                    .use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }
        }.getOrNull()
        // Offer an AutoFix only when the corruption mentions message_fts — that's the one
        // we know how to repair (DROP + recreate + reindex from the messages table). For
        // any other integrity failure, surface the message and let the user decide; we
        // don't blanket-rebuild things we don't know are safe.
        val mentionsFts = integrity != null && integrity != "ok" && integrity.contains("message_fts", ignoreCase = true)
        add(
            DoctorCheck(
                id = "db.integrity",
                category = DoctorCategory.Database,
                label = text(R.string.doctor_check_db_integrity),
                detail = when (integrity) {
                    null -> text(R.string.doctor_detail_db_integrity_timeout)
                    "ok" -> text(R.string.doctor_detail_db_integrity_ok)
                    else -> text(R.string.doctor_detail_db_integrity_result, integrity)
                },
                severity = if (integrity == "ok") Severity.OK else Severity.FAIL,
                fix = if (mentionsFts) FixAction.AutoFix(
                    label = text(R.string.doctor_fix_rebuild_index),
                    run = {
                        runCatching {
                            val n = conversationRepository.repairAndRebuildIndexes()
                            AutoFixResult(ok = true, message = text(R.string.doctor_fix_db_rebuilt, n))
                        }.getOrElse {
                            AutoFixResult(
                                ok = false,
                                message = text(R.string.doctor_fix_db_repair_failed, it::class.simpleName ?: "?", it.message ?: "?"),
                            )
                        }
                    },
                ) else null,
            )
        )
        // Workflows summary
        runCatching {
            val all = workflowRepository.observeAll().first()
            val enabled = all.count { it.entity.enabled }
            add(
                DoctorCheck(
                    id = "db.workflows",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_workflows),
                    detail = text(R.string.doctor_detail_tools_count, all.size, enabled),
                    severity = Severity.INFO,
                    fix = if (all.isNotEmpty())
                        FixAction.OpenAppRoute(text(R.string.doctor_fix_open_workflows), AppRouteKey.SettingWorkflows)
                    else null,
                )
            )
        }
        // Scheduled jobs summary
        runCatching {
            val all = scheduledJobRepository.getAll()
            val enabled = all.count { it.enabled }
            add(
                DoctorCheck(
                    id = "db.scheduled_jobs",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_scheduled_jobs),
                    detail = text(R.string.doctor_detail_tools_count, all.size, enabled),
                    severity = Severity.INFO,
                    fix = if (all.isNotEmpty())
                        FixAction.OpenAppRoute(text(R.string.doctor_fix_open_scheduled_jobs), AppRouteKey.SettingScheduledJobs)
                    else null,
                )
            )
        }
        // Stranded run rows (started but never finished — process killed mid-run)
        runCatching {
            val stranded = scheduledJobRunRepository.getStranded(System.currentTimeMillis() - 30 * 60_000L)
            add(
                DoctorCheck(
                    id = "db.stranded_runs",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_stranded_runs),
                    detail = if (stranded.isEmpty())
                        text(R.string.doctor_detail_worker_clean)
                    else
                        text(R.string.doctor_detail_worker_stranded, stranded.size),
                    severity = if (stranded.isEmpty()) Severity.OK else Severity.WARN,
                )
            )
        }
        // Phase 25 — SAF granted-directories live count for the ExternalStorage tool.
        // Reconciles against the OS persisted-permission list so revoked grants drop off.
        val store = storageVolumeGrantStore
        if (store != null) {
            runCatching {
                val externalStorageEnabled = enabled.contains(LocalToolOption.ExternalStorage)
                val grants = store.reconcile()
                add(
                    DoctorCheck(
                        id = "storage.granted_directories",
                        category = DoctorCategory.Database,
                        label = text(R.string.doctor_check_granted_dirs),
                        detail = when {
                            !externalStorageEnabled && grants.isEmpty() ->
                                text(R.string.doctor_detail_storage_not_enabled)
                            grants.isEmpty() ->
                                text(R.string.doctor_detail_storage_no_grants)
                            else ->
                                text(R.string.doctor_detail_storage_grants, grants.size, grants.joinToString(", ") { it.displayName })
                        },
                        severity = if (externalStorageEnabled && grants.isNotEmpty())
                            Severity.OK else Severity.INFO,
                    )
                )
            }
        }
    }

    // ----- Context compaction ---------------------------------------------------------------

    /**
     * Doctor refresh: whether auto-compaction is enabled, its configured threshold, and how
     * many conversations have ever actually been compacted, the fact we've repeatedly been
     * unable to confirm on device. Always INFO: zero-with-it-enabled is an expected state
     * (no conversation has crossed the threshold yet), not a problem.
     */
    suspend fun compactionChecks(): List<DoctorCheck> = buildList {
        runCatching {
            val settings = settingsStore.settingsFlow.first { !it.init }
            val count = database.conversationCompactionDao().countAll()
            val thresholdDesc = when (settings.autoCompactionThresholdMode) {
                AutoCompactionThresholdMode.PERCENT -> "${settings.autoCompactionThresholdPercent}% of context"
                AutoCompactionThresholdMode.TOKENS -> "${settings.autoCompactionThresholdTokensK}k tokens"
            }
            add(
                DoctorCheck(
                    id = "diag.compaction",
                    category = DoctorCategory.Diagnostics,
                    label = text(R.string.doctor_check_compaction),
                    detail = when {
                        !settings.enableAutoCompaction ->
                            text(R.string.doctor_detail_compaction_disabled, count)
                        count == 0 ->
                            text(R.string.doctor_detail_compaction_enabled_zero, thresholdDesc)
                        else ->
                            text(R.string.doctor_detail_compaction_enabled_count, thresholdDesc, count)
                    },
                    severity = Severity.INFO,
                )
            )
        }.onFailure {
            add(
                DoctorCheck(
                    id = "diag.compaction",
                    category = DoctorCategory.Diagnostics,
                    label = text(R.string.doctor_check_compaction),
                    detail = text(R.string.doctor_detail_probe_failed, it::class.simpleName ?: "?", it.message ?: "?"),
                    severity = Severity.WARN,
                )
            )
        }
    }
}
