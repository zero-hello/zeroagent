package me.rerere.zeroagent.data.root

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Bounded output and waits. A detached shell child cannot keep the app awaiting a pipe. */
internal object RootCommandRunner {
    fun collect(process: Process, timeoutSeconds: Int, onOutput: ((String) -> Unit)? = null): RootResult {
        val kept = ByteArrayOutputStream()
        var discarded = 0L
        val outputLock = Any()
        val varState = VolatileState()
        val reader = Thread({
            var lastPublished = ""
            var lastPublishedAt = 0L
            fun publish(snapshot: String, force: Boolean = false) {
                val now = System.nanoTime()
                if (snapshot != lastPublished && (force || now - lastPublishedAt >= 50_000_000L)) {
                    lastPublished = snapshot
                    lastPublishedAt = now
                    // Observers cannot interrupt pipe draining or leave the child blocked.
                    onOutput?.let { observer -> runCatching { observer(snapshot) } }
                }
            }
            try {
                val buffer = ByteArray(4096)
                process.inputStream.use { stream ->
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        val snapshot = synchronized(outputLock) {
                            val count = minOf(n, (65536 - kept.size()).coerceAtLeast(0))
                            kept.write(buffer, 0, count)
                            discarded += n - count
                            if (count > 0) kept.toString("UTF-8") else null
                        }
                        snapshot?.let { publish(it) }
                    }
                }
                publish(synchronized(outputLock) { kept.toString("UTF-8") }, force = true)
            } catch (_: java.io.IOException) { varState.failed = true }
            finally { varState.finished = true }
        }, "zeroagent-root-output").apply { isDaemon = true }
        try {
            process.outputStream.close()
            reader.start()
            if (!process.waitFor(timeoutSeconds.coerceIn(1, 60).toLong(), TimeUnit.SECONDS)) {
                return RootResult(-1, "命令執行逾時，已停止命令程序；先前完成的變更仍會保留。")
            }
            reader.join(2000)
            if (!varState.finished) return RootResult(-1, "輸出管道仍未關閉；此處不支援背景 Root 工作。")
            if (varState.failed) return RootResult(-1, "無法讀取命令輸出。")
            val text = synchronized(outputLock) {
                kept.toString("UTF-8") + if (discarded > 0) "\n[輸出已縮短：另有 $discarded 位元組]" else ""
            }
            return RootResult(process.exitValue(), text)
        } finally { process.destroyForcibly() }
    }
    private class VolatileState {
        @Volatile var finished = false
        @Volatile var failed = false
    }
}
