package me.rerere.zeroagent.data.local

import me.rerere.locallm.LocalRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class LocalDownloadTargetTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun sameNameDifferentSourcesAreIsolatedBeforeEitherDownloadStarts() {
        val base = temporary.newFolder()
        val sourceA = "https://one.example/model.gguf"
        val sourceB = "https://two.example/model.gguf"
        val first = target(base, sourceA)
        val second = target(base, sourceB)
        assertFalse(first.exists())
        assertFalse(second.exists())
        assertNotEquals(first, second)
        assertEquals(first, target(base, sourceA))
    }

    @Test
    fun onlyMatchingLegacySourceIsReused() {
        val base = temporary.newFolder()
        val source = "https://one.example/model.gguf"
        val existing = File(base, "llamacpp/model.gguf")
        existing.parentFile?.mkdirs()
        existing.writeText("GGUF-verified")
        File(existing.absolutePath + ".download.properties").outputStream().use { output ->
            Properties().apply {
                setProperty("url", source)
                setProperty("size", existing.length().toString())
            }.store(output, null)
        }
        assertEquals(existing, target(base, source))
        assertNotEquals(existing, target(base, "https://two.example/model.gguf"))
    }

    private fun target(base: File, url: String) = localDownloadTarget(
        base, LocalRuntime.LlamaCpp, "model.gguf", url, null, null,
    )
}
