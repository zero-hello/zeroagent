package me.rerere.zeroagent.browser

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal enum class BrowserLoadResult { LOADED, FAILED, TIMED_OUT }

/** Tracks the main-frame load without relying on JavaScript or the previous document. */
internal open class BrowserNavigationClient : WebViewClient() {
    private var current = CompletableDeferred<BrowserLoadResult>()
    private var startedUrl: String? = null

    fun load(view: WebView, url: String): CompletableDeferred<BrowserLoadResult> {
        current.complete(BrowserLoadResult.FAILED)
        current = CompletableDeferred()
        startedUrl = null
        view.loadUrl(url)
        return current
    }

    fun currentLoad(): CompletableDeferred<BrowserLoadResult> = current

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (current.isCompleted) current = CompletableDeferred()
        startedUrl = url
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null && url == startedUrl && url == view?.url) {
            current.complete(BrowserLoadResult.LOADED)
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true && request.url.toString() == startedUrl) {
            current.complete(BrowserLoadResult.FAILED)
        }
    }
}

internal suspend fun WebView.awaitBrowserNavigation(
    url: String?,
    timeoutMs: Long,
): BrowserLoadResult {
    val load = withContext(Dispatchers.Main.immediate) {
        val tracker = webViewClient as? BrowserNavigationClient ?: return@withContext null
        if (url != null) tracker.load(this@awaitBrowserNavigation, url) else tracker.currentLoad()
    } ?: return BrowserLoadResult.FAILED
    return withTimeoutOrNull(timeoutMs) { load.await() } ?: BrowserLoadResult.TIMED_OUT
}
