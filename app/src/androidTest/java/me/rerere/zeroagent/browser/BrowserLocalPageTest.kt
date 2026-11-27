package me.rerere.zeroagent.browser

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import me.rerere.zeroagent.data.files.FileFolders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserLocalPageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installedSkillCanLoadItsDocumentAndSiblingAsset() {
        val root = context.filesDir.resolve(FileFolders.SKILLS).resolve("preview-test-${UUID.randomUUID()}")
        check(root.mkdirs())
        val document = root.resolve("test page.html")
        val script = root.resolve("style.css")
        try {
            document.writeText("<p>Installed skill preview</p>")
            script.writeText("p { color: green; }")
            val source = Uri.fromFile(document).buildUpon().appendQueryParameter("mode", "compact").build()
            val preview = checkNotNull(BrowserLocalPage.resolve(context, source.toString()))
            val virtual = Uri.parse(preview.url)
            assertEquals("https", virtual.scheme)
            assertEquals("compact", virtual.getQueryParameter("mode"))
            assertEquals(document.readText(), preview.assetLoader.shouldInterceptRequest(virtual)?.data?.bufferedReader()?.use { it.readText() })
            val sibling = virtual.buildUpon().path("/preview/style.css").clearQuery().build()
            assertEquals(script.readText(), preview.assetLoader.shouldInterceptRequest(sibling)?.data?.bufferedReader()?.use { it.readText() })
        } finally {
            script.delete()
            document.delete()
            root.delete()
        }
    }

    @Test
    fun unrelatedApplicationFileDoesNotOpenAsLocalPreview() {
        val unrelated = File.createTempFile("browser-review-", ".html", context.cacheDir)
        try {
            assertNull(BrowserLocalPage.resolve(context, Uri.fromFile(unrelated).toString()))
        } finally {
            unrelated.delete()
        }
    }
}
