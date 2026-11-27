package me.rerere.zeroagent.data.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RootAccessStatus { DISABLED, UNVERIFIED, CHECKING, AVAILABLE, UNAVAILABLE }

/** Only a deliberate Settings request enables root. Android's superuser app owns its grant. */
class RootAccessManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("zeroagent_root", Context.MODE_PRIVATE)
    val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    val status = mutableStatus.asStateFlow()
    init {
        synchronized(lock) {
            if (!initialized) {
                mutableStatus.value = if (enabled) RootAccessStatus.UNVERIFIED else RootAccessStatus.DISABLED
                initialized = true
            }
        }
    }
    fun disable() {
        synchronized(lock) {
            generation++
            prefs.edit().putBoolean("enabled", false).commit()
            mutableStatus.value = RootAccessStatus.DISABLED
            active?.destroy()
            active?.destroyForcibly()
        }
    }
    suspend fun request(): RootResult {
        val requestGeneration = synchronized(lock) { generation }
        val result = checkGrant(requestGeneration)
        synchronized(lock) {
            if (generation == requestGeneration) {
                val granted = result.exitCode == 0 && Regex("(?:^|\\s)uid=0(?:\\D|$)").containsMatchIn(result.output)
                val saved = prefs.edit().putBoolean("enabled", granted).commit()
                mutableStatus.value = if (granted && saved) RootAccessStatus.AVAILABLE else RootAccessStatus.UNAVAILABLE
            }
        }
        return result
    }

    /** Recheck the system grant without displaying another app confirmation. */
    suspend fun refresh(): RootResult {
        if (!enabled) return RootResult(-1, "Root 存取已關閉，請先到設定啟用。")
        val expected = synchronized(lock) { generation }
        val result = checkGrant(expected)
        synchronized(lock) {
            if (generation == expected) mutableStatus.value = if (result.exitCode == 0 &&
                Regex("(?:^|\\s)uid=0(?:\\D|$)").containsMatchIn(result.output)) RootAccessStatus.AVAILABLE else RootAccessStatus.UNAVAILABLE
        }
        return result
    }

    suspend fun ensureAvailable(): Boolean {
        if (!enabled) return false
        if (status.value == RootAccessStatus.UNAVAILABLE) return false
        if (status.value != RootAccessStatus.AVAILABLE) refresh()
        return enabled && status.value == RootAccessStatus.AVAILABLE
    }

    private suspend fun checkGrant(expected: Long): RootResult {
        mutableStatus.value = RootAccessStatus.CHECKING
        try {
            return execute("id", 30, requesting = true, expectedGeneration = expected)
        } finally {
            synchronized(lock) {
                if (generation == expected && mutableStatus.value == RootAccessStatus.CHECKING) {
                    mutableStatus.value = RootAccessStatus.UNVERIFIED
                }
            }
        }
    }
    suspend fun run(command: String, timeoutSeconds: Int = 30, onOutput: ((String) -> Unit)? = null): RootResult {
        require(command.isNotBlank() && command.length <= 8192 && '\u0000' !in command) { "請輸入不超過 8192 個字元的有效命令。" }
        return execute(command, timeoutSeconds.coerceIn(1, 60), requesting = false,
            expectedGeneration = synchronized(lock) { generation }, onOutput = onOutput)
    }
    private suspend fun execute(command: String, timeout: Int, requesting: Boolean, expectedGeneration: Long, onOutput: ((String) -> Unit)? = null): RootResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!requesting && !enabled) return@withLock RootResult(-1, "Root 存取已關閉，請先到設定啟用。")
                val process = try {
                    synchronized(lock) {
                        if (generation != expectedGeneration) return@withLock RootResult(-1, "Root 授權已撤銷。")
                        if (!requesting && !enabled) return@withLock RootResult(-1, "Root 存取已關閉。")
                        val script = if (requesting) command else RootCommandProtocol.wrap(command)
                        ProcessBuilder("su", "-c", script).redirectErrorStream(true).start().also { active = it }
                    }
                } catch (e: java.io.IOException) {
                    synchronized(lock) {
                        if (generation == expectedGeneration) mutableStatus.value = RootAccessStatus.UNAVAILABLE
                    }
                    return@withLock RootResult(-1, "Root 無法使用，請確認裝置已取得 Root 並允許超級使用者管理工具授權。")
                }
                try {
                    val result = runInterruptible {
                        RootCommandRunner.collect(process, timeout, onOutput = if (requesting || onOutput == null) null else { snapshot ->
                            // Only show output after the shell has proved its real UID.
                            if (RootCommandProtocol.isVerified(snapshot)) onOutput(RootCommandProtocol.cleanOutput(snapshot))
                        })
                    }
                    if (requesting) result else {
                        val verified = RootCommandProtocol.isVerified(result.output)
                        synchronized(lock) {
                            if (generation == expectedGeneration) mutableStatus.value =
                                if (verified) RootAccessStatus.AVAILABLE else RootAccessStatus.UNAVAILABLE
                        }
                        if (verified) result.copy(output = RootCommandProtocol.cleanOutput(result.output))
                        else RootResult(-1, "Root 授權無法使用或無法確認，請到設定重新檢查。\n${result.output}")
                    }
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
        private var initialized = false
        private val mutableStatus = MutableStateFlow(RootAccessStatus.DISABLED)
    }
}
data class RootResult(val exitCode: Int, val output: String)
