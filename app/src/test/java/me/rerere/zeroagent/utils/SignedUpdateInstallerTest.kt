package me.rerere.zeroagent.utils

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedUpdateInstallerTest {
    @Test
    fun `cancelling update closes a stalled response body`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("fixture").bodyDelay(4, TimeUnit.SECONDS).build())
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/update.apk")).build())
            val readingBody = CompletableDeferred<Unit>()
            val download = launch(Dispatchers.IO) {
                call.readUpdateResponse { response ->
                    readingBody.complete(Unit)
                    response.body.string()
                }
            }
            withTimeout(2_000) { readingBody.await() }
            download.cancel()
            withTimeout(2_000) { download.join() }
            assertTrue(call.isCanceled())
        }
    }
}
