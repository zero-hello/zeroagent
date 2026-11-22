package me.rerere.zeroagent.utils

import android.content.Context
import android.os.SystemClock
import me.rerere.zeroagent.ui.components.ui.DownloadProgress
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.BuildConfig
import me.rerere.zeroagent.R
import me.rerere.zeroagent.reliability.GitHubReleaseChecker
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class SignedUpdateInstaller(private val client: OkHttpClient) {
    suspend fun downloadAndRequestInstall(context: Context, asset: UpdateDownload, report: (DownloadProgress, Boolean) -> Unit) {
        val target = withContext(Dispatchers.IO) {
            val url = asset.url.toHttpUrl()
            val segments = url.pathSegments
            val officialPath = GitHubReleaseChecker.DEFAULT_REPO.split('/') + listOf("releases", "download")
            require(url.isHttps && url.host == "github.com" && segments.size == 6 &&
                segments.take(4) == officialPath && segments.last() == asset.name && isCompatible(asset)) {
                context.getString(R.string.update_invalid_apk)
            }
            val directory = File(context.cacheDir, "verified_updates").apply { mkdirs() }
            val temporary = File.createTempFile("download-", ".apk", directory)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                client.newCall(Request.Builder().url(url).build()).readUpdateResponse { response ->
                    check(response.isSuccessful) { context.getString(R.string.update_download_failed) }
                    val expected = response.body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes
                    var lastReport = 0L
                    report(DownloadProgress(0, expected), false)
                    response.body.byteStream().use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                ensureActive()
                                if (count < 0) break
                                total += count
                                check(total <= 250L * 1024 * 1024) { context.getString(R.string.update_invalid_apk) }
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastReport >= 200) {
                                    report(DownloadProgress(total, expected), false)
                                    lastReport = now
                                }
                            }
                            report(DownloadProgress(total, expected ?: total), true)
                        }
                    }
                }
                if (asset.sha256 != null) {
                    val checksum = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    check(checksum.equals(asset.sha256, ignoreCase = true)) { context.getString(R.string.update_invalid_apk) }
                }
                verifyIdentity(context, temporary)
                retainInstalledApk(context)
                val verified = File(directory, asset.name)
                check(temporary.renameTo(verified) || run {
                    temporary.copyTo(verified, overwrite = true)
                    true
                })
                verified
            } finally {
                temporary.delete()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun verifyIdentity(context: Context, file: File) {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val downloaded = pm.getPackageArchiveInfo(file.absolutePath, flags)
        val expected = installed.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        check(downloaded != null && downloaded.packageName == installed.packageName &&
            downloaded.longVersionCode > installed.longVersionCode && expected.isNotEmpty() &&
            downloaded.signingInfo?.apkContentsSigners?.toSet() == expected) {
            context.getString(R.string.update_invalid_apk)
        }
    }

    private fun retainInstalledApk(context: Context) {
        val app = context.applicationInfo
        if (!app.splitSourceDirs.isNullOrEmpty()) return
        val previous = File(context.filesDir, "previous-installation.apk")
        val temporary = File.createTempFile("previous-", ".apk", context.filesDir)
        try {
            File(app.sourceDir).copyTo(temporary, overwrite = true)
            android.system.Os.rename(temporary.absolutePath, previous.absolutePath)
            context.getSharedPreferences("version_notice", Context.MODE_PRIVATE).edit()
                .putString("previous_version", BuildConfig.VERSION_NAME).apply()
        } finally {
            temporary.delete()
        }
    }

    companion object {
        fun isCompatible(asset: UpdateDownload): Boolean = Build.SUPPORTED_ABIS.any { abi ->
            abi in setOf("arm64-v8a", "x86_64") && asset.name.endsWith("-$abi.apk") &&
                asset.name.matches(Regex("ZeroAgent-v[0-9]+\\.[0-9]+\\.[0-9]+-(arm64-v8a|x86_64)\\.apk"))
        }
    }
}

/** Bind cancellation through the blocking response reads, not just until headers arrive. */
internal suspend fun <T> Call.readUpdateResponse(read: (Response) -> T): T = coroutineScope {
    val cancelOnExit = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            this@readUpdateResponse.cancel()
        }
    }
    try {
        ensureActive()
        execute().use(read)
    } catch (error: IOException) {
        ensureActive()
        throw error
    } finally {
        cancelOnExit.cancel()
    }
}
