package me.rerere.zeroagent.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.LITERT_PROVIDER_ID
import me.rerere.ai.provider.LLAMACPP_PROVIDER_ID
import me.rerere.ai.provider.Model
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.ModelInstall
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.SettingsStore
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException

class LocalModelDownloadWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {
    private val client: OkHttpClient by inject()
    private val preferences: LocalRuntimePreferences by inject()
    private val settings: SettingsStore by inject()
    private val notificationId = id.hashCode() and Int.MAX_VALUE

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val runtime = ModelInstall.runtimeForExtension(inputData.getString("runtime").orEmpty())
            ?: return@withContext Result.failure()
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val requestedFile = inputData.getString("file") ?: return@withContext Result.failure()
        try {
            val baseDir = ModelInstall.localModelsDir(applicationContext)
            val expectedHash = inputData.getString("sha256")
            val expectedSize = inputData.getLong("size", -1L).takeIf { it > 0 }
            val target = localDownloadTarget(baseDir, runtime, requestedFile, url, expectedHash, expectedSize)
            val file = target.name
            setForeground(foreground(0, file))
            var failure: Throwable? = null
            var done = false
            // Handles a process kill between atomic installation and provider registration.
            require(file.endsWith(".${runtime.fileExtension}", true)) { "Incorrect model file extension" }
            if (ModelInstall.isInstalledDownload(target, url, expectedHash) &&
                (expectedSize == null || target.length() == expectedSize)
            ) {
                register(runtime, file, requestedFile, target.absolutePath)
                done = true
            } else {
                ModelInstall.download(client, url, target, expectedSize, expectedHash)
                    .collect { event ->
                        when (event) {
                            is ModelInstall.Progress.Started -> progress(if (event.totalBytes != null) 0 else -1, 0, event.totalBytes, file)
                            is ModelInstall.Progress.Tick -> {
                                val percent = event.totalBytes?.takeIf { it > 0 }
                                    ?.let { ((event.bytesRead.toDouble() / it) * 100).toInt().coerceIn(0, 99) } ?: -1
                                progress(percent, event.bytesRead, event.totalBytes, file)
                            }
                            is ModelInstall.Progress.Done -> {
                                register(runtime, file, requestedFile, event.file.absolutePath)
                                done = true
                            }
                            is ModelInstall.Progress.Failed -> failure = event.cause
                        }
                    }
            }
            when {
                done -> Result.success(workDataOf("file" to file))
                failure is IOException && runAttemptCount < 5 -> Result.retry()
                else -> Result.failure(workDataOf("error" to (failure?.message ?: "Download failed"), "file" to file))
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) {
            if (error is IOException && runAttemptCount < 5) return@withContext Result.retry()
            Result.failure(workDataOf("error" to (error.message ?: error.javaClass.simpleName), "file" to requestedFile))
        }
    }

    private suspend fun register(runtime: LocalRuntime, file: String, sourceName: String, path: String) {
        preferences.addInstalledModel(runtime, file, path)
        val capabilities = deriveLocalModelCapabilities(runtime, sourceName)
        val model = Model(modelId = file, displayName = file,
            inputModalities = capabilities.inputModalities, abilities = capabilities.abilities)
        val providerId = if (runtime == LocalRuntime.LiteRT) LITERT_PROVIDER_ID else LLAMACPP_PROVIDER_ID
        settings.update { state ->
            state.copy(providers = state.providers.map { provider ->
                if (provider.id == providerId) enableAfterFirstDownload(registerInstalledModel(provider, model)) else provider
            })
        }
    }

    private suspend fun progress(percent: Int, bytes: Long, total: Long?, file: String) {
        setProgress(workDataOf("percent" to percent, "bytes" to bytes, "total" to (total ?: -1L), "file" to file))
        setForeground(foreground(percent, file))
    }

    private fun foreground(percent: Int, file: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            applicationContext.getString(R.string.local_v11_download_channel), NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.local_v11_download_title))
            .setContentText(if (percent >= 0) "$file · $percent%" else file)
            .setOnlyAlertOnce(true).setOngoing(true)
            .apply {
                applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let { intent ->
                    setContentIntent(PendingIntent.getActivity(applicationContext, notificationId, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .addAction(android.R.drawable.ic_media_pause,
                applicationContext.getString(R.string.local_v11_pause),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object { private const val CHANNEL = "local-model-downloads" }
}
