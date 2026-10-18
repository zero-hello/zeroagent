package me.rerere.search

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import kotlin.reflect.KClass
import org.junit.Assert.*
import org.junit.Test

class SearchTransportTest {
    private class FakeCall(private val response: Response? = null) : Call {
        var cancelled = false
        override fun addEventListener(eventListener: EventListener) {}
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun request() = Request.Builder().url("https://fixture.example").build()
        override fun execute(): Response = error("not used")
        override fun enqueue(responseCallback: Callback) { response?.let { responseCallback.onResponse(this, it) } }
        override fun cancel() { cancelled = true }
        override fun isExecuted() = true
        override fun isCanceled() = cancelled
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = FakeCall(response)
    }
    private fun response(body: String) = Response.Builder().request(Request.Builder().url("https://fixture.example").build())
        .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()

    @Test fun coroutineCancellationCancelsTheHttpCall() = runBlocking {
        val call = FakeCall()
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { call.await().close() }
        pending.cancelAndJoin()
        assertTrue(call.cancelled)
    }

    @Test fun transportReturnsAReadableBufferedBody() = runBlocking {
        FakeCall(response("fixture")).await().use { assertEquals("fixture", it.body.string()) }
    }

    @Test fun transportRejectsAnOversizedBody() = runBlocking {
        assertTrue(runCatching { FakeCall(response("x".repeat(SEARCH_BODY_CAP + 1))).await() }.isFailure)
    }
    @Test fun transportRejectsOversizedChunkedBodiesToo() = runBlocking {
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = -1L
            override fun source() = okio.Buffer().writeUtf8("x".repeat(SEARCH_BODY_CAP + 1))
        }
        val reply = response("").newBuilder().body(body).build()
        assertTrue(runCatching { FakeCall(reply).await() }.isFailure)
    }

}
