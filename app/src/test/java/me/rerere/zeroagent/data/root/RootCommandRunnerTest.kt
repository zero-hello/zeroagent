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
        assertTrue(result.output.endsWith("[輸出已縮短：另有 34464 位元組]"))
        assertTrue(result.output.length < 65_600)
        assertTrue(process.destroyed)
    }

    @Test fun timeoutIsFailureAndDestroysTheShell() {
        val process = Shell(ByteArrayInputStream(byteArrayOf()), completes = false)
        val result = RootCommandRunner.collect(process, 1)
        assertEquals(-1, result.exitCode)
        assertTrue(result.output.contains("逾時"))
        assertTrue(result.output.contains("變更仍會保留"))
        assertTrue(process.destroyed)
    }

    @Test fun fastOutputIsConflatedAndObserverFailureCannotBlockPipeDraining() {
        val process = Shell(ByteArrayInputStream(ByteArray(100_000) { 'x'.code.toByte() }))
        var callbacks = 0
        val result = RootCommandRunner.collect(process, 30) {
            callbacks++
            throw IllegalStateException("UI observer no longer attached")
        }
        assertEquals(0, result.exitCode)
        assertTrue(callbacks in 1..3)
        assertTrue(result.output.startsWith("x".repeat(65_536)))
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
        assertTrue(result.output.contains("不支援背景 Root 工作"))
        assertTrue(process.destroyed)
    }

    @Test fun publishesOutputWhileTheCommandIsStillRunning() {
        val observed = CountDownLatch(1)
        var snapshot = ""
        val process = object : Process() {
            var completed = false
            override fun getInputStream() = ByteArrayInputStream("working\n".toByteArray())
            override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun waitFor() = 0
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
                assertTrue(observed.await(2, TimeUnit.SECONDS))
                assertEquals("working\n", snapshot)
                completed = true
                return true
            }
            override fun exitValue() = 0
            override fun destroy() = Unit
        }
        val result = RootCommandRunner.collect(process, 30) {
            assertFalse(process.completed)
            snapshot = it
            observed.countDown()
        }
        assertEquals("working\n", result.output)
    }

    @Test fun interruptedWaitStopsTheShell() {
        val started = CountDownLatch(1)
        val process = object : Process() {
            @Volatile var destroyed = false
            override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
            override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun waitFor() = 0
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
                started.countDown()
                CountDownLatch(1).await()
                return false
            }
            override fun exitValue() = 0
            override fun destroy() { destroyed = true }
            override fun destroyForcibly(): Process { destroy(); return this }
        }
        val thread = Thread {
            try { RootCommandRunner.collect(process, 30) } catch (_: InterruptedException) { }
        }
        thread.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        thread.interrupt()
        thread.join(2000)
        assertFalse(thread.isAlive)
        assertTrue(process.destroyed)
    }

    @Test fun outputReadFailureDoesNotPretendToSucceed() {
        val process = Shell(object : InputStream() {
            override fun read(): Int = throw java.io.IOException("closed")
        })
        val result = RootCommandRunner.collect(process, 30)
        assertEquals(-1, result.exitCode)
        assertEquals("無法讀取命令輸出。", result.output)
        assertTrue(process.destroyed)
    }
}
