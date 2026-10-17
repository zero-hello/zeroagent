package me.rerere.zeroagent.data.root

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Bounded output and waits. A detached shell child cannot keep the app awaiting a pipe. */
internal object RootCommandRunner {
    fun collect(process: Process, timeoutSeconds: Int): RootResult {
        val kept = ByteArrayOutputStream()
        var discarded = 0L
        val outputLock = Any()
        val varState = VolatileState()
        val reader = Thread({
            try {
                val buffer = ByteArray(4096)
                process.inputStream.use { stream ->
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        synchronized(outputLock) {
                            val count = minOf(n, (65536 - kept.size()).coerceAtLeast(0))
                            kept.write(buffer, 0, count)
                            discarded += n - count
                        }
                    }
                }
            } catch (_: java.io.IOException) { varState.failed = true }
            finally { varState.finished = true }
        }, "zeroagent-root-output").apply { isDaemon = true }
        try {
            process.outputStream.close()
            reader.start()
            if (!process.waitFor(timeoutSeconds.coerceIn(1, 60).toLong(), TimeUnit.SECONDS)) {
                return RootResult(-1, "The command timed out. ZeroAgent stopped its shell process; changes already made remain.")
            }
            reader.join(2000)
            if (!varState.finished) return RootResult(-1, "The output pipe is still open. Background root jobs are not supported.")
            if (varState.failed) return RootResult(-1, "Could not read the command output.")
            val text = synchronized(outputLock) {
                kept.toString("UTF-8") + if (discarded > 0) "\n[Output shortened: $discarded more bytes]" else ""
            }
            return RootResult(process.exitValue(), text)
        } finally { process.destroyForcibly() }
    }
    private class VolatileState {
        @Volatile var finished = false
        @Volatile var failed = false
    }
}
