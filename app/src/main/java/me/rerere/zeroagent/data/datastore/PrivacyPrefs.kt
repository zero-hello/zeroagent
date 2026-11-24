package me.rerere.zeroagent.data.datastore

import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import kotlin.uuid.Uuid
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.zeroagent.data.ai.mcp.McpServerConfig
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.MCP_SERVERS
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.NETWORK_SETTING
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.S3_CONFIG
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.SEARCH_SERVICES
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.SELECTED_TTS_PROVIDER
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.TTS_PROVIDERS
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.WEBDAV_CONFIG
import me.rerere.zeroagent.data.sync.s3.S3Config
import me.rerere.zeroagent.utils.JsonInstant

/** Reads and writes device-local integration preferences that the portable restore preserves. */
internal object PrivacyPrefs {
    private const val TAG = "PreferencesStore"

    fun writeNetwork(preferences: MutablePreferences, settings: Settings, preserveLocalCredentials: Boolean) {
        if (!preserveLocalCredentials) preferences[NETWORK_SETTING] = JsonInstant.encodeToString(settings.networkSetting)
    }

    fun writeSearch(preferences: MutablePreferences, settings: Settings, preserveLocalCredentials: Boolean) {
        if (!preserveLocalCredentials) preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
    }

    fun writeIntegrations(preferences: MutablePreferences, settings: Settings, preserveLocalCredentials: Boolean) {
        if (!preserveLocalCredentials) {
            preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
            preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
            settings.selectedTTSProviderId?.let {
                preferences[SELECTED_TTS_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_TTS_PROVIDER)
        }
    }

    fun networkSetting(preferences: Preferences): NetworkSetting = runCatching {
        JsonInstant.decodeFromString<NetworkSetting>(preferences[NETWORK_SETTING] ?: "{}")
    }.getOrElse {
        Log.w(TAG, "Failed to decode networkSetting, using default", it)
        NetworkSetting()
    }

    fun searchServices(preferences: Preferences): List<SearchServiceOptions> = preferences[SEARCH_SERVICES]?.let { raw ->
        runCatching { JsonInstant.decodeFromString<List<SearchServiceOptions>>(raw) }.getOrElse {
            Log.w(TAG, "Failed to decode searchServices, using default", it)
            listOf(SearchServiceOptions.DEFAULT)
        }
    } ?: listOf(SearchServiceOptions.DEFAULT)

    fun mcpServers(preferences: Preferences): List<McpServerConfig> = preferences[MCP_SERVERS]?.let { raw ->
        runCatching { JsonInstant.decodeFromString<List<McpServerConfig>>(raw) }.getOrElse {
            Log.w(TAG, "Failed to decode mcpServers, using default", it)
            emptyList()
        }
    } ?: emptyList()

    fun webDavConfig(preferences: Preferences): WebDavConfig = preferences[WEBDAV_CONFIG]?.let { raw ->
        runCatching { JsonInstant.decodeFromString<WebDavConfig>(raw) }.getOrElse {
            Log.w(TAG, "Failed to decode webDavConfig, using default", it)
            WebDavConfig()
        }
    } ?: WebDavConfig()

    fun s3Config(preferences: Preferences): S3Config = preferences[S3_CONFIG]?.let { raw ->
        runCatching { JsonInstant.decodeFromString<S3Config>(raw) }.getOrElse {
            Log.w(TAG, "Failed to decode s3Config, using default", it)
            S3Config()
        }
    } ?: S3Config()

    fun ttsProviders(preferences: Preferences): List<TTSProviderSetting> = preferences[TTS_PROVIDERS]?.let { raw ->
        runCatching { JsonInstant.decodeFromString<List<TTSProviderSetting>>(raw) }.getOrElse {
            Log.w(TAG, "Failed to decode ttsProviders, using default", it)
            emptyList()
        }
    } ?: emptyList()

    fun selectedTTSProviderId(preferences: Preferences): Uuid = preferences[SELECTED_TTS_PROVIDER]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        ?: DEFAULT_SYSTEM_TTS_ID
}
