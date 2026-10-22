package me.rerere.zeroagent.data.api

import me.rerere.zeroagent.data.model.HfLfs
import me.rerere.zeroagent.data.model.HfModelDetail
import me.rerere.zeroagent.data.model.HfSibling
import org.junit.Assert.*
import org.junit.Test

class HuggingFaceLocalMetadataTest {
    @Test fun `LFS size and valid hash reach the installer metadata`() {
        val hash = "a".repeat(64)
        val result = HuggingFaceModelSearch.toFilesResult(HfModelDetail("public/model", siblings = listOf(
            HfSibling("model.gguf", lfs = HfLfs(hash, 1000)),
        ))) as HuggingFaceModelSearch.FilesResult.Files
        assertEquals(HuggingFaceModelSearch.GgufFileEntry("model.gguf", 1000, hash), result.entries.single())
    }
    @Test fun `unsupported split models and projection files cannot start a broken one-file install`() {
        val result = HuggingFaceModelSearch.toFilesResult(HfModelDetail("public/model", siblings = listOf(
            HfSibling("model-00001-of-00002.gguf", 100), HfSibling("mmproj-model.gguf", 100),
            HfSibling("model.gguf", 200), HfSibling("empty.gguf", 0),
        ))) as HuggingFaceModelSearch.FilesResult.Files
        assertEquals(listOf("model.gguf"), result.entries.map { it.fileName })
    }
    @Test fun `file URL preserves nested folders while encoding spaces and query punctuation`() {
        assertEquals("https://huggingface.co/org/repo/resolve/main/sub/model%20%231.gguf",
            HuggingFaceModelSearch.resolveUrl("org/repo", "sub/model #1.gguf"))
    }
}
