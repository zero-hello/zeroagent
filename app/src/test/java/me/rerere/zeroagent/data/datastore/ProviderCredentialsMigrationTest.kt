package me.rerere.zeroagent.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadParameters
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.KeysetHandle
import kotlinx.coroutines.runBlocking
import me.rerere.zeroagent.data.datastore.migration.PreferenceStoreV6Migration
import me.rerere.zeroagent.data.security.CredentialCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException

class ProviderCredentialsMigrationTest {
    private val mainKey = stringPreferencesKey("providers_encrypted_v1")
    private val archiveKey = stringPreferencesKey("zeroagent_retired_providers_encrypted_v1")
    private val fixture = """[{"apiKey":"test-provider-key","models":[]}]"""

    private fun testAead(): Aead {
        AeadConfig.register()
        val parameters: AeadParameters = AesGcmParameters.builder()
            .setKeySizeBytes(32)
            .setIvSizeBytes(12)
            .setTagSizeBytes(16)
            .build()
        return KeysetHandle.generateNew(parameters).getPrimitive(Aead::class.java)
    }

    @Test
    fun movesPlaintextAndArchivedProvidersInOneMigration() = runBlocking {
        val aead = testAead()
        val cipher = CredentialCipher({ aead }, { aead })
        val migration = ProviderCredentialsMigration(cipher)
        val archived = """[{"apiKey":"archived-test-provider-key"}]"""
        val prefs = mutablePreferencesOf(
            SettingsStore.PROVIDERS to fixture,
            PreferenceStoreV6Migration.RETIRED_PROVIDERS_ARCHIVE to archived,
        )
        assertTrue(migration.shouldMigrate(prefs))
        val result = migration.migrate(prefs)
        assertNull(result[SettingsStore.PROVIDERS])
        assertNull(result[PreferenceStoreV6Migration.RETIRED_PROVIDERS_ARCHIVE])
        assertNotEquals(fixture, result[mainKey])
        assertEquals(fixture, cipher.decrypt(result[mainKey]!!, mainKey.name))
        assertEquals(archived, cipher.decrypt(result[archiveKey]!!, archiveKey.name))
        assertFalse(migration.shouldMigrate(result))
        assertEquals(result, migration.migrate(result))
    }

    @Test
    fun migrationKeepsLegacyDataWhenEncryptionFailsAndCanRetry() = runBlocking {
        val aead = testAead()
        var canEncrypt = false
        val cipher = CredentialCipher({
            if (!canEncrypt) throw GeneralSecurityException("test key unavailable")
            aead
        }, { aead })
        val migration = ProviderCredentialsMigration(cipher)
        val prefs = mutablePreferencesOf(SettingsStore.PROVIDERS to fixture)
        try {
            migration.migrate(prefs)
            throw AssertionError("Migration unexpectedly succeeded")
        } catch (_: IOException) {
            assertEquals(fixture, prefs[SettingsStore.PROVIDERS])
            assertNull(prefs[mainKey])
        }
        canEncrypt = true
        assertEquals(fixture, cipher.decrypt(migration.migrate(prefs)[mainKey]!!, mainKey.name))
    }

    @Test
    fun ciphertextCannotBeReadAsADifferentRecord() {
        val aead = testAead()
        val cipher = CredentialCipher({ aead }, { aead })
        val ciphertext = cipher.encrypt(fixture, mainKey.name)
        org.junit.Assert.assertThrows(IOException::class.java) {
            cipher.decrypt(ciphertext, archiveKey.name)
        }
    }
}
