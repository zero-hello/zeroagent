package me.rerere.ai.provider.stream

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class BoundedSseDeliveryTest {
    private class FakeSse(val count: Int, val tail: Boolean = false, val failAtEnd: Boolean = false) : AutoCloseable {
        val attempts = AtomicInteger()
        val sent = AtomicInteger()
        val saturated = CompletableDeferred<Unit>()
        val sendStopped = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val callbackThread = AtomicReference<Thread>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = (0 until count).joinToString("") { "data: $it\n\n" }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("text/event-stream".toMediaType())).build()
        }.build()

        fun stream(): Flow<Int> = callbackFlow {
            val listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    callbackThread.set(Thread.currentThread())
                    if (attempts.incrementAndGet() == SSE_BUFFER_CAPACITY + 2) saturated.complete(Unit)
                    if (!sendSseChunks(listOf(data.toInt()))) {
                        sendStopped.complete(Unit); eventSource.cancel(); return
                    }
                    sent.incrementAndGet()
                    if (failAtEnd && data.toInt() == count - 1) { close(IOException("fixture stream failure")); eventSource.cancel() }
                }
                override fun onClosed(eventSource: EventSource) {
                    if (tail && !sendSseChunks(listOf(count, count + 1))) { eventSource.cancel(); return }
                    close()
                }
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) { close(t ?: IOException("fixture transport failure")) }
            }
            val source = EventSources.createFactory(client).newEventSource(Request.Builder().url("https://sse.invalid/events").build(), listener)
            awaitClose { source.cancel(); cleaned.complete(Unit) }
        }.buffer(SSE_BUFFER_CAPACITY).flowOn(Dispatchers.IO)

        override fun close() { client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun `fast SSE and slow collector preserve every delta and terminal tail with bounded backlog`() = runBlocking {
        withTimeout(8000) {
            FakeSse(SSE_BUFFER_CAPACITY * 4, tail = true).use { sse ->
                val release = CompletableDeferred<Unit>()
                val seen = mutableListOf<Int>()
                val collecting = async {
                    sse.stream().collect {
                        if (seen.isEmpty()) release.await()
                        seen.add(it); delay(1)
                    }
                }
                try {
                    sse.saturated.await()
                    assertEquals(SSE_BUFFER_CAPACITY + 2, sse.attempts.get())
                    assertEquals(SSE_BUFFER_CAPACITY + 1, sse.sent.get())
                    assertNotSame(Thread.currentThread(), sse.callbackThread.get())
                    // The collector's thread remains free while the OkHttp reader waits for space.
                    yield()
                    assertFalse(collecting.isCompleted)
                    release.complete(Unit)
                    collecting.await()
                    assertEquals((0 until sse.count + 2).toList(), seen)
                    sse.cleaned.await()
                } finally { release.complete(Unit); collecting.cancelAndJoin() }
            }
        }
    }

    @Test fun `collector cancellation releases a reader blocked on a full buffer and cancels the transport`() = runBlocking {
        FakeSse(SSE_BUFFER_CAPACITY * 8).use { sse ->
            var stage = "saturating the reader"
            val collecting = launch { sse.stream().collect { awaitCancellation() } }
            try {
                withTimeout(5000) {
                    sse.saturated.await()
                    stage = "cancelling the collector"
                    collecting.cancelAndJoin()
                    stage = "releasing the blocked send"
                    sse.sendStopped.await()
                    stage = "running awaitClose cleanup"
                    sse.cleaned.await()
                    stage = "releasing the HTTP dispatcher"
                    while (sse.client.dispatcher.runningCallsCount() != 0) delay(1)
                    assertTrue(sse.attempts.get() <= SSE_BUFFER_CAPACITY + 2)
                }
            } catch (timeout: TimeoutCancellationException) {
                throw AssertionError("Timeout while $stage; attempted=${sse.attempts.get()}, sent=${sse.sent.get()}, running=${sse.client.dispatcher.runningCallsCount()}", timeout)
            } finally { collecting.cancelAndJoin() }
        }
    }

    @Test fun `a stream failure drains all accepted deltas before surfacing the error`() = runBlocking {
        withTimeout(5000) {
            FakeSse(SSE_BUFFER_CAPACITY * 2, failAtEnd = true).use { sse ->
                val seen = mutableListOf<Int>()
                val failure = runCatching { sse.stream().collect { seen.add(it); delay(1) } }.exceptionOrNull()
                assertEquals((0 until sse.count).toList(), seen)
                assertTrue(failure is IOException)
                assertEquals("fixture stream failure", failure?.message)
            }
        }
    }

    private class TestProducer(private val output: Channel<Int>) : ProducerScope<Int>, SendChannel<Int> by output {
        override val coroutineContext: CoroutineContext = SupervisorJob()
        override val channel: SendChannel<Int> get() = output
    }

    @Test fun `closed and cancelled channels reject delivery without throwing into decoder catches`() = runBlocking {
        val closed = Channel<Int>(1); closed.close()
        assertFalse(TestProducer(closed).sendSseChunks(listOf(1)))
        val cancelled = Channel<Int>(1); cancelled.cancel()
        assertFalse(TestProducer(cancelled).sendSseChunks(listOf(1)))
    }

    @Test fun `interrupted reader closes the stream instead of silently skipping its delta`() = runBlocking {
        val channel = Channel<Int>(0)
        val started = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        val restoredInterrupt = AtomicReference<Boolean>()
        val reader = thread(name = "fixture-sse-reader") {
            started.countDown()
            result.set(TestProducer(channel).sendSseChunks(listOf(1)))
            restoredInterrupt.set(Thread.currentThread().isInterrupted)
        }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        reader.interrupt(); reader.join(2000)
        assertFalse(reader.isAlive)
        assertEquals(false, result.get())
        assertEquals(true, restoredInterrupt.get())
        assertTrue(channel.receiveCatching().exceptionOrNull() is InterruptedException)
    }
}
