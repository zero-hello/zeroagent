package me.rerere.zeroagent.data.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only a deliberate Settings request enables root. Android's superuser app owns its grant. */
class RootAccessManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("zeroagent_root", Context.MODE_PRIVATE)
    val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    fun disable() {
        synchronized(lock) {
            generation++
            prefs.edit().putBoolean("enabled", false).commit()
            active?.destroy()
            active?.destroyForcibly()
        }
    }
    suspend fun request(): RootResult {
        val requestGeneration = synchronized(lock) { generation }
        val result = execute("id", 30, requesting = true)
        synchronized(lock) {
            if (generation == requestGeneration) prefs.edit().putBoolean("enabled",
                result.exitCode == 0 && Regex("(?:^|\\s)uid=0(?:\\D|$)").containsMatchIn(result.output)).commit()
        }
        return result
    }
    suspend fun run(command: String, timeoutSeconds: Int = 30): RootResult {
        require(command.isNotBlank() && command.length <= 8192 && '\u0000' !in command) { "Enter a command up to 8192 characters." }
        return execute(command, timeoutSeconds.coerceIn(1, 60), requesting = false)
    }
    private suspend fun execute(command: String, timeout: Int, requesting: Boolean): RootResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!requesting && !enabled) return@withLock RootResult(-1, "Root access is off. Enable it in Settings first.")
                val process = try {
                    synchronized(lock) {
                        if (!requesting && !enabled) return@withLock RootResult(-1, "Root access was turned off.")
                        ProcessBuilder("su", "-c", command).redirectErrorStream(true).start().also { active = it }
                    }
                } catch (e: java.io.IOException) {
                    return@withLock RootResult(-1, "Root is unavailable. This phone needs an installed superuser manager.")
                }
                try {
                    runInterruptible { RootCommandRunner.collect(process, timeout) }
                } finally {
                    process.destroyForcibly()
                    synchronized(lock) { if (active === process) active = null }
                }
            }
        }
    companion object {
        private val mutex = Mutex()
        private val lock = Any()
        private var active: Process? = null
        private var generation = 0L
    }
}
data class RootResult(val exitCode: Int, val output: String)
