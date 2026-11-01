package me.rerere.search

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.internal.closeQuietly
import okio.IOException
import kotlin.coroutines.resumeWithException

/** Cap for a scraped page body: bounds memory before Jsoup parses it (which roughly doubles
 * the footprint), so a huge or unbounded response can't be dragged fully into memory first. */
internal const val SCRAPE_BODY_CAP = 256 * 1024

/**
 * Read at most [capBytes] from [response]'s body and decode it using its declared charset
 * (UTF-8 fallback), mirroring the chunked-read loop WebFetchTool.readBounded uses so a
 * multi-GB response never gets buffered whole just because callTimeout only bounds time.
 */
internal fun boundedBody(response: Response, capBytes: Int): String {
    val charset = response.body.contentType()?.charset() ?: Charsets.UTF_8
    val ins = response.body.byteStream()
    val out = java.io.ByteArrayOutputStream(minOf(capBytes, 8 * 1024))
    val buf = ByteArray(8192)
    var total = 0
    while (total < capBytes) {
        val want = minOf(buf.size, capBytes - total)
        val read = ins.read(buf, 0, want)
        if (read < 0) break
        out.write(buf, 0, read)
        total += read
    }
    return String(out.toByteArray(), charset)
}

internal suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val buffered = response.use {
                        val body = it.body
                        require(body.contentLength() <= SEARCH_BODY_CAP) { "Search response exceeds 1 MiB" }
                        if (body.source().request(SEARCH_BODY_CAP.toLong() + 1)) {
                            throw IOException("Search response exceeds 1 MiB")
                        }
                        it.newBuilder().body(body.bytes().toResponseBody(body.contentType())).build()
                    }
                    continuation.resume(buffered) { _, value, _ -> value.closeQuietly() }
                } catch (e: Exception) {
                    response.closeQuietly()
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
