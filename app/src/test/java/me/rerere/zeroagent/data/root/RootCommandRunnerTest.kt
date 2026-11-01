package me.rerere.zeroagent.data.root

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class RootCommandRunnerTest {
    private class Shell(
        private val bytes: InputStream,
        private val exit: Int = 0,
        private val completes: Boolean = true,
    ) : Process() {
        var destroyed = false
        override fun getInputStream() = bytes
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor() = exit
        override fun waitFor(timeout: Long, unit: TimeUnit) = completes
        override fun exitValue() = exit
        override fun destroy() { destroyed = true; bytes.close() }
        override fun destroyForcibly(): Process { destroy(); return this }
    }

    @Test fun preservesRealExitAndOutput() {
        val process = Shell(ByteArrayInputStream("permission denied".toByteArray()), 13)
        val result = RootCommandRunner.collect(process, 30)
        assertEquals(13, result.exitCode)
        assertEquals("permission denied", result.output)
        assertTrue(process.destroyed)
    }

    @Test fun capsOutputWithoutWaitingForTheProducerToStop() {
        val process = Shell(ByteArrayInputStream(ByteArray(100_000) { 'x'.code.toByte() }))
        val result = RootCommandRunner.collect(process, 30)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.startsWith("x".repeat(65_536)))
        assertTrue(result.output.endsWith("[Output shortened: 34464 more bytes]"))
        assertTrue(result.output.length < 65_600)
        assertTrue(process.destroyed)
    }

    @Test fun timeoutIsFailureAndDestroysTheShell() {
        val process = Shell(ByteArrayInputStream(byteArrayOf()), completes = false)
        val result = RootCommandRunner.collect(process, 1)
        assertEquals(-1, result.exitCode)
        assertTrue(result.output.contains("timed out"))
        assertTrue(result.output.contains("changes already made remain"))
        assertTrue(process.destroyed)
    }

    @Test(timeout = 5000) fun detachedOutputPipeDoesNotHangTheCaller() {
        val closed = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int { closed.await(); return -1 }
            override fun close() { closed.countDown() }
        }
        val process = Shell(stream)
        val result = RootCommandRunner.collect(process, 30)
        assertEquals(-1, result.exitCode)
        assertTrue(result.output.contains("Background root jobs are not supported"))
        assertTrue(process.destroyed)
    }

    @Test fun outputReadFailureDoesNotPretendToSucceed() {
        val process = Shell(object : InputStream() {
            override fun read(): Int = throw java.io.IOException("closed")
        })
        val result = RootCommandRunner.collect(process, 30)
        assertEquals(-1, result.exitCode)
        assertEquals("Could not read the command output.", result.output)
        assertTrue(process.destroyed)
    }
}
