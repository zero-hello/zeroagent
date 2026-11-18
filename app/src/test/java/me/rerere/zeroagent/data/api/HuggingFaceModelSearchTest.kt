package me.rerere.zeroagent.data.api

import kotlinx.serialization.decodeFromString
import me.rerere.zeroagent.data.model.HfModelDetail
import me.rerere.zeroagent.data.model.HfSibling
import me.rerere.zeroagent.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Covers [HuggingFaceModelSearch.toFilesResult], the pure gating/filtering decision split out
 * of [HuggingFaceModelSearch.listGgufFiles] specifically so it needs no network call to test,
 * and the wire-format decoding of HuggingFace's `gated` field, verified against the live
 * `/api/models/{repo}` response on 2026-08-03 (see HfGatedSerializer's doc).
 */
class HuggingFaceModelSearchTest {

    @Test
    fun `blank query browses popular GGUF models and forwards pagination`() = runBlocking {
        val api = RecordingHuggingFaceApi()

        val result = HuggingFaceModelSearch.search(api, "  ", skip = 40)

        assertTrue(result.isSuccess)
        assertEquals(null, api.search)
        assertEquals("gguf", api.filter)
        assertEquals(HuggingFaceModelSearch.PAGE_SIZE, api.limit)
        assertEquals("downloads", api.sort)
        assertEquals(-1, api.direction)
        assertEquals(40, api.skip)
    }

    @Test
    fun `search query is trimmed and negative offsets are rejected`() = runBlocking {
        val api = RecordingHuggingFaceApi()

        HuggingFaceModelSearch.search(api, "  Qwen3  ", skip = 80)

        assertEquals("Qwen3", api.search)
        assertEquals(80, api.skip)
        assertTrue(runCatching { HuggingFaceModelSearch.search(api, "Qwen3", skip = -1) }.isFailure)
    }

    private class RecordingHuggingFaceApi : HuggingFaceAPI {
        var search: String? = null
        var filter: String? = null
        var limit: Int? = null
        var sort: String? = null
        var direction: Int? = null
        var skip: Int? = null

        override suspend fun searchModels(
            search: String?,
            filter: String,
            limit: Int,
            sort: String,
            direction: Int,
            skip: Int,
        ): List<me.rerere.zeroagent.data.model.HfModelSearchResult> {
            this.search = search
            this.filter = filter
            this.limit = limit
            this.sort = sort
            this.direction = direction
            this.skip = skip
            return emptyList()
        }

        override suspend fun getModel(repoId: String, blobs: Boolean): HfModelDetail =
            error("Not used by search tests")
    }

    @Test
    fun `a public repo's siblings are filtered down to gguf files with a known size`() {
        val detail = HfModelDetail(
            id = "Qwen/Qwen3-4B-GGUF",
            gated = false,
            private = false,
            siblings = listOf(
                HfSibling(".gitattributes", size = 1798),
                HfSibling("LICENSE", size = 11544),
                HfSibling("Qwen3-4B-Q4_K_M.gguf", size = 2497280256),
                HfSibling("Qwen3-4B-Q8_0.gguf", size = 4211308416),
                HfSibling("README.md", size = 100),
            ),
        )

        val result = HuggingFaceModelSearch.toFilesResult(detail)

        assertTrue(result is HuggingFaceModelSearch.FilesResult.Files)
        val files = (result as HuggingFaceModelSearch.FilesResult.Files).entries
        assertEquals(
            listOf(
                HuggingFaceModelSearch.GgufFileEntry("Qwen3-4B-Q4_K_M.gguf", 2497280256),
                HuggingFaceModelSearch.GgufFileEntry("Qwen3-4B-Q8_0.gguf", 4211308416),
            ),
            files,
        )
    }

    @Test
    fun `a gated repo is RequiresAccess regardless of its siblings`() {
        val detail = HfModelDetail(
            id = "meta-llama/Llama-3.1-8B-Instruct-GGUF",
            gated = true,
            private = false,
            siblings = listOf(HfSibling("model.gguf", size = 1_000_000)),
        )

        assertEquals(HuggingFaceModelSearch.FilesResult.RequiresAccess, HuggingFaceModelSearch.toFilesResult(detail))
    }

    @Test fun `split weights and vision projectors are not offered as standalone models`() {
        val detail = HfModelDetail(id = "test/repo", siblings = listOf(
            HfSibling("model-00001-of-00002.gguf", size = 1000),
            HfSibling("mmproj-model.gguf", size = 2000),
            HfSibling("model.gguf", size = 3000)))
        val result = HuggingFaceModelSearch.toFilesResult(detail) as HuggingFaceModelSearch.FilesResult.Files
        assertEquals(listOf("model.gguf"), result.entries.map { it.fileName })
    }

    @Test
    fun `a private repo is RequiresAccess even when not gated`() {
        val detail = HfModelDetail(
            id = "someone/private-repo",
            gated = false,
            private = true,
            siblings = listOf(HfSibling("model.gguf", size = 1_000_000)),
        )

        assertEquals(HuggingFaceModelSearch.FilesResult.RequiresAccess, HuggingFaceModelSearch.toFilesResult(detail))
    }

    @Test
    fun `a sibling without a size is dropped rather than reported with a bogus size`() {
        val detail = HfModelDetail(
            id = "some/repo",
            siblings = listOf(HfSibling("model.gguf", size = null)),
        )

        val result = HuggingFaceModelSearch.toFilesResult(detail) as HuggingFaceModelSearch.FilesResult.Files
        assertEquals(emptyList<HuggingFaceModelSearch.GgufFileEntry>(), result.entries)
    }

    @Test
    fun `a repo with no gguf files at all reports an empty Files result, not RequiresAccess`() {
        val detail = HfModelDetail(
            id = "some/repo",
            siblings = listOf(HfSibling("README.md", size = 100)),
        )

        assertEquals(
            HuggingFaceModelSearch.FilesResult.Files(emptyList()),
            HuggingFaceModelSearch.toFilesResult(detail),
        )
    }

    @Test
    fun `resolveUrl builds the same resolve-main URL shape ModelInstall already normalises to`() {
        assertEquals(
            "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
            HuggingFaceModelSearch.resolveUrl("Qwen/Qwen3-4B-GGUF", "Qwen3-4B-Q4_K_M.gguf"),
        )
    }

    @Test
    fun `gated decodes the literal JSON boolean false as public`() {
        val detail = JsonInstant.decodeFromString<HfModelDetail>(
            """{"id":"Qwen/Qwen3-4B-GGUF","gated":false,"private":false,"siblings":[]}""",
        )
        assertEquals(false, detail.gated)
    }

    @Test
    fun `gated decodes the HuggingFace gate-kind strings as gated`() {
        val manual = JsonInstant.decodeFromString<HfModelDetail>(
            """{"id":"x/y","gated":"manual","private":false,"siblings":[]}""",
        )
        val auto = JsonInstant.decodeFromString<HfModelDetail>(
            """{"id":"x/y","gated":"auto","private":false,"siblings":[]}""",
        )
        assertEquals(true, manual.gated)
        assertEquals(true, auto.gated)
    }

    @Test
    fun `size is null when blobs were not requested, matching the un-parameterised endpoint`() {
        val detail = JsonInstant.decodeFromString<HfModelDetail>(
            """{"id":"x/y","gated":false,"private":false,"siblings":[{"rfilename":"model.gguf"}]}""",
        )
        assertEquals(listOf(HfSibling("model.gguf", size = null)), detail.siblings)
    }
}
