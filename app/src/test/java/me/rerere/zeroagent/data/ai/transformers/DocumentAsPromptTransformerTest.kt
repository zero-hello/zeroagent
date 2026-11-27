package me.rerere.zeroagent.data.ai.transformers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Coverage for [DocumentAsPromptTransformer.resolveWorkspacePathForFile] — the pure,
 * android.net.Uri-free part of the path-advertising gate shared by Document and Image parts
 * (issue #37, second bullet: generated images should be advertised the same way uploaded
 * documents are, but only for files that really live under filesDir/upload).
 *
 * Note: the android.net.Uri parsing step (`String.toUri()`) that sits in front of this
 * function is NOT covered here. The :app module's JVM unit tests run against the unmocked
 * Android stub jar (no Robolectric, no `unitTests.isReturnDefaultValues`), so
 * `android.net.Uri.parse` throws and is swallowed by the surrounding `runCatching`, making
 * `data:`/`http(s)` vs. `file:` URLs indistinguishable in this test environment (see the same
 * caveat in DownloadToolTest.kt). That parsing step is exercised implicitly by production use;
 * this test covers the part that is actually testable in isolation: given a real local file,
 * is it inside the upload folder, and if so what workspace path is advertised.
 */
class DocumentAsPromptTransformerTest {

    @Test
    fun `read outcomes survive a message round trip without recording extracted text`() {
        val metadata = buildJsonObject { put("legacy", JsonPrimitive("keep")) }
        val original = UIMessagePart.Document("file:///original.pdf", "original.pdf", "application/pdf", metadata)
        assertNull(original.readStatus())
        for (status in DocumentReadStatus.entries) {
            val encoded = Json.encodeToString(original.withReadStatus(status))
            val decoded = Json.decodeFromString<UIMessagePart.Document>(encoded)
            assertEquals(status, decoded.readStatus())
            assertEquals("original.pdf", decoded.fileName)
            assertEquals(JsonPrimitive("keep"), decoded.metadata?.get("legacy"))
            assertEquals(2, decoded.metadata?.size)
        }
        assertNull(original.readStatus())
    }

    @Test
    fun `attachment budget reserves room for the reply and existing messages`() {
        assertEquals(1024, DocumentAsPromptTransformer.documentCharacterBudget(4096, 1024, 1000))
        assertEquals(0, DocumentAsPromptTransformer.documentCharacterBudget(4096, 1024, 3000))
        assertEquals(24_000, DocumentAsPromptTransformer.documentCharacterBudget(256_000, null, 1000))
    }

    @Test
    fun `file inside the upload folder resolves to its workspace path`() {
        val file = File(File("/data/user/0/pkg/files/upload"), "abc.png")
        val path = DocumentAsPromptTransformer.resolveWorkspacePathForFile(file)
        assertEquals("/upload/abc.png", path)
    }

    @Test
    fun `file outside the upload folder resolves to null`() {
        val file = File(File("/data/user/0/pkg/files/other"), "abc.png")
        val path = DocumentAsPromptTransformer.resolveWorkspacePathForFile(file)
        assertNull(path)
    }

    @Test
    fun `file with no parent resolves to null`() {
        val file = File("abc.png")
        val path = DocumentAsPromptTransformer.resolveWorkspacePathForFile(file)
        assertNull(path)
    }
}
