package me.rerere.zeroagent.data.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.rerere.locallm.ModelInstall
import me.rerere.zeroagent.R
import me.rerere.zeroagent.RouteActivity
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Unmodified F-Droid stable build; standard Termux owns its bootstrap, packages and processes. */
object TermuxInstaller {
    const val VERSION = "0.118.3"
    const val VERSION_CODE = 1002L
    const val BYTES = 113880067L
    const val SHA256 = "e6265a57eb5ca363808488e3b01955958bed93bc0c8a0d281849b363b11027ec"
    const val URL = "https://f-droid.org/repo/com.termux_1002.apk"
    const val WORK = "zeroagent-termux-installer"
    fun target(context: Context) = File(context.cacheDir, "termux-installer/termux-$VERSION.apk")
    fun download(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
        WORK, ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<TermuxInstallerWorker>()
            .setInputData(workDataOf("requested_at" to System.currentTimeMillis()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
    )

    suspend fun validatedFile(context: Context): File = withContext(Dispatchers.IO) {
        val file = target(context)
        require(file.isFile && file.length() == BYTES) { "corrupt_termux_apk" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == SHA256) { "corrupt_termux_apk" }
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        require(info != null && info.packageName == "com.termux" && info.longVersionCode == VERSION_CODE && info.versionName == VERSION) {
            "corrupt_termux_apk"
        }
        file
    }

    fun installIntent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
            "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

class TermuxInstallerWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {
    private val client: OkHttpClient by inject()
    private val noticeId = id.hashCode() and Int.MAX_VALUE
    private var lastPercent = -1
    private val requestedAt get() = inputData.getLong("requested_at", 0L)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(foreground(0))
            val target = TermuxInstaller.target(applicationContext)
            var complete = false
            var failure: Throwable? = null
            if (target.isFile) {
                try { TermuxInstaller.validatedFile(applicationContext); complete = true
                } catch (cancel: CancellationException) { throw cancel
                } catch (_: IllegalArgumentException) { ModelInstall.deleteModelFiles(target) }
            }
            if (!complete) {
                val publicClient = client.newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }
                    .cache(null).followSslRedirects(false).build()
                ModelInstall.download(publicClient, TermuxInstaller.URL, target, TermuxInstaller.BYTES, TermuxInstaller.SHA256)
                    .collect { event ->
                        when (event) {
                            is ModelInstall.Progress.Started -> update(0)
                            is ModelInstall.Progress.Tick -> update(((event.bytesRead.toDouble() / TermuxInstaller.BYTES) * 100).toInt().coerceIn(0, 99))
                            is ModelInstall.Progress.Done -> { TermuxInstaller.validatedFile(applicationContext); complete = true }
                            is ModelInstall.Progress.Failed -> failure = event.cause
                        }
                    }
            }
            when {
                complete -> { update(100); readyNotice(); Result.success(workDataOf("ready" to true, "requested_at" to requestedAt)) }
                failure is IOException && runAttemptCount < 3 -> Result.retry()
                else -> Result.failure(workDataOf("error" to (failure?.message ?: "corrupt_termux_apk"), "requested_at" to requestedAt))
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: Exception) {
            if (failure is IOException && runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("error" to failure.message.orEmpty(), "requested_at" to requestedAt))
        }
    }

    private suspend fun update(percent: Int) {
        if (percent == lastPercent) return
        lastPercent = percent
        setProgress(workDataOf("percent" to percent, "requested_at" to requestedAt))
        setForeground(foreground(percent))
    }
    private fun notification(percent: Int, ready: Boolean = false): android.app.Notification {
        val ctx = applicationContext.zeroAgentLocalizedContext()
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.termux_setup_download_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, noticeId,
            Intent(ctx, RouteActivity::class.java).putExtra("open_termux_settings", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(ctx.getString(if (ready) R.string.termux_setup_download_ready else R.string.termux_setup_downloading))
            .setContentText(if (ready) ctx.getString(R.string.termux_setup_install_hint) else "$percent%")
            .setContentIntent(open).setOnlyAlertOnce(true).setSilent(true)
            .setOngoing(!ready).setAutoCancel(ready)
            .apply {
                if (!ready) {
                    setProgress(100, percent, false)
                    addAction(android.R.drawable.ic_media_pause, ctx.getString(R.string.local_v11_pause),
                        WorkManager.getInstance(ctx).createCancelPendingIntent(id))
                }
            }.build()
    }
    private fun foreground(percent: Int) = ForegroundInfo(noticeId, notification(percent), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    private fun readyNotice() {
        if (android.os.Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            applicationContext.getSystemService(NotificationManager::class.java).notify(READY_ID, notification(100, ready = true))
    }
    companion object {
        private const val CHANNEL = "zeroagent-termux-installer"
        private const val READY_ID = 0x5A212
    }
}
