package me.rerere.zeroagent.data.local

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.ModelInstall
import java.util.concurrent.TimeUnit

/** A unique destination owns one work item; leaving Settings does not own its lifetime. */
class LocalModelDownloads(context: Context) {
    private val saved = context.applicationContext.getSharedPreferences("local-download-requests", Context.MODE_PRIVATE)
    private val manager = WorkManager.getInstance(context.applicationContext)

    internal enum class DownloadRestriction(val key: String) {
        UNMETERED("unmetered"), BATTERY_NOT_LOW("battery_not_low"), CHARGING("charging"),
    }

    internal fun restrictions(): Set<DownloadRestriction> =
        DownloadRestriction.entries.filterTo(mutableSetOf()) { saved.getBoolean(it.key, false) }

    internal fun setRestriction(restriction: DownloadRestriction, enabled: Boolean) {
        saved.edit().putBoolean(restriction.key, enabled).apply()
    }

    fun observe(runtime: LocalRuntime) = manager.getWorkInfosForUniqueWorkFlow(tag(runtime)).map { infos ->
        val current = saved.getString("id:${tag(runtime)}", null)
        infos.filter { it.id.toString() == current || !it.state.isFinished }
    }

    suspend fun enqueue(runtime: LocalRuntime, url: String, size: Long? = null, sha256: String? = null) = withContext(Dispatchers.IO) {
        if (manager.getWorkInfosForUniqueWork(tag(runtime)).get().any { !it.state.isFinished }) return@withContext
        val file = ModelInstall.extractFileNameFromUrl(url)
        val limits = restrictions()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (DownloadRestriction.UNMETERED in limits) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(DownloadRestriction.BATTERY_NOT_LOW in limits)
            .setRequiresCharging(DownloadRestriction.CHARGING in limits)
            .build()
        val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
            .setInputData(workDataOf("runtime" to runtime.fileExtension, "url" to url,
                "file" to file, "size" to (size ?: -1L), "sha256" to sha256))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(tag(runtime))
            .build()
        val persisted = saved.edit().putString(tag(runtime), JSONObject().put("url", url).put("size", size ?: -1L)
            .put("sha256", sha256).toString()).putString("id:${tag(runtime)}", request.id.toString()).commit()
        if (!persisted) throw java.io.IOException("Could not save the download request")
        manager.enqueueUniqueWork(tag(runtime), ExistingWorkPolicy.KEEP, request)
    }

    suspend fun resume(runtime: LocalRuntime) {
        val data = saved.getString(tag(runtime), null)?.let { JSONObject(it) } ?: return
        enqueue(runtime, data.getString("url"), data.optLong("size", -1).takeIf { it > 0 },
            data.optString("sha256").takeIf { it.isNotBlank() })
    }

    fun cancel(runtime: LocalRuntime) { manager.cancelAllWorkByTag(tag(runtime)) }

    companion object {
        fun tag(runtime: LocalRuntime) = "local-model-download:${runtime.fileExtension}"
    }
}
