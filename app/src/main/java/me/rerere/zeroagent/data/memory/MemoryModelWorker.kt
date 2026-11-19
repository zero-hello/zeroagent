package me.rerere.zeroagent.data.memory

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import me.rerere.locallm.ModelInstall
import me.rerere.zeroagent.R
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException

/** Uses the same resumable, atomic, hash-checked installer as local chat models. */
class MemoryModelWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val client: OkHttpClient by inject()
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val manager = MemoryManager.get(applicationContext)
            setForeground(notification(0))
            if (manager.modelFile.isFile && manager.modelFile.length() == MemoryManager.MODEL_SIZE) {
                try {
                    manager.validateModel()
                    progress(100)
                    return@withContext Result.success()
                } catch (corrupt: IllegalArgumentException) {
                    // This owned, hash-invalid artifact may be replaced; memory/DB failures must not redownload it.
                    if (!manager.modelFile.delete()) throw IOException("Could not replace the corrupt memory model", corrupt)
                }
            }
            var failure: Throwable? = null
            var installed = false
            ModelInstall.download(client, MemoryManager.MODEL_URL, manager.modelFile, MemoryManager.MODEL_SIZE, MemoryManager.MODEL_SHA)
                .collect { event -> when (event) {
                    is ModelInstall.Progress.Started -> progress(0)
                    is ModelInstall.Progress.Tick -> progress(((event.bytesRead * 100 / MemoryManager.MODEL_SIZE).toInt()).coerceIn(0, 99))
                    is ModelInstall.Progress.Done -> { manager.validateModel(); installed = true; progress(100) }
                    is ModelInstall.Progress.Failed -> failure = event.cause
                } }
            when { installed -> Result.success(); failure is IOException && runAttemptCount < 5 -> Result.retry()
                else -> Result.failure(workDataOf("error" to (failure?.javaClass?.simpleName ?: "initialization_failed"))) }
        } catch (e: CancellationException) { throw e
        } catch (e: IOException) { if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (e: Exception) { Result.failure(workDataOf("error" to e.javaClass.simpleName)) }
    }
    private suspend fun progress(percent: Int) {
        setProgress(workDataOf("percent" to percent))
        setForeground(notification(percent))
    }
    private fun notification(percent: Int): ForegroundInfo {
        val textContext = applicationContext.zeroAgentLocalizedContext()
        val channel = "contextual-memory-download"
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, textContext.getString(R.string.memory12_title), NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, channel).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(textContext.getString(R.string.memory12_building)).setContentText("$percent%")
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(100, percent, false)
            .addAction(android.R.drawable.ic_media_pause, textContext.getString(R.string.local_v11_pause), WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return ForegroundInfo(12012, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
