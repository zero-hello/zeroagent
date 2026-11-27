package me.rerere.zeroagent.ui.pages.setting.doctor

import android.content.Context
import kotlinx.coroutines.flow.first
import me.rerere.zeroagent.data.ai.tools.local.AgentWorkspace
import me.rerere.zeroagent.data.db.AppDatabase
import java.io.File
import me.rerere.zeroagent.R

internal class DoctorStorageChecker(
    context: Context,
    private val database: AppDatabase,
    private val localRuntimePreferences: me.rerere.locallm.LocalRuntimePreferences?,
) : DoctorCheckResources(context) {
    suspend fun localModelChecks(): List<DoctorCheck> = buildList {
        // LiteRT accelerator status. The runtime's GPU -> CPU fallback is silent today:
        // if the device's OpenCL/OpenGL delegate fails to init (e.g. MLDrift's
        // "CreateSharedMemoryManager is not implemented" on some Adreno drivers), the
        // model loads on CPU and the user has no UI indication. LiteRtProvider now
        // persists the actually-chosen accelerator after every load; surface that here
        // so the user can confirm GPU is engaged.
        runCatching {
            val prefs = localRuntimePreferences
            if (prefs != null) {
                val accel = prefs.acceleratorFlow(me.rerere.locallm.LocalRuntime.LiteRT).first()
                val forceCpu = prefs.forceCpu(me.rerere.locallm.LocalRuntime.LiteRT)
                val detail = when {
                    accel == null -> text(R.string.doctor_detail_accel_not_probed)
                    forceCpu && accel == "CPU" ->
                        text(R.string.doctor_detail_accel_cpu_full)
                    accel == "CPU" ->
                        text(R.string.doctor_detail_accel_cpu_fallback_full)
                    accel == "GPU" -> text(R.string.doctor_detail_accel_gpu_full)
                    accel == "QNN" || accel == "NPU" -> text(R.string.doctor_detail_accel_npu_full)
                    accel == "NNAPI" -> text(R.string.doctor_detail_accel_nnapi)
                    else -> text(R.string.doctor_detail_accel_unknown, accel)
                }
                val severity = when {
                    accel == null -> Severity.INFO
                    accel == "CPU" && !forceCpu -> Severity.WARN  // unexpected fallback
                    else -> Severity.OK
                }
                add(
                    DoctorCheck(
                        id = "net.litert_accel",
                        category = DoctorCategory.Network,
                        label = text(R.string.doctor_check_litert_accel),
                        detail = detail,
                        severity = severity,
                        fix = FixAction.OpenAppRoute(
                            text(R.string.doctor_fix_open_local_litert),
                            AppRouteKey.SettingProvider,
                        ),
                    )
                )
                // Performance telemetry — surface the last-known prefill/decode tok/s for
                // each model so the user (and the support team triaging a slow report)
                // can see at a glance whether the runtime is hitting expected rates. We
                // INFO when present; WARN never (the model could legitimately be slow on a
                // weak device — the user knows their hardware better than we do).
                val perfMap = prefs.perfTelemetryFlow(me.rerere.locallm.LocalRuntime.LiteRT).first()
                if (perfMap.isNotEmpty()) {
                    val rows = perfMap.values.sortedByDescending { it.sampledAtMs }
                    val detail = rows.joinToString("\n") { s ->
                        val spec = if (s.specDecodingEngaged) text(R.string.doctor_detail_mtp_on) else ""
                        text(R.string.doctor_detail_model_rates_row, s.modelId, "%.1f".format(s.prefillTps).toFloat(), "%.1f".format(s.decodeTps).toFloat(), spec)
                    }
                    add(
                        DoctorCheck(
                            id = "net.litert_perf",
                            category = DoctorCategory.Network,
                            label = text(R.string.doctor_check_litert_perf),
                            detail = text(R.string.doctor_detail_model_rates_full, detail),
                            severity = Severity.INFO,
                            fix = FixAction.OpenAppRoute(
                                text(R.string.doctor_fix_open_local_litert),
                                AppRouteKey.SettingProvider,
                            ),
                        )
                    )
                }
                // Vision-encoder availability — surface any models the runtime had to drop
                // to text-only on this device's GPU. The provider's vision-CPU fallback
                // means a multimodal model still works for chat, but the user has lost
                // image input on this chip. Most common cause: Adreno 7xx + restrictive
                // OEM linker namespace (One UI / OriginOS) hitting upstream LiteRT-LM
                // issue #2292 (gpu_backend_opengl.cc:CreateSharedMemoryManager UNIMPLEMENTED).
                val visionUnavailable = prefs
                    .visionUnavailableFlow(me.rerere.locallm.LocalRuntime.LiteRT).first()
                if (visionUnavailable.isNotEmpty()) {
                    add(
                        DoctorCheck(
                            id = "net.litert_vision",
                            category = DoctorCategory.Network,
                            label = text(R.string.doctor_check_litert_vision),
                            detail = text(R.string.doctor_detail_vision_unavailable_full, visionUnavailable.joinToString(", ")),
                            severity = Severity.WARN,
                            fix = FixAction.OpenAppRoute(
                                text(R.string.doctor_fix_open_local_litert),
                                AppRouteKey.SettingProvider,
                            ),
                        )
                    )
                }
            }
        }
        // llama.cpp installed-model check. Unlike LiteRT, this runtime is CPU-only with no
        // vision encoder and nothing to probe for an accelerator, so there is no analogue
        // to net.litert_accel/_perf/_vision here — those would be reporting on things that
        // cannot vary on this build. The one thing that genuinely can go wrong: a model
        // registered in prefs whose backing file was moved, deleted, or lives on a volume
        // that got unmounted. Own id (net.llamacpp_models) so it can't collide with the
        // net.litert_* rows above.
        runCatching {
            val prefs = localRuntimePreferences
            if (prefs != null) {
                val installed = prefs.installedModels(me.rerere.locallm.LocalRuntime.LlamaCpp)
                val status = llamaCppModelStatus(installed)
                val detail = when {
                    status.total == 0 -> text(R.string.doctor_detail_no_llama_models)
                    status.missing.isEmpty() ->
                        "${status.total} model(s) installed, all present on disk."
                    else ->
                        text(R.string.doctor_detail_llamacpp_missing, status.missing.size, status.total, status.missing.joinToString(", "))
                }
                add(
                    DoctorCheck(
                        id = "net.llamacpp_models",
                        category = DoctorCategory.Network,
                        label = text(R.string.doctor_check_llamacpp_models),
                        detail = detail,
                        severity = when {
                            status.total == 0 -> Severity.INFO
                            status.missing.isEmpty() -> Severity.OK
                            else -> Severity.WARN
                        },
                        fix = if (status.missing.isNotEmpty()) FixAction.OpenAppRoute(
                            text(R.string.doctor_fix_open_local_llama),
                            AppRouteKey.SettingProvider,
                        ) else null,
                    )
                )
            }
        }
    }

    // ----- Storage (gallery + workspace) -----------------------------------------------------

    /**
     * Doctor refresh: two read-only storage rows.
     *  - `storage.gallery_orphans`: the #39 bug class made visible, generated-image DB
     *    records ([me.rerere.zeroagent.data.db.entity.GenMediaEntity]) whose backing file
     *    under `filesDir/images/` no longer exists.
     *  - `storage.workspace`: health of the agent's `~` sandbox ([AgentWorkspace]), exists,
     *    is a directory, is writable, plus file count and size via the existing
     *    [directorySize] / [humanBytes] helpers.
     */
    suspend fun storageChecks(): List<DoctorCheck> = buildList {
        runCatching {
            val entities = database.genMediaDao().getAllMedia()
            val absolutePaths = entities.map { File(context.filesDir, it.path).absolutePath }
            val status = galleryOrphanStatus(absolutePaths)
            add(
                DoctorCheck(
                    id = "storage.gallery_orphans",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_gallery_orphans),
                    detail = if (status.orphanCount == 0)
                        text(R.string.doctor_detail_images_all_ok, status.total)
                    else
                        text(R.string.doctor_detail_images_orphan, status.orphanCount, status.total),
                    severity = if (status.orphanCount > 0) Severity.WARN else Severity.OK,
                )
            )
        }.onFailure {
            add(
                DoctorCheck(
                    id = "storage.gallery_orphans",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_gallery_orphans),
                    detail = text(R.string.doctor_detail_probe_failed, it::class.simpleName ?: "?", it.message ?: "?"),
                    severity = Severity.WARN,
                )
            )
        }

        runCatching {
            val root = File(AgentWorkspace.rootPath())
            val exists = root.exists() && root.isDirectory
            val writable = exists && root.canWrite()
            add(
                DoctorCheck(
                    id = "storage.workspace",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_workspace),
                    detail = when {
                        !exists -> "${root.absolutePath} does not exist."
                        !writable -> "${root.absolutePath} exists but is not writable."
                        else -> {
                            val fileCount = root.walkTopDown().count { it.isFile }
                            "${root.absolutePath}: $fileCount file(s), ${humanBytes(directorySize(root))}."
                        }
                    },
                    severity = if (!exists || !writable) Severity.WARN else Severity.INFO,
                )
            )
        }.onFailure {
            add(
                DoctorCheck(
                    id = "storage.workspace",
                    category = DoctorCategory.Database,
                    label = text(R.string.doctor_check_workspace),
                    detail = text(R.string.doctor_detail_probe_failed, it::class.simpleName ?: "?", it.message ?: "?"),
                    severity = Severity.WARN,
                )
            )
        }
    }

    // ----- Maintenance -----------------------------------------------------------------

    fun maintenanceChecks(): List<DoctorCheck> = buildList {
        // Cache size on disk
        val cacheBytes = directorySize(context.cacheDir)
        add(
            DoctorCheck(
                id = "maint.cache_size",
                category = DoctorCategory.Maintenance,
                label = text(R.string.doctor_check_cache_size),
                detail = text(R.string.doctor_detail_cache_using, humanBytes(cacheBytes)) +
                    if (cacheBytes > 200L * 1024 * 1024) text(R.string.doctor_detail_cache_clear_hint) else text(R.string.doctor_detail_cache_normal),
                severity = if (cacheBytes > 500L * 1024 * 1024) Severity.WARN else Severity.OK,
                fix = FixAction.AutoFix(
                    label = text(R.string.doctor_fix_clear_cache),
                    run = {
                        val freed = clearDirectoryContents(context.cacheDir)
                        AutoFixResult(ok = true, message = text(R.string.doctor_fix_cache_freed, humanBytes(freed)))
                    },
                ),
            )
        )
    }

    private fun directorySize(dir: File): Long = runCatching {
        if (!dir.exists()) return@runCatching 0L
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    private fun clearDirectoryContents(dir: File): Long {
        var freed = 0L
        runCatching {
            dir.listFiles()?.forEach { f ->
                freed += directorySize(f)
                f.deleteRecursively()
            }
        }
        return freed
    }

    private fun humanBytes(bytes: Long): String {
        val mb = 1024.0 * 1024
        val gb = mb * 1024
        return when {
            bytes < mb -> "%.0f KB".format(bytes / 1024.0)
            bytes < gb -> "%.1f MB".format(bytes / mb)
            else -> "%.2f GB".format(bytes / gb)
        }
    }
}

