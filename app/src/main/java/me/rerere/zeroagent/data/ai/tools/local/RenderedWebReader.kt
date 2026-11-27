package me.rerere.zeroagent.data.ai.tools.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.browser.BrowserCacheSweeper
import me.rerere.zeroagent.browser.evaluateJavascriptAsync
import me.rerere.zeroagent.data.ai.net.GuardedDns
import me.rerere.zeroagent.data.ai.net.hostIsBlockedLiteral
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.Proxy
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val SNAPSHOT_WIDTH = 960
private const val SNAPSHOT_HEIGHT = 1440
private const val PAGE_BYTES = 512 * 1024
private const val ASSET_BYTES = 192 * 1024
private const val MAX_ASSETS = 12
private const val SNAPSHOT_TIMEOUT_MS = 25_000L
private val renderLock = Mutex()
private val assetTypes = setOf("text/css", "image/png", "image/jpeg", "image/webp", "image/gif", "image/avif")

// A separate client ensures provider headers, authentication and cookies never enter reader requests.
private val publicReaderClient by lazy {
    OkHttpClient.Builder()
        .dns(GuardedDns())
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()
}

internal fun isPublicReaderUrl(url: HttpUrl): Boolean =
    url.username.isEmpty() && url.password.isEmpty() && !hostIsBlockedLiteral(url.host)

private fun HttpUrl.sameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private data class ReaderResponse(
    val url: HttpUrl,
    val status: Int,
    val contentType: String?,
    val body: ByteArray,
    val truncated: Boolean,
) {
    val mediaType get() = contentType?.substringBefore(';')?.trim()?.lowercase()
    val successful get() = status in 200..299
}

private class BlockedReaderAddress : IOException()

private suspend fun loadPublicResource(initial: HttpUrl, limit: Int, assetOrigin: HttpUrl? = null): ReaderResponse {
    var url = initial
    repeat(5) {
        if (!isPublicReaderUrl(url) || assetOrigin?.sameOrigin(url) == false) throw BlockedReaderAddress()
        val (next, result) = loadReaderHop(url, limit)
        if (next == null) return checkNotNull(result)
        url = url.resolve(next) ?: throw IOException("Invalid redirect")
    }
    throw IOException("Too many redirects")
}

private suspend fun loadReaderHop(url: HttpUrl, limit: Int): Pair<String?, ReaderResponse?> =
    suspendCancellableCoroutine { continuation ->
        val call = publicReaderClient.newCall(Request.Builder().url(url).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val next = response.header("Location")
                        if (response.code in setOf(301, 302, 303, 307, 308) && next != null) {
                            next to null
                        } else {
                            val (body, truncated) = readBounded(response.body.byteStream(), limit)
                            null to ReaderResponse(url, response.code, response.header("Content-Type"), body, truncated)
                        }
                    }
                    continuation.resume(result)
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

internal fun prepareOfflineReaderPage(html: String): Document = Jsoup.parse(html).apply {
    // Use an opaque origin, fixed policy, and no page-authored scripts or nested documents.
    select("script, base, meta[http-equiv], iframe, frame, object, embed, portal, audio, video, source, noscript").remove()
    select("link:not([rel=stylesheet])").remove()
    select("[srcset]").removeAttr("srcset")
    select("[ping]").removeAttr("ping")
    val policy = "default-src 'none'; style-src 'unsafe-inline' data:; img-src data:; " +
        "script-src 'none'; form-action 'none'; base-uri 'none'"
    head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", policy)
    outputSettings().prettyPrint(false)
}

private suspend fun inlineReaderAssets(document: Document, pageUrl: HttpUrl) {
    val started = System.nanoTime()
    var attempted = 0
    // Preserve same-origin CSS and raster images within the resource budget. WebView never
    // loads remote resource URLs itself, including any extra URLs nested inside CSS.
    for (element in document.select("link[rel=stylesheet], img[src]")) {
        val stylesheet = element.normalName() == "link"
        val attribute = if (stylesheet) "href" else "src"
        val raw = element.attr(attribute)
        element.removeAttr(attribute)
        if (raw.isBlank() || attempted >= MAX_ASSETS || System.nanoTime() - started > 5_000_000_000L) continue
        val url = pageUrl.resolve(raw)?.takeIf(pageUrl::sameOrigin) ?: continue
        attempted++
        val resource = try {
            loadPublicResource(url, ASSET_BYTES, pageUrl)
        } catch (_: IOException) {
            continue
        }
        val type = resource.mediaType ?: continue
        if (!resource.successful || resource.truncated || type !in assetTypes || stylesheet != (type == "text/css")) continue
        if (!stylesheet) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(resource.body, 0, resource.body.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 4_000_000) continue
        }
        val encoded = Base64.getEncoder().encodeToString(resource.body)
        element.attr(attribute, "data:$type;base64,$encoded")
    }
}

