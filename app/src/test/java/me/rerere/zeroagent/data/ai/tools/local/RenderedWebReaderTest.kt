package me.rerere.zeroagent.data.ai.tools.local

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderedWebReaderTest {
    @Test
    fun `offline page retains styled contents but removes active documents and refreshes`() {
        val snapshot = prepareOfflineReaderPage(
            """
            <base href="https://example.com/">
            <meta http-equiv="refresh" content="0; url=https://example.net/">
            <style>.sample { color: blue }</style>
            <script>document.body.textContent = 'replaced';</script>
            <iframe src="https://example.net/"></iframe>
            <p class="sample">Visible text</p>
            <img src="hero.jpg" srcset="other.jpg">
            """.trimIndent(),
        )

        assertEquals("meta", snapshot.head().child(0).normalName())
        val policy = snapshot.head().child(0).attr("content")
        assertTrue(policy.contains("default-src 'none'"))
        assertTrue(policy.contains("script-src 'none'"))
        assertTrue(snapshot.select("script, iframe, base, meta[http-equiv=refresh], [srcset]").isEmpty())
        assertEquals("Visible text", snapshot.selectFirst("p.sample")?.text())
        assertTrue(snapshot.selectFirst("style")?.data()?.contains("color: blue") == true)
    }

    @Test
    fun `reader rejects private literals and credentials before connecting`() {
        for (url in listOf(
            "http://127.0.0.1/", "http://10.0.0.1/", "http://169.254.169.254/",
            "http://[::1]/", "http://[fd00::1]/", "https://user:pass@example.com/",
        )) {
            assertFalse(url, isPublicReaderUrl(url.toHttpUrl()))
        }
        assertTrue(isPublicReaderUrl("https://example.com/".toHttpUrl()))
    }
}
