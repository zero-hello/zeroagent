package me.rerere.zeroagent.data.ai

import me.rerere.common.android.Logging
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

class RequestLoggingInterceptorTest {
    @Before fun enableLogging() {
        Logging.clear()
        Logging.setRequestLoggingEnabled(true)
    }

    @After fun clearLogging() {
        Logging.setRequestLoggingEnabled(false)
        Logging.clear()
    }

    private val request = Request.Builder()
        .url("https://user:private-host-token@private-host.example/private-path?key=private-query")
        .header("Authorization", "Bearer private-authorization")
        .post("private-chat-content".toRequestBody())
        .build()

    private fun chain(proceed: (Request) -> Response): Interceptor.Chain =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Interceptor.Chain::class.java)) { _, method, args ->
            when (method.name) {
                "request" -> request
                "proceed" -> proceed(args.single() as Request)
                else -> error("Unexpected interceptor call: ${method.name}")
            }
        } as Interceptor.Chain

    @Test fun recordsOnlySafeStatusAndTiming() {
        val fakeChain = chain { received ->
            Response.Builder().request(received).protocol(Protocol.HTTP_1_1)
                .code(201).message("private-status-message").header("Set-Cookie", "private-cookie")
                .body("private-model-content".toResponseBody()).build()
        }
        RequestLoggingInterceptor().intercept(fakeChain).use { assertEquals(201, it.code) }
        val log = Logging.getRequestLogs().single()
        assertEquals("HTTPS", log.url)
        assertEquals("POST", log.method)
        assertEquals(201, log.responseCode)
        assertNotNull(log.durationMs)
        assertFalse(log.toString().contains("private-"))
    }

    @Test fun recordsExceptionCategoryWithoutItsMessage() {
        val fakeChain = chain { throw IOException("private-transport-data") }
        assertThrows(IOException::class.java) { RequestLoggingInterceptor().intercept(fakeChain) }
        assertEquals("IOException", Logging.getRequestLogs().single().error)
        assertFalse(Logging.getRequestLogs().single().toString().contains("private-"))
    }
}
