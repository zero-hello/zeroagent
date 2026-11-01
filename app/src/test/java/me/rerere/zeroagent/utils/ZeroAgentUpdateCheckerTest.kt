package me.rerere.zeroagent.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import me.rerere.zeroagent.reliability.GitHubReleaseChecker
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class ZeroAgentUpdateCheckerTest {
    @Test fun `missing update channel is current version without network or error`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val client = OkHttpClient.Builder().addInterceptor { error("Unexpected network request") }.build()
            val updates = GitHubReleaseChecker(client)
            assertFalse(updates.isConfigured)
            val checker = UpdateChecker(client, scope, apiUrl = "", currentVersionName = "1.0.1", githubReleases = updates)
            val info = checker.fetchUpdateInfo()
            assertEquals("1.0.1", info.version)
            assertTrue(info.downloads.isEmpty())
        } finally {
            scope.cancel()
        }
    }
}
