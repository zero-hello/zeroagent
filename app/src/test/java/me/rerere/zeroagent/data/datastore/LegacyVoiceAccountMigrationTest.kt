package me.rerere.zeroagent.data.datastore

import me.rerere.ai.provider.*
import org.junit.Assert.*
import org.junit.Test

class LegacyVoiceAccountMigrationTest {
    @Test fun `Gemini dictation credentials migrate without inventing a native model`() {
        val raw = """[{"type":"cloud_speech","service":"GEMINI","name":"Gemini","apiKey":"fixture-not-a-key","endpoint":"https://voice.example/v1beta","model":"transcription-model"}]"""
        val migrated = migrateLegacyVoiceAccount(emptyList(), RealtimeVoiceSetting(), raw, null)
        assertTrue(migrated.providers.single() is ProviderSetting.Google)
        assertEquals("fixture-not-a-key", (migrated.providers.single() as ProviderSetting.Google).apiKey)
        assertFalse(migrated.voice.enabled); assertEquals("", migrated.voice.modelId)
        assertEquals(migrated.providers.single().id.toString(), migrated.voice.providerId)
    }
    @Test fun `existing voice settings are never replaced by legacy credentials`() {
        val current = RealtimeVoiceSetting(providerId = "chosen", modelId = "realtime-model", enabled = true)
        assertEquals(current, migrateLegacyVoiceAccount(emptyList(), current, "[]", null).voice)
    }
    @Test fun `STT-only services are not presented as native duplex models`() {
        val raw = """[{"type":"cloud_speech","service":"GROQ","apiKey":"fixture-not-a-key"}]"""
        val result = migrateLegacyVoiceAccount(emptyList(), RealtimeVoiceSetting(), raw, null)
        assertTrue(result.providers.isEmpty()); assertFalse(result.voice.enabled)
    }
}
