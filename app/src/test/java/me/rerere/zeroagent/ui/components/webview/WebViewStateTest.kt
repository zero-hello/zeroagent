package me.rerere.zeroagent.ui.components.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewStateTest {
    @Test
    fun `redirect and link navigation do not restart the requested page`() {
        val request = WebContent.Url("https://example.com/")
        val state = WebViewState(request)

        assertTrue(state.shouldLoad(request))
        state.currentUrl = "https://example.com/redirected"
        assertFalse(state.shouldLoad(request))
        state.currentUrl = "https://example.com/another-page"
        assertFalse(state.shouldLoad(request))
    }

    @Test
    fun `an explicit repeat loads once and header changes count as a new request`() {
        val request = WebContent.Url("https://example.com/")
        val state = WebViewState(request)
        assertTrue(state.shouldLoad(request))

        state.loadUrl(request.url)
        assertTrue(state.shouldLoad(state.content))
        assertFalse(state.shouldLoad(state.content))

        state.loadUrl(request.url, mapOf("Accept-Language" to "zh-TW"))
        assertTrue(state.shouldLoad(state.content))
        assertFalse(state.shouldLoad(state.content))
    }

    @Test
    fun `generated document reloads once and its request is reset when the view is released`() {
        val document = WebContent.Data("<p>preview</p>")
        val state = WebViewState(document)
        assertTrue(state.shouldLoad(document))
        assertFalse(state.shouldLoad(document))

        state.reload()
        assertTrue(state.shouldLoad(document))
        assertFalse(state.shouldLoad(document))

        state.lastRequestedContent = null
        assertTrue(state.shouldLoad(document))
    }
}
