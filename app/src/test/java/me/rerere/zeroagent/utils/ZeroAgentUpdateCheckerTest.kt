package me.rerere.zeroagent.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import me.rerere.zeroagent.reliability.GitHubReleaseChecker
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ZeroAgentUpdateCheckerTest {
    @Test fun `missing update channel is current version without network or error`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val client = OkHttpClient.Builder().addInterceptor { error("Unexpected network request") }.build()
            val updates = GitHubReleaseChecker(client, repo = "")
            assertFalse(updates.isConfigured)
            val checker = UpdateChecker(client, scope, apiUrl = "", currentVersionName = "1.0.1", githubReleases = updates)
            val info = checker.fetchUpdateInfo()
            assertEquals("1.0.1", info.version)
            assertTrue(info.downloads.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `transient GitHub error retries on schedule and offers the new APK`() = runBlocking {
        var elapsed = 0L
        var requests = 0
        val apk = "ZeroAgent-v2.2.3-arm64-v8a.apk"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/repos/zero-hello/zeroagent/releases/latest", request.url.encodedPath)
            assertEquals("no-cache", request.header("Cache-Control"))
            requests++
            val code = if (requests == 1) 503 else 200
            val response = if (code == 503) "{}" else """
                {
                    "tag_name": "v2.2.3",
                    "body": null,
                    "published_at": "2026-11-23T00:00:00Z",
                    "assets": [
                        {"name": "ZeroAgent-v2.2.3-x86_64.apk"},
                        {"name": "$apk", "size": 50000000,
                         "browser_download_url": "https://github.com/zero-hello/zeroagent/releases/download/v2.2.3/$apk"}
                    ]
                }
            """.trimIndent()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test response")
                .body(response.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val checker = UpdateChecker(
            client, this, apiUrl = "", currentVersionName = "2.2.2",
            githubReleases = GitHubReleaseChecker(client), elapsedRealtime = { elapsed },
        )

        assertEquals(120_000L, checker.refresh())
        assertTrue(checker.updateState.value is UiState.Error)
        elapsed += 30_000L
        assertEquals(90_000L, checker.refresh())
        assertEquals(1, requests)
        elapsed += 90_000L
        assertEquals(900_000L, checker.refresh())
        assertEquals(2, requests)

        val info = (checker.updateState.value as UiState.Success).data
        assertEquals("2.2.3", info.version)
        assertEquals("", info.changelog)
        val arm64: (UpdateDownload) -> Boolean = { it.name.endsWith("-arm64-v8a.apk") }
        assertEquals(apk, info.newerDownload("2.2.2", arm64)?.name)
        assertNull(info.newerDownload("2.2.3", arm64))
        assertNull(info.newerDownload("2.2.4", arm64))
        assertNull(info.newerDownload("2.2.2") { false })
        assertEquals(900_000L, checker.refresh())
        assertEquals(2, requests)
    }
    @Test fun `manual check bypasses cooldown and can reopen a compatible update`() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val code = if (requests == 1) 503 else 200
            releaseResponse(chain.request(), code, """{"tag_name":"v2.4.1","assets":[{"name":"ZeroAgent-v2.4.1-arm64-v8a.apk"}]}""")
        }.build()
        val checker = UpdateChecker(
            client, this, apiUrl = "", currentVersionName = "2.4.0",
            githubReleases = GitHubReleaseChecker(client), elapsedRealtime = { 0L },
        )
        val compatible: (UpdateDownload) -> Boolean = { it.name.endsWith("-arm64-v8a.apk") }

        assertEquals(120_000L, checker.refresh())
        assertTrue(checker.checkNow(compatible) is UiState.Success)
        assertEquals(2, requests)
        val available = requireNotNull(checker.manualUpdate.value)
        assertEquals("2.4.1", available.version)
        checker.dismissManualUpdate(available)
        assertNull(checker.manualUpdate.value)
        checker.checkNow(compatible)
        assertEquals(available, checker.manualUpdate.value)
        checker.checkNow { false }
        assertNull(checker.manualUpdate.value)
        assertEquals(4, requests)
    }

    @Test fun `GitHub rate limit falls back to the public release manifest`() = runBlocking {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.host + request.url.encodedPath
            if (request.url.host == "api.github.com") {
                releaseResponse(request, 429, "{}")
            } else {
                releaseResponse(request, 200, """{
                    "tag_name":"v2.4.1", "body":"Update notes", "assets":[{
                        "name":"ZeroAgent-v2.4.1-arm64-v8a.apk",
                        "size":55696406,
                        "browser_download_url":"https://github.com/zero-hello/zeroagent/releases/download/v2.4.1/ZeroAgent-v2.4.1-arm64-v8a.apk",
                        "digest":"sha256:52a1ee23e85425d49b484bab68cc12188c869d7ea34e82da0b6e46716cd1fd09"
                    }]
                }""")
            }
        }.build()
        val info = GitHubReleaseChecker(client, useReleaseManifest = true).fetchUpdateInfo()
        assertEquals(listOf(
            "api.github.com/repos/zero-hello/zeroagent/releases/latest",
            "github.com/zero-hello/zeroagent/releases/latest/download/update.json",
        ), requests)
        assertEquals("2.4.1", info.version)
        assertEquals("Update notes", info.changelog)
        assertEquals(55696406L, info.downloads.single().sizeBytes)
        assertEquals(64, info.downloads.single().sha256?.length)
    }

    private fun releaseResponse(request: okhttp3.Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test response")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}