internal data class VisiblePage(val title: String, val text: String, val length: Int, val image: Bitmap?)

internal suspend fun renderOfflinePage(
    context: Context,
    html: String,
    offset: Int,
    limit: Int,
    includeImage: Boolean,
): VisiblePage? = withContext(Dispatchers.Main.immediate) {
    val webView = try {
        WebView(context)
    } catch (_: RuntimeException) {
        return@withContext null
    }
    var rendererGone = false
    try {
        val pageReady = CompletableDeferred<Boolean>()
        webView.settings.apply {
            // Only the native evaluation below can run script. The document CSP forbids page scripts.
            javaScriptEnabled = true
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            domStorageEnabled = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return if (request.url.scheme == "data" || request.url.scheme == "about") null else
                    WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageReady.complete(true)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone = true
                pageReady.complete(false)
                return true
            }
        }
        webView.setBackgroundColor(Color.WHITE)
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(SNAPSHOT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SNAPSHOT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT)
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        if (withTimeoutOrNull(7_000L) { pageReady.await() } != true || rendererGone) return@withContext null
        val response = webView.evaluateJavascriptAsync(
            """(function() {
                const text = document.body?.innerText?.trim() || '';
                return {title: document.title.slice(0, 300), text: text.slice($offset, ${offset.toLong() + limit}), length: text.length};
            })()""".trimIndent(),
            timeoutMs = 2_000L,
        ) ?: return@withContext null
        val visible = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return@withContext null
        val image = if (includeImage && !rendererGone) {
            val drawn = CompletableDeferred<Unit>()
            webView.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { drawn.complete(Unit) }
            })
            withTimeoutOrNull(2_000L) { drawn.await() }
            if (!rendererGone) {
                Bitmap.createBitmap(SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap ->
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        webView.draw(Canvas(bitmap))
                    } catch (e: RuntimeException) {
                        bitmap.recycle()
                        throw e
                    }
                }.let { bitmap ->
                    if (hasVisiblePixels(bitmap)) bitmap else {
                        bitmap.recycle()
                        null
                    }
                }
            } else null
        } else null
        VisiblePage(
            title = visible["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            text = visible["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            length = visible["length"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            image = image,
        )
    } finally {
        try {
            if (!rendererGone) webView.stopLoading()
        } finally {
            webView.destroy()
        }
    }
}

private suspend fun VisiblePage.textResultWithSnapshot(context: Context): Pair<VisiblePage, String?> {
    val snapshot = image ?: return this to null
    val imagePath = try {
        withContext(Dispatchers.IO) {
            BrowserCacheSweeper.sweep(context)
            val folder = context.cacheDir.resolve("browser-shots").apply { mkdirs() }
            val temp = java.io.File.createTempFile("reader-", ".png", folder)
            try {
                if (!temp.outputStream().use { snapshot.compress(Bitmap.CompressFormat.PNG, 100, it) }) {
                    throw IOException("Could not encode page snapshot")
                }
                temp.absolutePath
            } catch (e: IOException) {
                temp.delete()
                throw e
            }
        }
    } catch (_: IOException) {
        null
    } finally {
        snapshot.recycle()
    }
    return copy(image = null) to imagePath
}

