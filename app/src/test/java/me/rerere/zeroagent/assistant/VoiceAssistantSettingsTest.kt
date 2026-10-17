package me.rerere.zeroagent.assistant
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
class VoiceAssistantSettingsTest {
    @Test fun `new installs are off and inherited language changes do not touch overrides`() {
        val defaults = Json.decodeFromString<VoiceAssistantSettings>("{}")
        assertFalse(defaults.enabled); assertFalse(defaults.wakeEnabled)
        assertEquals("zh-TW", defaults.sttTag("zh-TW"))
        val selected = defaults.copy(ttsLanguage = "ja-JP", systemTtsVoice = "chosen", modelId = "model")
        assertEquals("en-US", selected.sttTag("en-US")); assertEquals("ja-JP", selected.ttsTag("en-US"))
        assertEquals("chosen", selected.systemTtsVoice)
        assertEquals(selected.ttsLanguage, selected.copy(modelId = "other").ttsLanguage)
        assertEquals(selected.wakePhrase, selected.copy(modelId = "other").wakePhrase)
    }
    @Test fun `text mode waits for the whole TTS queue not the first chunk`() {
        assertFalse(assistantPlaybackFinished(me.rerere.tts.model.PlaybackStatus.Ended, true))
        assertFalse(assistantPlaybackFinished(me.rerere.tts.model.PlaybackStatus.Idle, false))
        assertTrue(assistantPlaybackFinished(me.rerere.tts.model.PlaybackStatus.Ended, false))
    }
    @Test fun `old epochs are rejected after cancellation`() {
        val turns = AssistantTurnEpoch(); val first = turns.next(); assertTrue(turns.valid(first))
        val second = turns.next(); assertFalse(turns.valid(first)); assertTrue(turns.valid(second))
    }
    @Test fun `assistant TTS settings reuse accounts and map only supported language controls`() {
        val config = VoiceAssistantSettings(ttsLanguage = "ja-JP", sttLanguage = "en-US", systemTtsVoice = "voice", speechRate = 1.2f)
        val base = me.rerere.tts.provider.TTSProviderSetting.SystemTTS()
        val system = base.assistantVoice(config, "zh-TW") as me.rerere.tts.provider.TTSProviderSetting.SystemTTS
        assertEquals(base.id, system.id); assertEquals("ja-JP", system.languageTag); assertEquals("voice", system.voiceName)
        val cloud = me.rerere.tts.provider.TTSProviderSetting.XAI(apiKey = "fixture-not-a-key")
        val selected = cloud.assistantVoice(config, "zh-TW") as me.rerere.tts.provider.TTSProviderSetting.XAI
        assertEquals(cloud.id, selected.id); assertEquals(cloud.apiKey, selected.apiKey); assertEquals("ja", selected.language)
        assertEquals("es-MX", (cloud.assistantVoice(config.copy(ttsLanguage = "es-MX"), "en") as me.rerere.tts.provider.TTSProviderSetting.XAI).language)
        val automatic = me.rerere.tts.provider.TTSProviderSetting.OpenAI()
        assertSame(automatic, automatic.assistantVoice(config, "en"))
        assertEquals("en-US", config.sttLanguage)
    }

}
