package me.rerere.zeroagent.browser

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private val Context.browserDataStore by preferencesDataStore(name = "browser_prefs")

/**
 * DataStore-backed per-tool toggle store for the in-app browser. Mirrors
 * [me.rerere.zeroagent.data.telegram.TelegramBotPreferences] in shape: a single store,
 * suspend reads/writes, and an observable Flow.
 *
 * Pass 1 lays the wiring. Pass 2's tool factories will call [isToolEnabled] before
 * registering each tool — that's how the spec's "master toggle ON + per-tool toggle ON"
 * gate is enforced. Pass 1 doesn't touch tool registration.
 *
 * Defaults come from [BrowserToolDefaults.DEFAULT_ENABLED] so users get a sensible
 * read-only browser on first install: navigate, screenshot, read text — but no clicks
 * or JS until they explicitly opt in.
 */
data class AgentBrowserDisplay(
    val previewExpanded: Boolean = false,
    val desktop: Boolean = false,
    val javaScript: Boolean = true,
    val images: Boolean = true,
    val userAgent: String = "",
    val pageTimeoutSeconds: Long = 8,
)

class BrowserPreferences(private val context: Context) {

    private val store = context.browserDataStore

    private fun keyFor(toolName: String) = booleanPreferencesKey("tool_$toolName")

    // --- Timeout settings (GitHub issue #4) --------------------------------------------------

    private val perToolTimeoutKey = longPreferencesKey("per_tool_timeout_ms")
    private val singleTaskTimeoutKey = longPreferencesKey("single_task_timeout_ms")
    private val previewKey = booleanPreferencesKey("agent_preview_expanded")
    private val desktopKey = booleanPreferencesKey("agent_desktop")
    private val javaScriptKey = booleanPreferencesKey("agent_javascript")
    private val imagesKey = booleanPreferencesKey("agent_images")
    private val userAgentKey = stringPreferencesKey("agent_user_agent")
    private val pageTimeoutKey = longPreferencesKey("agent_page_timeout_seconds")

    private fun readDisplay(prefs: Preferences) = AgentBrowserDisplay(
        previewExpanded = prefs[previewKey] ?: false,
        desktop = prefs[desktopKey] ?: false,
        javaScript = prefs[javaScriptKey] ?: true,
        images = prefs[imagesKey] ?: true,
        userAgent = prefs[userAgentKey].orEmpty(),
        pageTimeoutSeconds = (prefs[pageTimeoutKey] ?: 8).coerceIn(2, 60),
    )

    fun displayFlow(): Flow<AgentBrowserDisplay> = store.data.map(::readDisplay).distinctUntilChanged()

    suspend fun updateDisplay(transform: (AgentBrowserDisplay) -> AgentBrowserDisplay) {
        store.edit { prefs ->
            val display = transform(readDisplay(prefs))
            prefs[previewKey] = display.previewExpanded
            prefs[desktopKey] = display.desktop
            prefs[javaScriptKey] = display.javaScript
            prefs[imagesKey] = display.images
            prefs[pageTimeoutKey] = display.pageTimeoutSeconds.coerceIn(2, 60)
            prefs[userAgentKey] = display.userAgent.filter { it in ' '..'~' }.take(512).trim()
        }
    }

    init {
        // Push the persisted (or default) timeout values into [BrowserController] — the
        // synchronous runtime source of truth read by every browser tool's withTimeoutOrNull
        // and the task-window check. BrowserPreferences is a Koin singleton constructed at
        // app start, so this collector arms the values before the first tool call and keeps
        // them in sync when the user edits them in Settings → Browser.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch { displayFlow().collect { BrowserController.agentDisplay = it } }
        scope.launch {
            perToolTimeoutFlow()
                .distinctUntilChanged()
                .onEach { BrowserController.perToolTimeoutMs = it }
                .collect {}
        }
        scope.launch {
            singleTaskTimeoutFlow()
                .distinctUntilChanged()
                .onEach { BrowserController.singleTaskTimeoutMs = it }
                .collect {}
        }
    }

    /** Per-tool timeout (ms). Missing-key safe; always clamped into the supported range. */
    fun perToolTimeoutFlow(): Flow<Long> = store.data.map { prefs ->
        BrowserToolDefaults.clampPerToolTimeoutMs(
            prefs[perToolTimeoutKey] ?: BrowserToolDefaults.DEFAULT_PER_TOOL_TIMEOUT_MS
        )
    }