private fun hasVisiblePixels(bitmap: Bitmap): Boolean {
    val background = bitmap.getPixel(0, 0)
    val row = IntArray(bitmap.width)
    for (y in 0 until bitmap.height step 2) {
        bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
        if (row.any { it != background }) return true
    }
    return false
}

internal suspend fun readRenderedWebPage(
    context: Context,
    url: HttpUrl,
    startIndex: Int,
    maxChars: Int,
    modelCanSeeImages: Boolean,
): List<UIMessagePart> = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
    try {
        val page = loadPublicResource(url, PAGE_BYTES)
        if (!page.successful) {
            return@withTimeoutOrNull fmTextPart(buildJsonObject {
                put("error", "http_error")
                put("status", page.status)
                put("final_url", page.url.toString())
            }.toString())
        }
        if (page.mediaType !in setOf(null, "text/html", "application/xhtml+xml", "text/plain")) {
            return@withTimeoutOrNull fmTextPart(fmErrEnvelope("unsupported_page", "Rendered reading supports HTML and plain text pages."))
        }
        val offset = startIndex.coerceAtLeast(0)
        val html = withContext(Dispatchers.IO) {
            val body = decodeBody(page.body, page.body.size, page.contentType)
            val document = prepareOfflineReaderPage(if (page.mediaType == "text/plain") "" else body)
            if (page.mediaType == "text/plain") document.body().appendElement("pre").text(body)
            inlineReaderAssets(document, page.url)
            document.outerHtml()
        }
        renderLock.withLock {
            val visible = withContext(Dispatchers.Main.immediate) {
                val rendered = renderOfflinePage(context, html, offset, maxChars, modelCanSeeImages && offset == 0)
                    ?: return@withContext null
                rendered.textResultWithSnapshot(context)
            }
                ?: return@withLock fmTextPart(fmErrEnvelope("render_unavailable", "The page could not be rendered. Try article mode for text-only extraction."))
            val (pageText, imagePath) = visible
            buildList {
                val nextIndex = offset.toLong() + pageText.text.length
                add(UIMessagePart.Text(buildJsonObject {
                    put("status", page.status)
                    put("ok", true)
                    put("final_url", page.url.toString())
                    put("title", pageText.title)
                    put("text", pageText.text)
                    put("rendered", true)
                    put("body_truncated", page.truncated)
                    put("truncated", pageText.length > nextIndex)
                    if (pageText.length > nextIndex && pageText.text.isNotEmpty()) put("next_start_index", nextIndex)
                    put("image_attached", imagePath != null)
                    put("detail", "Offline rendering of public content. Script-based content, cross-origin assets and content outside the first screenshot viewport may be missing.")
                    if (pageText.text.isBlank()) put("recovery", "No visible text in this range. If the page needs an interactive browser, ask the user to share a screenshot.")
                }.toString()))
                if (imagePath != null) add(UIMessagePart.Image(url = "file://$imagePath"))
            }
        }
    } catch (e: IOException) {
        val blocked = e is BlockedReaderAddress || e.message?.contains("blocked_private_address") == true
        val code = when {
            blocked -> "blocked_address"
            e is java.io.InterruptedIOException -> "timeout"
            else -> "network_error"
        }
        fmTextPart(fmErrEnvelope(code, when (code) {
            "blocked_address" -> "Only public URLs without credentials are supported."
            "timeout" -> "The public page did not respond in time."
            else -> "Could not load the public page."
        }))
    } catch (e: CancellationException) {
        throw e
    } catch (_: RuntimeException) {
        fmTextPart(fmErrEnvelope("render_unavailable", "The page could not be rendered. Try article mode for text-only extraction."))
    }
} ?: fmTextPart(fmErrEnvelope("timeout", "Rendered webpage reading ran out of time."))
