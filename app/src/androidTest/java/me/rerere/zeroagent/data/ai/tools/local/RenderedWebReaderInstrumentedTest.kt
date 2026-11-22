package me.rerere.zeroagent.data.ai.tools.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderedWebReaderInstrumentedTest {
    @Test
    fun readsVisiblyRenderedTextAndCapturesFirstViewport() = runBlocking {
        val html = prepareOfflineReaderPage(
            """
            <title>Public reader fixture</title>
            <style>body { background: #fafafa; } .hidden { display: none; }</style>
            <h1>Visible</h1><p class="hidden">Hidden by CSS</p>
            <script>document.querySelector('h1').textContent = 'Script ran';</script>
            """.trimIndent(),
        ).outerHtml()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val page = requireNotNull(renderOfflinePage(context, html, 0, 100, includeImage = true))
        try {
            assertEquals("Public reader fixture", page.title)
            assertEquals("Visible", page.text)
            assertNotNull("The drawn viewport contains the rendered fixture", page.image)
        } finally {
            page.image?.recycle()
        }
    }
}
