package me.rerere.zeroagent.data.datastore

import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.uuid.Uuid
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.DELETED_BUILTIN_PROVIDER_IDS
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.VOICE_CONVERSATION
import me.rerere.zeroagent.utils.JsonInstant

/** Reads the provider catalog and its legacy voice settings and writes both through the existing settings transaction. */
internal object ProviderPrefs {
    private const val TAG = "PreferencesStore"

    fun readVoiceAccount(preferences: Preferences): MigratedVoiceAccount = migrateLegacyVoiceAccount(
        decodeProvidersTolerant(ProviderCredentialsMigration.read(preferences) ?: "[]"),
        preferences[VOICE_CONVERSATION]?.let { raw -> runCatching { JsonInstant.decodeFromString<me.rerere.ai.provider.RealtimeVoiceSetting>(raw) }.getOrNull() } ?: me.rerere.ai.provider.RealtimeVoiceSetting(),
        preferences[stringPreferencesKey("asr_providers")], preferences[stringPreferencesKey("selected_asr_provider")],
    )

    fun deletedBuiltInProviderIds(preferences: Preferences): Set<Uuid> = preferences[DELETED_BUILTIN_PROVIDER_IDS]
        ?.let { raw ->
            runCatching {
                JsonInstant.decodeFromString<Set<String>>(raw)
                    .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
                    .toSet()
            }.getOrNull()
        } ?: emptySet()

    fun writeVoice(preferences: MutablePreferences, settings: Settings) {
        preferences[VOICE_CONVERSATION] = JsonInstant.encodeToString(settings.voiceConversation)
    }

    fun write(preferences: MutablePreferences, settings: Settings, preserveLocalCredentials: Boolean) {
        if (preserveLocalCredentials) {
            val incoming = JsonInstant.parseToJsonElement(JsonInstant.encodeToString(settings.providers)) as kotlinx.serialization.json.JsonArray
            val retained = ProviderCredentialsMigration.read(preferences)?.let {
                runCatching { JsonInstant.parseToJsonElement(it) as kotlinx.serialization.json.JsonArray }.getOrNull()
            } ?: kotlinx.serialization.json.JsonArray(emptyList())
            ProviderCredentialsMigration.write(preferences, me.rerere.zeroagent.data.sync.mergeLocalProviderCredentials(incoming, retained).toString())
        } else {
            ProviderCredentialsMigration.write(preferences, JsonInstant.encodeToString(settings.providers))
        }
        preferences[DELETED_BUILTIN_PROVIDER_IDS] = JsonInstant.encodeToString(
            settings.deletedBuiltInProviderIds.map { it.toString() }.toSet()
        )
    }

    /**
     * Per-entry tolerant decode for the persisted `providers` list.
     *
     * Why this exists: the providers list is stored in DataStore as a single JSON array of
     * polymorphic [ProviderSetting] entries. A single-shot `decodeFromString<List<ProviderSetting>>`
     * throws on the entire list as soon as one element has an unknown polymorphic discriminator.
     *
     * Concrete trigger that motivated this: the never-shipped Phase-22A scaffolding seeded a
     * `"type":"local_llamacpp"` entry into DEFAULT_PROVIDERS for early test installs. Deleting
     * the `LlamaCppLocal` subclass would otherwise make decode-of-list throw on those entries
     * → user loses ALL their saved providers (API keys, custom models, the lot).
     *
     * Per-entry decode lets surviving entries land while the unknown one is logged and skipped.
     * Keep this even though `local_llamacpp` now ships: it's good hygiene for any future
     * polymorphic schema change (renamed types, removed types, etc).
     */
    private fun decodeProvidersTolerant(raw: String): List<ProviderSetting> {
        if (raw.isBlank()) return emptyList()
        val array = runCatching {
            JsonInstant.parseToJsonElement(raw) as? JsonArray
        }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            try {
                JsonInstant.decodeFromJsonElement<ProviderSetting>(element)
            } catch (e: SerializationException) {
                Log.w(TAG, "Skipping unrecognised provider entry (${e.javaClass.simpleName})")
                null
            }
        }
    }
}
