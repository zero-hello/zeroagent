package me.rerere.locallm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.buffer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

private const val TAG = "ModelInstall"

/** Streams model downloads and imports to private storage; only validated files replace installed models. */
object ModelInstall {

    private const val LOCAL_MODELS_DIRNAME = "local-models"

    /**
     * Progress events emitted by [download]. Terminal events are [Done] and [Failed].
     */
    sealed class Progress {
        data class Started(val totalBytes: Long?) : Progress()
        data class Tick(val bytesRead: Long, val totalBytes: Long?) : Progress()
        data class Done(val file: File) : Progress()
        data class Failed(val cause: Throwable) : Progress()
    }

    /**
     * Normalise a HuggingFace URL so that both the viewer (blob) form and the direct
     * download (resolve) form work.
     *
     * HuggingFace "view file" URLs look like:
     *   https://huggingface.co/<user>/<repo>/blob/main/<file>
     * The actual download URL is:
     *   https://huggingface.co/<user>/<repo>/resolve/main/<file>
     *
     * Pasting the viewer URL into the manual-install field would previously succeed
     * HTTP-wise (200 OK) but return an HTML page, not the model binary. This transform
     * converts blob → resolve for any huggingface.co path. Non-HF URLs are returned
     * unchanged.
     */
    fun normalizeHuggingFaceUrl(url: String): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (!host.equals("huggingface.co", true)) return url
        return url.replace(Regex("/blob/([^/]+)/")) { m -> "/resolve/${m.groupValues[1]}/" }
    }

    fun isValidDownloadUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return runCatching {
            val parsed = java.net.URI(url)
            parsed.scheme == "https" && parsed.host?.isNotBlank() == true
        }.getOrDefault(false)
    }

    fun runtimeForExtension(extension: String): LocalRuntime? = when (extension.lowercase()) {
        "litertlm" -> LocalRuntime.LiteRT
        "gguf" -> LocalRuntime.LlamaCpp
        else -> null
    }

    fun extractFileNameFromUrl(url: String): String {
        val rawPath = java.net.URI(url).rawPath.orEmpty()
        return java.net.URLDecoder.decode(rawPath.substringAfterLast('/').replace("+", "%2B"), "UTF-8")
    }

    fun targetFile(baseDir: File, runtime: LocalRuntime, fileName: String): File {
        val sub = when (runtime) {
            LocalRuntime.LiteRT -> "litert"
            LocalRuntime.LlamaCpp -> "llamacpp"
        }
        require(fileName.isNotBlank() && fileName != "." && fileName != ".." && !fileName.contains('/') && !fileName.contains('\\') && !fileName.contains('\u0000')) { "Invalid model file name" }
        return File(File(baseDir, sub), fileName)
    }

    fun localModelsDir(context: Context): File =
        File(context.filesDir, LOCAL_MODELS_DIRNAME).apply { mkdirs() }

    /**
     * Returns true if the first [count] bytes of [buf] look like an HTML document.
     * Used to abort downloads that succeeded HTTP-wise but returned an error page.
     */
    fun looksLikeHtml(buf: ByteArray, count: Int): Boolean {
        val sample = String(buf, 0, count.coerceAtMost(256), Charsets.UTF_8)
            .trimStart()
            .lowercase()
        return sample.startsWith("<!doctype") ||
               sample.startsWith("<html") ||
               sample.startsWith("<head") ||
               sample.startsWith("<body") ||
               sample.startsWith("<?xml")
    }

    /**
     * Returns true if [firstBytes] looks like a valid file of the type implied by
     * [extension]. Used as a post-download integrity check to refuse files that
     * downloaded successfully but whose contents are wrong (sparse-fill, server
     * misroute, partial corruption, etc.).
     */
    fun isValidMagicForExtension(extension: String, firstBytes: ByteArray): Boolean {
        if (firstBytes.size < 4) return false
        return when (extension.lowercase()) {
            "litertlm", "tflite", "task" -> {
                // LiteRT-LM files start with ASCII "LITERTLM"
                // (0x4c 0x49 0x54 0x45 0x52 0x54 0x4c 0x4d).
                // .tflite / .task files start with TFL3 (0x54 0x46 0x4c 0x33) at offset 4 in the
                // FlatBuffer header (offset 0..3 is the size prefix). The simplest check: first
                // 8 bytes are "LITERTLM" OR bytes 4..7 are "TFL3". Reject all-zero, all-FF,
                // or HTML-like first bytes.
                val isLitertlm = firstBytes.size >= 8 &&
                    firstBytes[0] == 0x4c.toByte() && firstBytes[1] == 0x49.toByte() &&
                    firstBytes[2] == 0x54.toByte() && firstBytes[3] == 0x45.toByte() &&
                    firstBytes[4] == 0x52.toByte() && firstBytes[5] == 0x54.toByte() &&
                    firstBytes[6] == 0x4c.toByte() && firstBytes[7] == 0x4d.toByte()
                val isTflite = firstBytes.size >= 8 &&
                    firstBytes[4] == 0x54.toByte() && firstBytes[5] == 0x46.toByte() &&
                    firstBytes[6] == 0x4c.toByte() && firstBytes[7] == 0x33.toByte()
                isLitertlm || isTflite
            }
            "gguf" -> {
                // GGUF files start with the ASCII magic "GGUF"
                // (0x47 0x47 0x55 0x46). A truncated download or an HTML error page
                // served in its place falls through to this arm without it and was
                // previously accepted as a model.
                firstBytes[0] == 0x47.toByte() && firstBytes[1] == 0x47.toByte() &&
                    firstBytes[2] == 0x55.toByte() && firstBytes[3] == 0x46.toByte()
            }
            else -> {
                // Unknown extension — accept by default but reject obvious zeros / HTML.
                !looksLikeHtml(firstBytes, firstBytes.size) && firstBytes.any { it != 0x00.toByte() }
            }
        }
    }

    fun deleteModelFiles(target: File) {
        target.parentFile?.mkdirs()
        java.io.RandomAccessFile(File(target.absolutePath + ".lock"), "rw").use { handle ->
            val lock = runCatching { handle.channel.tryLock() }.getOrNull()
                ?: error("Pause the active download before deleting this model")
            lock.use {
                if (target.exists() && !target.delete()) throw java.io.IOException("Could not delete model")
                listOf(".partial", ".partial.properties", ".download.properties", ".download.properties.tmp")
                    .forEach { File(target.absolutePath + it).delete() }
            }
        }
    }

    fun isInstalledDownload(target: File, url: String, expectedSha256: String? = null): Boolean = runCatching {
        val stamp = java.util.Properties().apply {
            File(target.absolutePath + ".download.properties").inputStream().use { load(it) }
        }
        target.isFile && stamp.getProperty("url") == normalizeHuggingFaceUrl(url) &&
            stamp.getProperty("size")?.toLongOrNull() == target.length() &&
            (expectedSha256 == null || expectedSha256.equals(stamp.getProperty("sha256"), true)) &&
            target.inputStream().use { input ->
                val bytes = ByteArray(16)
                val n = input.read(bytes)
                isValidMagicForExtension(target.extension, bytes.copyOf(n.coerceAtLeast(0)))
            }
    }.getOrDefault(false)

    /** A cancelled transfer keeps its validated partial and closes the socket immediately. */
    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    fun download(
        client: OkHttpClient,
        url: String,
        target: File,
        expectedSizeBytes: Long? = null,
        expectedSha256: String? = null,
    ): Flow<Progress> = flow {
        target.parentFile?.mkdirs()
        val partial = File(target.absolutePath + ".partial")
        val metadata = File(target.absolutePath + ".partial.properties")
        val normalized = normalizeHuggingFaceUrl(url)
        require(isValidDownloadUrl(normalized)) { "Invalid HTTPS model URL" }
        val lockFile = java.io.RandomAccessFile(File(target.absolutePath + ".lock"), "rw")
        lockFile.use { handle ->
            val lock = runCatching { handle.channel.tryLock() }.getOrNull()
            if (lock == null) {
                emit(Progress.Failed(IllegalStateException("A transfer already owns this model file")))
                return@flow
            }
            lock.use {
                try {
                    val saved = java.util.Properties().apply {
                        if (metadata.isFile) runCatching { metadata.inputStream().use { load(it) } }
                    }
                    var validator = saved.getProperty("validator")
                    if (saved.getProperty("url") != normalized || validator == null) {
                        partial.delete()
                        metadata.delete()
                        validator = null
                    }
                    var offset = partial.takeIf { it.isFile }?.length() ?: 0L
                    var response: okhttp3.Response? = null
                    var completePartial = false
                    // A stale partial (416) is discarded once; a second error is surfaced.
                    for (attempt in 0..1) {
                        val request = Request.Builder().url(normalized).header("Accept-Encoding", "identity").apply {
                            if (offset > 0 && validator != null) {
                                header("Range", "bytes=$offset-")
                                header("If-Range", validator)
                            }
                        }.build()
                        val call = client.newCall(request)
                        val cancellation = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                            ?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
                                if (cause != null) call.cancel()
                            }
                        try {
                            response = call.execute()
                        } finally {
                            // The body remains covered by the separate cancellation handler below.
                            cancellation?.dispose()
                        }
                        val serverLength = response.header("Content-Range")?.removePrefix("bytes */")?.toLongOrNull()
                        val responseValidator = HttpResume.validator(response.header("ETag"), response.header("Last-Modified"))
                        if (response.code == 416 && offset > 0 && serverLength == offset &&
                            validator != null && validator == responseValidator) {
                            completePartial = true
                            break
                        }
                        if (response.code != 416 || offset == 0L || attempt == 1) break
                        response.close()
                        partial.delete()
                        metadata.delete()
                        offset = 0L
                        validator = null
                    }
                    response!!.use { result ->
                        if (result.code == 429 || result.code >= 500) throw java.io.IOException("HTTP ${result.code}")
                        check(result.isSuccessful || completePartial) { "HTTP ${result.code}" }
                        val contentType = result.header("Content-Type").orEmpty()
                        check(completePartial || (!contentType.startsWith("text/html", true) && !contentType.startsWith("application/xhtml", true))) {
                            "Server returned an HTML page instead of a model"
                        }
                        val currentValidator = HttpResume.validator(result.header("ETag"), result.header("Last-Modified"))
                        val range = HttpResume.parseRange(result.header("Content-Range"))
                        val append = result.code == 206 || completePartial
                        if (append && !completePartial && !HttpResume.canAppend(offset, range, validator, currentValidator)) {
                            partial.delete()
                            metadata.delete()
                            error("Server returned a mismatched HTTP range or validator; resume was rejected")
                        }
                        if (!append) offset = 0
                        val total = if (completePartial) offset else if (append) range!!.total else result.header("Content-Length")?.toLongOrNull()
                            ?: result.body.contentLength().takeIf { it >= 0 }
                        if (expectedSizeBytes != null && total != null) check(total == expectedSizeBytes) { "Model size changed" }
                        java.util.Properties().apply {
                            setProperty("url", normalized)
                            currentValidator?.let { setProperty("validator", it) }
                            metadata.outputStream().use { store(it, null) }
                        }
                        emit(Progress.Started(total ?: expectedSizeBytes))
                        if (offset > 0) emit(Progress.Tick(offset, total ?: expectedSizeBytes))
                        var received = offset
                        var lastProgress = 0L
                        // Response.close interrupts blocking reads when WorkManager stops this worker.
                        val bodyCancellation = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                            ?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
                                if (cause != null) result.close()
                            }
                        try {
                            if (!completePartial) java.io.FileOutputStream(partial, append).use { sink ->
                                result.body.byteStream().use { input ->
                                    val buffer = ByteArray(128 * 1024)
                                    while (true) {
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        if (count == 0) continue
                                        sink.write(buffer, 0, count)
                                        received += count
                                        val now = System.nanoTime()
                                        if (now - lastProgress >= 500_000_000L) {
                                            emit(Progress.Tick(received, total ?: expectedSizeBytes))
                                            lastProgress = now
                                        }
                                    }
                                    sink.fd.sync()
                                }
                            }
                        } finally { bodyCancellation?.dispose() }
                        if ((total != null && received != total) || (expectedSizeBytes != null && received != expectedSizeBytes)) {
                            throw java.io.IOException("Incomplete model: $received / ${total ?: expectedSizeBytes} bytes")
                        }
                        val firstBytes = partial.inputStream().use { input ->
                            val prefix = ByteArray(16)
                            val count = input.read(prefix)
                            prefix.copyOf(count.coerceAtLeast(0))
                        }
                        if (!isValidMagicForExtension(target.extension, firstBytes)) {
                            partial.delete()
                            metadata.delete()
                            error("Invalid model file header")
                        }
                        if (expectedSha256 != null) {
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                            partial.inputStream().use { input ->
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    digest.update(buffer, 0, n)
                                }
                            }
                            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                            if (!hash.equals(expectedSha256, true)) {
                                partial.delete()
                                metadata.delete()
                                error("Model SHA-256 mismatch")
                            }
                        }
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        java.nio.file.Files.move(partial.toPath(), target.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        val installed = File(target.absolutePath + ".download.properties")
                        val stamp = File(installed.absolutePath + ".tmp")
                        java.util.Properties().apply {
                            setProperty("url", normalized)
                            setProperty("size", received.toString())
                            expectedSha256?.let { setProperty("sha256", it) }
                            stamp.outputStream().use { store(it, null) }
                        }
                        java.nio.file.Files.move(stamp.toPath(), installed.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        metadata.delete()
                        emit(Progress.Done(target))
                    }
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    emit(Progress.Failed(error))
                }
            }
        }
    }.buffer(0).flowOn(Dispatchers.IO)

    /**
     * Copies [input] into [target], emitting the same [Progress] events as [download]. Used
     * to import a model file the user already has on-device (SAF file picker): there is
     * nothing to fetch or resume, just a local stream copy, but sharing [Progress] lets
     * callers register a finished import exactly like a finished download.
     *
     * The magic-byte check runs against [expectedExtension] on the FIRST bytes read, before
     * anything is written to disk — a mis-picked file fails immediately instead of after
     * copying gigabytes for nothing. [expectedExtension] is passed explicitly rather than
     * derived from [target]'s name, since the picker lets the user choose any file regardless
     * of its own claimed name/extension.
     */
    fun copyFromStream(
        input: InputStream,
        target: File,
        totalBytes: Long?,
        expectedExtension: String,
    ): Flow<Progress> = flow {
        target.parentFile?.mkdirs()
        java.io.RandomAccessFile(File(target.absolutePath + ".lock"), "rw").use { handle ->
            val lock = runCatching { handle.channel.tryLock() }.getOrNull()
            if (lock == null) {
                input.close()
                emit(Progress.Failed(IllegalStateException("A transfer already owns this model file")))
                return@flow
            }
            lock.use {
                val partial = File(target.absolutePath + ".partial")
                emit(Progress.Started(totalBytes))

                var invalidMagic = false
                val ioFailure: Throwable? = try {
                    input.use { source ->
                        partial.outputStream().use { sink ->
                            val buf = ByteArray(64 * 1024)
                            // InputStream.read(ByteArray) is only guaranteed to return at least one
                            // byte, not four — a network-backed SAF provider (Google Drive and
                            // similar) can hand back 1-3 bytes on the very first read. Accumulate
                            // across reads into a dedicated 4-byte buffer until either 4 bytes are
                            // in hand or the stream hits EOF, then validate once. Every byte read
                            // during accumulation is still written to sink below, so the copy stays
                            // byte-exact regardless of how the reads happen to chunk.
                            val sniffBuf = ByteArray(4)
                            var sniffLen = 0
                            var sniffed = false
                            var totalRead = 0L
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val n = source.read(buf)
                                if (n <= 0) {
                                    // EOF before 4 bytes accumulated (including a 0-byte file, where
                                    // sniffLen stays 0): validate whatever was collected.
                                    // isValidMagicForExtension rejects anything shorter than 4 bytes.
                                    if (!sniffed) {
                                        sniffed = true
                                        if (!isValidMagicForExtension(expectedExtension, sniffBuf.copyOf(sniffLen))) {
                                            invalidMagic = true
                                        }
                                    }
                                    break
                                }
                                sink.write(buf, 0, n)
                                totalRead += n
                                emit(Progress.Tick(totalRead, totalBytes))
                                if (!sniffed) {
                                    val need = (4 - sniffLen).coerceAtMost(n)
                                    System.arraycopy(buf, 0, sniffBuf, sniffLen, need)
                                    sniffLen += need
                                    if (sniffLen >= 4) {
                                        sniffed = true
                                        if (!isValidMagicForExtension(expectedExtension, sniffBuf)) {
                                            invalidMagic = true
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                    null
                } catch (e: java.io.IOException) {
                    e
                }

                if (invalidMagic) {
                    Log.w(TAG, "Import rejected: first bytes are not a valid .$expectedExtension file")
                    runCatching { partial.delete() }
                    emit(Progress.Failed(IllegalArgumentException(
                        "Selected file does not look like a valid .$expectedExtension model " +
                        "(unexpected magic bytes)."
                    )))
                    return@flow
                }
                if (ioFailure != null) {
                    Log.w(TAG, "Import read interrupted: ${ioFailure::class.simpleName}: ${ioFailure.message}")
                    runCatching { partial.delete() }
                    emit(Progress.Failed(java.io.IOException(
                        "Import interrupted (${ioFailure::class.simpleName}: ${ioFailure.message}).",
                        ioFailure,
                    )))
                    return@flow
                }

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val renamed = runCatching {
                    java.nio.file.Files.move(partial.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }.isSuccess
                if (!renamed) {
                    Log.e(TAG, "Rename failed: ${partial.absolutePath} -> ${target.absolutePath}")
                    runCatching { partial.delete() }
                    emit(Progress.Failed(IllegalStateException(
                        "Failed to move the imported file to its final location: ${target.absolutePath}."
                    )))
                    return@flow
                }
                Log.i(TAG, "Import complete: ${target.absolutePath}")
                File(target.absolutePath + ".download.properties").delete()
                File(target.absolutePath + ".partial.properties").delete()
                emit(Progress.Done(target))
            }
        }
    }.buffer(0).flowOn(Dispatchers.IO)
}
