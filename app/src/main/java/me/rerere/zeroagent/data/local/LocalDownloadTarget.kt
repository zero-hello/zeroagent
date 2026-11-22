package me.rerere.zeroagent.data.local

import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.ModelInstall
import java.io.File
import java.security.MessageDigest

internal fun localDownloadTarget(
    baseDirectory: File,
    runtime: LocalRuntime,
    fileName: String,
    url: String,
    expectedHash: String?,
    expectedSize: Long?,
): File {
    require(fileName.endsWith(".${runtime.fileExtension}", ignoreCase = true)) {
        "Incorrect model file extension"
    }
    val legacyTarget = ModelInstall.targetFile(baseDirectory, runtime, fileName)
    val stem = fileName.substringBeforeLast('.').take(36)
    fun File.isSameInstall() = ModelInstall.isInstalledDownload(this, url, expectedHash) &&
        (expectedSize == null || length() == expectedSize)

    // Keep previously installed files in place when their download stamp confirms the source.
    if (legacyTarget.isSameInstall()) return legacyTarget
    val oldDigest = downloadDigest(url + "|" + expectedHash.orEmpty(), 10)
    val previousTarget = ModelInstall.targetFile(
        baseDirectory, runtime, "${fileName.substringBeforeLast('.').take(160)}-$oldDigest.${runtime.fileExtension}",
    )
    if (previousTarget.isSameInstall()) return previousTarget

    // Choose a destination before any partial files exist. Locks and resume stamps are per target.
    val source = ModelInstall.normalizeHuggingFaceUrl(url)
    val digest = downloadDigest("$source\n${expectedHash.orEmpty().lowercase()}\n${expectedSize ?: ""}", 16)
    return ModelInstall.targetFile(baseDirectory, runtime, "$stem-$digest.${runtime.fileExtension}")
}

private fun downloadDigest(source: String, bytes: Int): String = MessageDigest.getInstance("SHA-256")
    .digest(source.toByteArray(Charsets.UTF_8))
    .take(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }
