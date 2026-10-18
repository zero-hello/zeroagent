package me.rerere.llamacpp

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.sqrt

class LlamaEmbeddingRuntimeTest {
    @get:Rule val temporary = TemporaryFolder()

    private class FakeNative : EmbeddingNative {
        var dim = 3
        var bytes = 1024L
        var output = floatArrayOf(3f, 4f, 0f)
        var closed = 0
        var opens = 0
        var input: ByteArray? = null
        var maxTokens = 0
        var threads = 0
        var onEmbed: (() -> Unit)? = null
        var metadataFailure: RuntimeException? = null
        override fun open(path: ByteArray, maxTokens: Int, nThreads: Int): Long {
            opens++
            this.maxTokens = maxTokens
            threads = nThreads
            return 7L
        }
        override fun dimension(handle: Long): Int {
            metadataFailure?.let { throw it }
            return dim
        }
        override fun modelBytes(handle: Long): Long = bytes
        override fun embed(handle: Long, text: ByteArray): FloatArray {
            input = text
            onEmbed?.invoke()
            return output
        }
        override fun close(handle: Long) { closed++ }
    }

    private fun open(native: FakeNative = FakeNative()): LlamaEmbeddingRuntime {
        val file = temporary.newFile()
        file.writeBytes(byteArrayOf(1))
        return LlamaEmbeddingRuntime.open(file.path, 128, 2, native)
    }

    @Test fun `reads actual width and normalizes without altering native buffer`() {
        val native = FakeNative()
        open(native).use { runtime ->
            assertEquals(3, runtime.dimension)
            assertEquals(1024L, runtime.modelBytes)
            assertEquals(128, runtime.maxTokens)
            assertEquals(128, native.maxTokens)
            assertEquals(2, native.threads)
            assertArrayEquals(floatArrayOf(.6f, .8f, 0f), runtime.embed("query: 測試😀"), 0.000001f)
            assertArrayEquals(floatArrayOf(3f, 4f, 0f), native.output, 0f)
        }
        assertEquals(1, native.closed)
    }

    @Test fun `preserves caller prefix and standard UTF8 including supplementary and NUL`() {
        val native = FakeNative()
        open(native).use {
            val text = "passage: 繁體中文😀\u0000next"
            it.embed(text)
            assertArrayEquals(text.toByteArray(Charsets.UTF_8), native.input)
        }
    }

    @Test fun `rejects nonfinite zero and wrong width outputs`() {
        val native = FakeNative()
        open(native).use { runtime ->
            for (invalid in listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(Float.NaN, 0f, 1f),
                floatArrayOf(Float.POSITIVE_INFINITY, 0f, 1f), floatArrayOf(Float.NEGATIVE_INFINITY, 0f, 1f),
                floatArrayOf(1f, 2f), floatArrayOf())) {
                native.output = invalid
                assertThrows(IllegalStateException::class.java) { runtime.embed("query: test") }
            }
        }
    }

    @Test fun `normalizes extreme finite values without float overflow or underflow`() {
        for (value in listOf(Float.MAX_VALUE, Float.MIN_VALUE, -Float.MAX_VALUE)) {
            val output = normalizeEmbedding(floatArrayOf(value, value), 2)
            assertTrue(output.all { it.isFinite() })
            assertEquals(1.0, sqrt(output.sumOf { it.toDouble() * it }), 0.000001)
        }
    }

    @Test fun `rejects invalid expected dimensions`() {
        for (dim in listOf(0, -1, 1025)) {
            assertThrows(IllegalStateException::class.java) { normalizeEmbedding(FloatArray(0), dim) }
        }
    }

    @Test fun `input bounds include UTF8 byte count and reject blank text before JNI`() {
        val native = FakeNative()
        open(native).use { runtime ->
            for (text in listOf("", " \n\t", "x".repeat(32769), "中".repeat(10923))) {
                assertThrows(IllegalArgumentException::class.java) { runtime.embed(text) }
                assertNull(native.input)
            }
            runtime.embed("x".repeat(32768))
            assertEquals(32768, native.input!!.size)
        }
    }

    @Test fun `close is idempotent and closed runtime rejects inference`() {
        val native = FakeNative()
        val runtime = open(native)
        runtime.close()
        runtime.close()
        assertEquals(1, native.closed)
        assertThrows(IllegalStateException::class.java) { runtime.embed("query: test") }
    }

    @Test fun `invalid metadata frees already opened native resources`() {
        for (dim in listOf(0, -1, 1025)) {
            val native = FakeNative().apply { this.dim = dim }
            assertThrows(IllegalStateException::class.java) { open(native) }
            assertEquals(1, native.closed)
        }
        for (bytes in listOf(0L, LlamaEmbeddingRuntime.MAX_MODEL_BYTES + 1)) {
            val native = FakeNative().apply { this.bytes = bytes }
            assertThrows(IllegalStateException::class.java) { open(native) }
            assertEquals(1, native.closed)
        }
    }

    @Test fun `metadata exception preserves failure and closes native handle`() {
        val failure = IllegalStateException("metadata failed")
        val native = FakeNative().apply { metadataFailure = failure }
        assertSame(failure, assertThrows(IllegalStateException::class.java) { open(native) })
        assertEquals(1, native.closed)
    }

    @Test fun `rejects invalid configuration and files before opening model`() {
        val file = temporary.newFile().apply { writeBytes(byteArrayOf(1)) }
        val native = FakeNative()
        for (tokens in listOf(0, 7, 513, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                LlamaEmbeddingRuntime.open(file.path, tokens, 2, native)
            }
        }
        for (threads in listOf(0, 5, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                LlamaEmbeddingRuntime.open(file.path, 128, threads, native)
            }
        }
        for (path in listOf("", "bad\u0000path", temporary.root.path, temporary.newFile().path,
            temporary.root.resolve("missing.gguf").path)) {
            assertThrows(IllegalArgumentException::class.java) {
                LlamaEmbeddingRuntime.open(path, 128, 2, native)
            }
        }
        assertEquals(0, native.opens)
    }

    @Test fun `inference failure leaves runtime closable and reusable`() {
        val native = FakeNative().apply { onEmbed = { error("encode failed") } }
        val runtime = open(native)
        assertThrows(IllegalStateException::class.java) { runtime.embed("query: one") }
        native.onEmbed = null
        assertEquals(3, runtime.embed("query: two").size)
        runtime.close()
        assertEquals(1, native.closed)
    }

    @Test fun `close waits until inference has released the native output`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val native = FakeNative().apply {
            onEmbed = {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                check(this.closed == 0)
            }
        }
        val runtime = open(native)
        val worker = thread {
            try { runtime.embed("query: concurrency") } catch (t: Throwable) { failure.set(t) }
        }
        var closer: Thread? = null
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            closer = thread {
                closing.countDown()
                try { runtime.close() } catch (t: Throwable) { failure.set(t) }
                closed.countDown()
            }
            assertTrue(closing.await(5, TimeUnit.SECONDS))
            assertFalse(closed.await(100, TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
            worker.join(5000)
            closer?.join(5000)
            runtime.close()
        }
        assertFalse(worker.isAlive)
        assertFalse(closer?.isAlive ?: false)
        assertNull(failure.get())
        assertEquals(1, native.closed)
    }
}
