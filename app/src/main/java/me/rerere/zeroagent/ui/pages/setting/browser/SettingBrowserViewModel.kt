package me.rerere.zeroagent.ui.pages.setting.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.zeroagent.browser.BrowserPreferences
import me.rerere.zeroagent.browser.BrowserToolDefaults

class SettingBrowserViewModel(
    private val prefs: BrowserPreferences,
) : ViewModel() {

    /** Per-tool enabled map, keyed by [me.rerere.zeroagent.browser.BrowserToolDefaults.ALL_TOOLS]. */
    val toolStates: StateFlow<Map<String, Boolean>> = prefs.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
    )

    /** Per-tool timeout, in milliseconds. Always clamped into the supported range. */
    val perToolTimeoutMs: StateFlow<Long> = prefs.perToolTimeoutFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserToolDefaults.DEFAULT_PER_TOOL_TIMEOUT_MS,
    )

    /** Single-task timeout, in milliseconds. Always clamped into the supported range. */
    val singleTaskTimeoutMs: StateFlow<Long> = prefs.singleTaskTimeoutFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserToolDefaults.DEFAULT_SINGLE_TASK_TIMEOUT_MS,
    )

    val display = prefs.displayFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), me.rerere.zeroagent.browser.AgentBrowserDisplay(),
    )

    fun updateDisplay(transform: (me.rerere.zeroagent.browser.AgentBrowserDisplay) -> me.rerere.zeroagent.browser.AgentBrowserDisplay) {
        viewModelScope.launch { prefs.updateDisplay(transform) }
    }

    fun setToolEnabled(toolName: String, enabled: Boolean) {
        viewModelScope.launch { prefs.setToolEnabled(toolName, enabled) }
    }

    fun setToolsEnabled(tools: Collection<String>, enabled: Boolean) {
        viewModelScope.launch { prefs.setToolsEnabled(tools, enabled) }
    }

    fun useToolPreset(interactive: Boolean) {
        viewModelScope.launch { prefs.useToolPreset(interactive) }
    }

    /** Persist a per-tool timeout given in seconds. Clamping happens in [BrowserPreferences]. */
    fun setPerToolTimeoutSeconds(seconds: Long) {
        viewModelScope.launch { prefs.setPerToolTimeoutMs(seconds * 1_000L) }
    }

    /** Persist a single-task timeout given in minutes. Clamping happens in [BrowserPreferences]. */
    fun setSingleTaskTimeoutMinutes(minutes: Long) {
        viewModelScope.launch { prefs.setSingleTaskTimeoutMs(minutes * 60_000L) }
    }

    fun clearBrowsingData(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val cleared = runCatching {
                me.rerere.zeroagent.browser.HeadlessBrowserSessionPool.eraseProfile()
            }.getOrDefault(false)
            onDone(cleared)
        }
    }
}
