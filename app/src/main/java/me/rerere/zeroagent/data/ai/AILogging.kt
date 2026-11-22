package me.rerere.zeroagent.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.zeroagent.AppScope
import me.rerere.zeroagent.data.datastore.AiLogLevel
import me.rerere.zeroagent.data.datastore.SettingsStore

sealed class AILogging {
    data class Generation(
        val inputMessageCount: Int,
        val availableToolCount: Int,
        val stream: Boolean,
    ) : AILogging()
}

private const val MAX_LOGS = 32

class AILoggingManager(
    private val settingsStore: SettingsStore,
    appScope: AppScope,
) {
    private val logs = MutableStateFlow<List<AILogging>>(emptyList())
    private val logLevel = MutableStateFlow(settingsStore.settingsFlow.value.aiLogLevel)

    init {
        appScope.launch {
            settingsStore.settingsFlow
                .map { it.aiLogLevel }
                .collectLatest { logLevel.value = it }
        }
    }

    fun getLogs(): StateFlow<List<AILogging>> = logs

    fun getLogLevel(): StateFlow<AiLogLevel> = logLevel

    suspend fun setLogLevel(level: AiLogLevel) {
        settingsStore.update { it.copy(aiLogLevel = level) }
    }

    fun addLog(log: AILogging) {
        if (!settingsStore.settingsFlow.value.developerMode || logLevel.value == AiLogLevel.OFF) return
        logs.update { entries -> (entries + log).takeLast(MAX_LOGS) }
    }

    fun clearLogs() {
        logs.value = emptyList()
    }
}