    /** Single-task timeout (ms). Missing-key safe; always clamped into the supported range. */
    fun singleTaskTimeoutFlow(): Flow<Long> = store.data.map { prefs ->
        BrowserToolDefaults.clampSingleTaskTimeoutMs(
            prefs[singleTaskTimeoutKey] ?: BrowserToolDefaults.DEFAULT_SINGLE_TASK_TIMEOUT_MS
        )
    }

    /** Persist the per-tool timeout. Input is clamped before write so the store never holds
     *  an out-of-range value. */
    suspend fun setPerToolTimeoutMs(ms: Long) {
        store.edit { it[perToolTimeoutKey] = BrowserToolDefaults.clampPerToolTimeoutMs(ms) }
    }

    /** Persist the single-task timeout. Input is clamped before write. */
    suspend fun setSingleTaskTimeoutMs(ms: Long) {
        store.edit { it[singleTaskTimeoutKey] = BrowserToolDefaults.clampSingleTaskTimeoutMs(ms) }
    }

    /**
     * Reads the current toggle state for [toolName], falling back to
     * [BrowserToolDefaults.DEFAULT_ENABLED] when no value has been written yet.
     */
    suspend fun isToolEnabled(toolName: String): Boolean {
        val prefs = store.data.first()
        return prefs[keyFor(toolName)] ?: BrowserToolDefaults.DEFAULT_ENABLED[toolName] ?: false
    }

    suspend fun setToolEnabled(toolName: String, enabled: Boolean) {
        setToolsEnabled(listOf(toolName), enabled)
    }

    suspend fun setToolsEnabled(tools: Collection<String>, enabled: Boolean) {
        store.edit { prefs ->
            tools.filter { it in BrowserToolDefaults.ALL_TOOLS }.forEach { prefs[keyFor(it)] = enabled }
        }
    }

    suspend fun useToolPreset(interactive: Boolean) {
        store.edit { prefs ->
            BrowserToolDefaults.ALL_TOOLS.forEach { tool ->
                prefs[keyFor(tool)] = tool in BrowserToolDefaults.READ_TOOLS ||
                    tool in BrowserToolDefaults.LOOP_CONTROL_TOOLS ||
                    (interactive && tool in BrowserToolDefaults.WRITE_TOOLS && tool != BrowserToolDefaults.EVAL_JS)
            }
        }
    }

    /**
     * Observe the full enabled-map. Emits one entry per tool in [BrowserToolDefaults.ALL_TOOLS],
     * pre-merged with defaults so callers don't have to do the same fallback dance.
     */
    fun observeAll(): Flow<Map<String, Boolean>> = store.data.map { prefs ->
        BrowserToolDefaults.ALL_TOOLS.associateWith { tool ->
            prefs[keyFor(tool)] ?: (BrowserToolDefaults.DEFAULT_ENABLED[tool] ?: false)
        }
    }

    /**
     * One-shot read of the full enabled-map. Useful for Pass 2's tool registration block
     * which runs synchronously during LocalTools.getTools() construction.
     */
    suspend fun snapshot(): Map<String, Boolean> {
        val prefs: Preferences = store.data.first()
        return BrowserToolDefaults.ALL_TOOLS.associateWith { tool ->
            prefs[keyFor(tool)] ?: (BrowserToolDefaults.DEFAULT_ENABLED[tool] ?: false)
        }
    }

    /**
     * Blocking variant of [snapshot] for callers that aren't already in a coroutine.
     * Used by [me.rerere.zeroagent.data.ai.tools.LocalTools.getTools] which is non-suspend
     * (called from ChatService / CronJobWorker / etc — all already on background dispatchers).
     *
     * DataStore caches the latest [Preferences] instance after the first read, so steady-state
     * cost is a flow `.first()` against an in-memory replay — measured in microseconds. The
     * ONLY scenario this could stall is the very first read after process start before the
     * file is decoded; the alternative (forcing every getTools caller to be suspend) ripples
     * through the entire generation path for what is, at most, a single-digit-millisecond hit.
     */
    fun snapshotBlocking(): Map<String, Boolean> = kotlinx.coroutines.runBlocking { snapshot() }
}
