package me.rerere.zeroagent.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.RETIRED_SPEECH_TYPES
import me.rerere.zeroagent.data.datastore.isRetiredProvider
import me.rerere.zeroagent.utils.JsonInstant

/** Retires old catalog entries while keeping their original configuration recoverable. */
class PreferenceStoreV6Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[SettingsStore.VERSION] ?: 0) < 6

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val raw = prefs[SettingsStore.PROVIDERS]
        val providers = raw?.let { runCatching { JsonInstant.parseToJsonElement(it) as JsonArray }.getOrNull() }
        if (providers != null) {
            val (retired, retained) = providers.partition { item ->
                val obj = item as? JsonObject ?: return@partition false
                isRetiredProvider(
                    (obj["id"] as? JsonPrimitive)?.contentOrNull,
                    (obj["baseUrl"] as? JsonPrimitive)?.contentOrNull,
                )
            }
            if (retired.isNotEmpty()) {
                prefs[RETIRED_PROVIDERS_ARCHIVE] = JsonArray(retired).toString()
                prefs[SettingsStore.PROVIDERS] = JsonArray(retained).toString()
            }
        }
        for ((key, archiveKey) in listOf(
            SettingsStore.TTS_PROVIDERS to RETIRED_TTS_ARCHIVE,
            LEGACY_DICTATION_PROVIDERS to RETIRED_ASR_ARCHIVE,
            SettingsStore.SEARCH_SERVICES to RETIRED_SEARCH_ARCHIVE,
        )) {
            val entries = prefs[key]?.let { runCatching { JsonInstant.parseToJsonElement(it) as JsonArray }.getOrNull() } ?: continue
            val (retired, retained) = entries.partition { item ->
                val obj = item as? JsonObject ?: return@partition false
                (obj["type"] as? JsonPrimitive)?.contentOrNull in
                    (if (key == SettingsStore.SEARCH_SERVICES) setOf("rikkahub", "zhipu", "doubao", "metaso", "bocha") else RETIRED_SPEECH_TYPES) ||
                    isRetiredProvider(null, (obj["baseUrl"] as? JsonPrimitive)?.contentOrNull)
            }
            if (retired.isNotEmpty()) {
                prefs[archiveKey] = JsonArray(retired).toString()
                prefs[key] = JsonArray(retained).toString()
            }
        }
        prefs[SettingsStore.VERSION] = 6
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
    companion object {
        val LEGACY_DICTATION_PROVIDERS = stringPreferencesKey("asr_providers")
        val RETIRED_TTS_ARCHIVE = stringPreferencesKey("zeroagent_retired_tts_archive")
        val RETIRED_SEARCH_ARCHIVE = stringPreferencesKey("zeroagent_retired_search_archive")
        val RETIRED_ASR_ARCHIVE = stringPreferencesKey("zeroagent_retired_asr_archive")
        val RETIRED_PROVIDERS_ARCHIVE = stringPreferencesKey("zeroagent_retired_providers_archive")
    }
}
