package me.rerere.zeroagent.data.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import me.rerere.zeroagent.data.datastore.migration.PreferenceStoreV6Migration
import me.rerere.zeroagent.data.security.CredentialCipher
import java.io.IOException

/** Writes encrypted provider configuration and removes the plaintext keys in one DataStore update. */
internal class ProviderCredentialsMigration(
    private val cipher: CredentialCipher = CredentialCipher.device,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.contains(SettingsStore.PROVIDERS) || currentData.contains(PreferenceStoreV6Migration.RETIRED_PROVIDERS_ARCHIVE)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val updated = currentData.toMutablePreferences()
        for ((legacy, encryptedKey) in KEYS) {
            val plaintext = currentData[legacy] ?: continue
            val ciphertext = currentData[encryptedKey]
            if (ciphertext == null) {
                updated[encryptedKey] = cipher.encrypt(plaintext, encryptedKey.name)
            } else {
                // Refuse to discard the legacy copy when the encrypted value cannot be read.
                if (cipher.decrypt(ciphertext, encryptedKey.name) != plaintext) {
                    throw IOException("Local provider credentials could not be migrated safely")
                }
            }
            updated.remove(legacy)
        }
        return updated.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    companion object {
        private val PROVIDERS = stringPreferencesKey("providers_encrypted_v1")
        private val RETIRED = stringPreferencesKey("zeroagent_retired_providers_encrypted_v1")
        private val KEYS get() = listOf(
            SettingsStore.PROVIDERS to PROVIDERS,
            PreferenceStoreV6Migration.RETIRED_PROVIDERS_ARCHIVE to RETIRED,
        )

        fun read(preferences: Preferences): String? = preferences[PROVIDERS]?.let {
            CredentialCipher.device.decrypt(it, PROVIDERS.name)
        }

        fun write(preferences: MutablePreferences, json: String) {
            if (read(preferences) != json) {
                preferences[PROVIDERS] = CredentialCipher.device.encrypt(json, PROVIDERS.name)
            }
            preferences.remove(SettingsStore.PROVIDERS)
        }
    }
}
