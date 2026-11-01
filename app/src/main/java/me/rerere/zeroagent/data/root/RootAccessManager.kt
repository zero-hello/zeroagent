package me.rerere.zeroagent.data.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

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
            val isRoot = result.exitCode == 0 && Regex("(?:^|\\s)uid=0(?:\\D|$)").containsMatchIn(result.output)
            if (generation == requestGeneration) {
                prefs.edit().putBoolean("enabled", isRoot).commit()
            }
        }
        return result
    }

    suspend fun run(command: String, timeoutSeconds: Int = 30): RootResult {
        require(command.isNotBlank() && command.length <= 8192 && '\u0000' !in command) { "Enter a command up to 8192 characters." }
        return execute(command, timeoutSeconds.coerceIn(1, 60), requesting = false)
    }

    private fun findSuBinary(): String {
        val candidatePaths = listOf(
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su"
        )
        for (candidate in candidatePaths) {
            try {
                val f = File(candidate)
                if (f.exists() && f.canExecute()) {
                    return candidate
                }
            } catch (_: SecurityException) {
                // Ignore permissions on check
            }
        }
        return "su"
    }

    private suspend fun execute(command: String, timeout: Int, requesting: Boolean): RootResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!requesting && !enabled) return@withLock RootResult(-1, "Root access is off. Enable it in Settings first.")
                val suBinary = findSuBinary()
                val augmentedPath = "/system/xbin:/system/bin:/sbin:/vendor/bin:/data/adb/ksu/bin:/data/adb/magisk:${System.getenv("PATH").orEmpty()}"

                // Try interactive stdin pipe first (best for emulators/VMs and KernelSU/Magisk grant prompts)
                val firstAttempt = try {
                    val process = synchronized(lock) {
                        if (!requesting && !enabled) return@withLock RootResult(-1, "Root access was turned off.")
                        val pb = ProcessBuilder(suBinary).redirectErrorStream(true)
                        pb.environment()["PATH"] = augmentedPath
                        pb.start().also { active = it }
                    }
                    try {
                        runInterruptible { RootCommandRunner.collect(process, command, timeout) }
                    } finally {
                        process.destroyForcibly()
                        synchronized(lock) { if (active === process) active = null }
                    }
                } catch (_: IOException) {
                    null
                }

                if (firstAttempt != null && firstAttempt.exitCode == 0 && Regex("(?:^|\\s)uid=0(?:\\D|$)").containsMatchIn(firstAttempt.output)) {
                    return@withLock firstAttempt
                }

                // Fallback to one-shot su -c command
                val secondAttempt = try {
                    val process = synchronized(lock) {
                        if (!requesting && !enabled) return@withLock RootResult(-1, "Root access was turned off.")
                        val pb = ProcessBuilder(suBinary, "-c", command).redirectErrorStream(true)
                        pb.environment()["PATH"] = augmentedPath
                        pb.start().also { active = it }
                    }
                    try {
                        runInterruptible { RootCommandRunner.collect(process, null, timeout) }
                    } finally {
                        process.destroyForcibly()
                        synchronized(lock) { if (active === process) active = null }
                    }
                } catch (e: IOException) {
                    firstAttempt ?: RootResult(-1, "Root is unavailable. This phone needs an installed superuser manager.")
                }

                if (secondAttempt.exitCode == 0) {
                    secondAttempt
                } else {
                    firstAttempt ?: secondAttempt
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
