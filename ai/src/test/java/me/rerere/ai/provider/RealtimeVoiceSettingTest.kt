package me.rerere.ai.provider
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
class RealtimeVoiceSettingTest {
    @Test fun `old settings do not silently enable a microphone session`() {
        val setting = Json.decodeFromString<RealtimeVoiceSetting>("{}")
        assertFalse(setting.enabled);assertEquals("", setting.modelId);assertEquals("", setting.providerId)
    }
    @Test fun `selected native model and provider survive settings export`() {
        val setting = RealtimeVoiceSetting(enabled = true, modelId = "native", providerId = "provider", voice = "Kore")
        assertEquals(setting, Json.decodeFromString<RealtimeVoiceSetting>(Json.encodeToString(setting)))
    }
}