/**
 * Pure decision logic backing the "net.llamacpp_models" row: given the filename ->
 * absolute-path map from [me.rerere.locallm.LocalRuntimePreferences.installedModels],
 * report the total installed count and which filenames' backing file is no longer on
 * disk. Extracted to a top-level function (rather than left inline) so it's unit-testable
 * on the JVM without an Android Context — [DoctorChecks] itself needs one for every other
 * check, which rules out constructing it directly in a plain JUnit test.
 */
internal data class LlamaCppModelStatus(val total: Int, val missing: List<String>)

internal fun llamaCppModelStatus(installed: Map<String, String>): LlamaCppModelStatus =
    LlamaCppModelStatus(
        total = installed.size,
        missing = installed.filterValues { path -> !File(path).exists() }.keys.sorted(),
    )

/**
 * Pure decision logic backing the "storage.gallery_orphans" row: given the resolved
 * absolute paths of every generated-image DB record, report the total and how many no
 * longer have a backing file on disk (the #39 bug class). Mirrors [llamaCppModelStatus]'s
 * shape so both are unit-testable on the JVM without a Context.
 */
internal data class GalleryOrphanStatus(val total: Int, val orphanCount: Int)

internal fun galleryOrphanStatus(absolutePaths: List<String>): GalleryOrphanStatus =
    GalleryOrphanStatus(
        total = absolutePaths.size,
        orphanCount = absolutePaths.count { path -> !File(path).exists() },
    )
