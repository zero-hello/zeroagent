package me.rerere.zeroagent.utils

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.DownloadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.http.await
import me.rerere.zeroagent.BuildConfig
import me.rerere.zeroagent.reliability.GitHubReleaseChecker
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateChecker(
    private val client: OkHttpClient,
    @Suppress("UNUSED_PARAMETER") appScope: CoroutineScope,
    private val apiUrl: String = BuildConfig.UPDATE_API_URL,
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    // Fallback source used when no self-hosted [apiUrl] is configured — this fork feeds the
    // card straight off GitHub Releases. Null keeps the pre-existing "report current version"
    // behaviour (card stays hidden); tests construct it that way.
    private val githubReleases: GitHubReleaseChecker? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val state = MutableStateFlow<UiState<UpdateInfo>>(UiState.Loading)
    val updateState: StateFlow<UiState<UpdateInfo>> = state.asStateFlow()
    private val refreshMutex = Mutex()
    private var lastCheck = -1L

    suspend fun refresh() = refreshMutex.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastCheck >= 0 && now - lastCheck < 15 * 60 * 1000L) return@withLock
        try {
            state.value = UiState.Success(data = withContext(Dispatchers.IO) { fetchUpdateInfo() })
            lastCheck = now
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.value = UiState.Error(e)
            lastCheck = now
        }
    }

    /**
     * Preferred source is a self-hosted endpoint speaking upstream's [UpdateInfo] contract
     * (`BuildConfig.UPDATE_API_URL`). This fork ships that field blank, so we fall back to
     * GitHub Releases — where the APKs are actually published — and synthesise the same
     * shape (see [GitHubReleaseChecker.fetchUpdateInfo]). With neither available we report
     * the installed version, which keeps the card hidden.
     */
    internal suspend fun fetchUpdateInfo(): UpdateInfo {
        if (apiUrl.isNotBlank()) {
            val response = client.newCall(
                Request.Builder()
                    .url(apiUrl)
                    .get()
                    .addHeader(
                        "User-Agent",
                        "ZeroAgent $currentVersionName #${BuildConfig.VERSION_CODE}"
                    )
                    .build()
            ).await()
            return response.use {
                check(it.isSuccessful) { "Failed to fetch update info" }
                json.decodeFromString<UpdateInfo>(it.body.string())
            }
        }
        githubReleases?.takeIf { it.isConfigured }?.let { return it.fetchUpdateInfo() }
        return UpdateInfo(
            version = currentVersionName,
            publishedAt = "",
            changelog = "",
            downloads = emptyList()
        )
    }

    private val downloadMutex = Mutex()
    private val downloadStatus = MutableStateFlow<UpdateTransfer?>(null)
    internal val transfer = downloadStatus.asStateFlow()

    suspend fun downloadUpdate(context: Context, download: UpdateDownload) {
        if (!downloadMutex.tryLock()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "zeroagent-app-updates"
        val notificationId = 12013
        fun publish(progress: DownloadProgress, verifying: Boolean) {
            downloadStatus.value = UpdateTransfer(progress, verifying)
            val percent = progress.fraction?.let { (100 * it).toInt() }
            val detail = if (verifying) context.getString(R.string.transfer_verifying_update) else progress.label(context)
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_zeroagent)
                .setContentTitle(context.getString(R.string.update_card_downloading))
                .setContentText(detail)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, percent ?: 0, percent == null || verifying)
                .build()
            if (manager?.areNotificationsEnabled() == true) runCatching { manager.notify(notificationId, notification) }
        }
        try {
            manager?.createNotificationChannel(NotificationChannel(channel, context.getString(R.string.update_card_downloading), NotificationManager.IMPORTANCE_LOW))
            publish(DownloadProgress(0, download.sizeBytes), false)
            SignedUpdateInstaller(client).downloadAndRequestInstall(context, download, ::publish)
        } finally {
            manager?.cancel(notificationId)
            downloadStatus.value = null
            downloadMutex.unlock()
        }
    }

}

internal data class UpdateTransfer(val progress: DownloadProgress, val verifying: Boolean)

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>
)

/**
 * 版本号值类，封装版本号字符串并提供比较功能
 *
 * 支持完整的 SemVer 规范：MAJOR.MINOR.PATCH[-prerelease][+build]
 * - 预发布版本优先级低于正式版：1.0.0-alpha < 1.0.0
 * - 预发布标识符按段逐个比较：数字按数值比较，字符串按字典序比较
 * - 预发布标识符优先级：alpha < beta < rc（通过字典序自然满足）
 * - build metadata（+号后面的部分）不影响优先级比较
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {

    private fun parse(): ParsedVersion {
        // 去掉 build metadata（+号后面的部分）
        val withoutBuild = value.split("+").first()
        // 分离主版本号和预发布标识符
        val hyphenIndex = withoutBuild.indexOf('-')
        val (coreStr, prereleaseStr) = if (hyphenIndex >= 0) {
            withoutBuild.substring(0, hyphenIndex) to withoutBuild.substring(hyphenIndex + 1)
        } else {
            withoutBuild to null
        }
        val core = coreStr.split(".").map { it.toIntOrNull() ?: 0 }
        val prerelease = prereleaseStr?.split(".")
        return ParsedVersion(core, prerelease)
    }

    override fun compareTo(other: Version): Int {
        val a = this.parse()
        val b = other.parse()

        // 先比较主版本号
        val maxLen = maxOf(a.core.size, b.core.size)
        for (i in 0 until maxLen) {
            val ap = if (i < a.core.size) a.core[i] else 0
            val bp = if (i < b.core.size) b.core[i] else 0
            if (ap != bp) return ap.compareTo(bp)
        }

        // 主版本号相同时比较预发布标识符
        // 有预发布标识符的版本优先级低于没有的：1.0.0-alpha < 1.0.0
        return when {
            a.prerelease == null && b.prerelease == null -> 0
            a.prerelease != null && b.prerelease == null -> -1
            a.prerelease == null && b.prerelease != null -> 1
            else -> comparePrerelease(a.prerelease!!, b.prerelease!!)
        }
    }

    companion object {
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            val maxLen = maxOf(a.size, b.size)
            for (i in 0 until maxLen) {
                // 字段少的优先级更低：1.0.0-alpha < 1.0.0-alpha.1
                if (i >= a.size) return -1
                if (i >= b.size) return 1

                val aNum = a[i].toIntOrNull()
                val bNum = b[i].toIntOrNull()

                val cmp = when {
                    // 都是字：按数值比较
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    // 数字优先级低于字符串
                    aNum != null -> -1
                    bNum != null -> 1
                    // 都是字符串：按字典序比较
                    else -> a[i].compareTo(b[i])
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<String>?,
)

// 扩展操作符函数，使比较更直观
operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))
