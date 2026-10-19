package me.rerere.locallm

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class ModelResumeTest {
    @get:Rule val folder = TemporaryFolder()
    private val url = "https://example.org/model.gguf"
    private val weights = "GGUF".toByteArray() + ByteArray(256) { it.toByte() }

    private fun partial(target: File, validator: String = "\"v1\"") {
        File("$target.partial").writeBytes(weights.copyOf(80))
        Properties().apply {
            setProperty("url", url); setProperty("validator", validator)
            File("$target.partial.properties").outputStream().use { store(it, null) }
        }
    }
    private fun client(code: Int, body: ByteArray, range: String? = null, etag: String = "\"v1\"",
                       inspect: (okhttp3.Request) -> Unit = {}) = OkHttpClient.Builder().addInterceptor { chain ->
        inspect(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .header("ETag", etag).apply { range?.let { header("Content-Range", it) } }
            .body(body.toResponseBody()).build()
    }.build()

    @Test fun `valid ETag resume preserves prefix and appends exact bytes`() = runBlocking {
        val target = File(folder.root, "model.gguf"); partial(target)
        val events = ModelInstall.download(client(206, weights.copyOfRange(80, weights.size), "bytes 80-259/260") {
            assertEquals("bytes=80-", it.header("Range")); assertEquals("\"v1\"", it.header("If-Range"))
        }, url, target).toList()
        assertTrue(events.last() is ModelInstall.Progress.Done)
        assertArrayEquals(weights, target.readBytes())
    }
    @Test fun `changed ETag or wrong offset never appends or replaces installed model`() = runBlocking {
        for ((range, etag) in listOf("bytes 81-259/260" to "\"v1\"", "bytes 80-259/260" to "\"v2\"")) {
            val target = File(folder.newFolder(), "model.gguf"); target.writeBytes(weights); partial(target)
            val events = ModelInstall.download(client(206, weights.copyOfRange(80, 260), range, etag), url, target).toList()
            assertTrue(events.last() is ModelInstall.Progress.Failed)
            assertArrayEquals(weights, target.readBytes())
        }
    }
    @Test fun `server returning 200 overwrites partial instead of duplicating its prefix`() = runBlocking {
        val target = File(folder.root, "model.gguf"); partial(target)
        val events = ModelInstall.download(client(200, weights, etag = "\"v2\""), url, target).toList()
        assertTrue(events.last() is ModelInstall.Progress.Done)
        assertArrayEquals(weights, target.readBytes())
    }
    @Test fun `wrong hash preserves existing installation`() = runBlocking {
        val target = File(folder.root, "model.gguf"); target.writeText("old installation")
        val events = ModelInstall.download(client(200, weights), url, target,
            expectedSizeBytes = weights.size.toLong(), expectedSha256 = "0".repeat(64)).toList()
        assertTrue(events.last() is ModelInstall.Progress.Failed)
        assertEquals("old installation", target.readText())
    }
    @Test fun `unsafe file names cannot escape model storage`() {
        listOf("..", "../evil.gguf", "a/b.gguf", "a\\b.gguf", "").forEach {
            assertTrue(runCatching { ModelInstall.targetFile(folder.root, LocalRuntime.LlamaCpp, it) }.isFailure)
        }
    }
    @Test fun `cancellation keeps resumable bytes without promoting a partial`() = runBlocking {
        val target = File(folder.root, "model.gguf")
        val content = "GGUF".toByteArray() + ByteArray(300_000)
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val body = object : okhttp3.ResponseBody() {
                private val source = object : ForwardingSource(Buffer().write(content)) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        Thread.sleep(5)
                        if (closed.get()) throw java.io.IOException("closed")
                        return super.read(sink, byteCount.coerceAtMost(8192))
                    }
                    override fun close() { closed.set(true); super.close() }
                }.buffer()
                override fun contentType(): okhttp3.MediaType? = null
                override fun contentLength() = content.size.toLong()
                override fun source() = source
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("test").header("ETag", "\"v1\"").body(body).build()
        }.build()
        ModelInstall.download(transport, url, target)
            .takeWhile { it !is ModelInstall.Progress.Tick }.toList()
        assertFalse(target.exists())
        assertTrue(closed.get())
        assertTrue(File("$target.partial").length() > 0)
        assertTrue(File("$target.partial.properties").isFile)
    }
    @Test fun `416 retries from zero once without keeping a stale range`() = runBlocking {
        val target = File(folder.root, "model.gguf"); partial(target)
        var requests = 0
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            if (requests == 2) assertNull(chain.request().header("Range"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (requests == 1) 416 else 200).message("test").header("ETag", "\"v2\"")
                .body((if (requests == 1) byteArrayOf() else weights).toResponseBody()).build()
        }.build()
        assertTrue(ModelInstall.download(transport, url, target).toList().last() is ModelInstall.Progress.Done)
        assertEquals(2, requests)
        assertArrayEquals(weights, target.readBytes())
    }
    @Test fun `unidentified legacy partial restarts instead of appending unverified data`() = runBlocking {
        val target = File(folder.root, "model.gguf")
        File("$target.partial").writeText("unverified old bytes")
        val transport = client(200, weights) { assertNull(it.header("Range")) }
        assertTrue(ModelInstall.download(transport, url, target).toList().last() is ModelInstall.Progress.Done)
        assertArrayEquals(weights, target.readBytes())
    }
    @Test fun `installation stamp permits registration recovery but does not match a different URL`() = runBlocking {
        val target = File(folder.root, "model.gguf")
        ModelInstall.download(client(200, weights), url, target).toList()
        assertTrue(ModelInstall.isInstalledDownload(target, url))
        assertFalse(ModelInstall.isInstalledDownload(target, "https://other.org/model.gguf"))
        assertFalse(ModelInstall.isInstalledDownload(target, url, "0".repeat(64)))
    }
    @Test fun `a concurrent destination lock prevents a duplicate transfer`() = runBlocking {
        val target = File(folder.root, "model.gguf")
        java.io.RandomAccessFile(File("$target.lock"), "rw").use { handle ->
            handle.channel.lock().use {
                val transport = client(200, weights) { error("A locked destination must not open the network") }
                assertTrue(ModelInstall.download(transport, url, target).toList().last() is ModelInstall.Progress.Failed)
                assertFalse(target.exists())
            }
        }
    }

    @Test fun `416 with matching complete partial verifies and installs without redownloading`() = runBlocking {
        val target = File(folder.root, "model.gguf"); partial(target)
        File("$target.partial").writeBytes(weights)
        var requests = 0
        val transport = client(416, byteArrayOf(), "bytes */260") { requests++ }
        assertTrue(ModelInstall.download(transport, url, target).toList().last() is ModelInstall.Progress.Done)
        assertEquals(1, requests)
        assertArrayEquals(weights, target.readBytes())
    }
    @Test fun `deletion refuses an active transfer and retains the installed file`() {
        val target = File(folder.root, "model.gguf"); target.writeBytes(weights)
        java.io.RandomAccessFile(File("$target.lock"), "rw").use { handle ->
            handle.channel.lock().use { assertTrue(runCatching { ModelInstall.deleteModelFiles(target) }.isFailure) }
        }
        assertArrayEquals(weights, target.readBytes())
        ModelInstall.deleteModelFiles(target)
        assertFalse(target.exists())
    }

}
