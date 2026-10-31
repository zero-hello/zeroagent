package me.rerere.zeroagent.assistant

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceSetting
import me.rerere.zeroagent.assistant.wake.WakeEnrollment
import me.rerere.zeroagent.assistant.wake.WakeTestAudio
import me.rerere.zeroagent.assistant.wake.WakeSampleQuality
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.nativeVoiceSelection
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class VoiceAssistantSettingsTest {
    @Test fun `new installs are off and use the fixed wake phrase`() {
        val defaults = Json.decodeFromString<VoiceAssistantSettings>("{}")
        assertFalse(defaults.enabled)
        assertFalse(defaults.wakeEnabled)
        assertEquals(VoiceAssistantSettings.FIXED_WAKE_PHRASE, defaults.wakePhrase)
        assertEquals("Hey Zero", VoiceAssistantSettings.FIXED_WAKE_PHRASE)
    }

    @Test fun `language overrides do not change the fixed wake phrase`() {
        val config = VoiceAssistantSettings(wakeLanguage = "zh-TW")
        assertEquals("zh-TW", config.wakeTag("en-US"))
        assertEquals("zh-TW", config.wakeTag("ja-JP"))
        assertEquals(VoiceAssistantSettings.FIXED_WAKE_PHRASE, config.copy(wakeLanguage = "en-US").wakePhrase)
        assertEquals("ja-JP", VoiceAssistantSettings().wakeTag("ja-JP"))
    }

    @Test fun `assistant selects the unified native voice model instead of the chat model`() {
        val text = Model(modelId = "text-model")
        val native = Model(modelId = "native-voice-model")
        val provider = ProviderSetting.OpenAI(enabled = true, apiKey = "fixture-not-a-real-key", models = listOf(text, native))
        val voice = RealtimeVoiceSetting(enabled = true, providerId = provider.id.toString(), modelId = native.modelId)
        val settings = Settings(providers = listOf(provider), chatModelId = text.id,
            voiceAssistant = VoiceAssistantSettings(modelId = text.id.toString()), voiceConversation = voice)

        val selected = settings.nativeVoiceSelection()!!
        assertEquals(native.id, selected.first.id)
        assertEquals(provider.id, selected.second.id)
        assertEquals(voice, selected.first.realtimeVoice)
    }

    @Test fun `missing disabled or unconfigured native voice never falls back to chat`() {
        val model = Model(modelId = "text-model")
        val provider = ProviderSetting.OpenAI(enabled = true, apiKey = "fixture-not-a-real-key", models = listOf(model))
        val base = Settings(providers = listOf(provider), chatModelId = model.id)
        assertNull(base.nativeVoiceSelection())
        val configured = base.copy(voiceConversation = RealtimeVoiceSetting(enabled = true,
            providerId = provider.id.toString(), modelId = model.modelId))
        assertNull(configured.copy(providers = listOf(provider.copy(enabled = false))).nativeVoiceSelection())
        assertNull(configured.copy(providers = listOf(provider.copy(apiKey = ""))).nativeVoiceSelection())
        assertNull(configured.copy(voiceConversation = configured.voiceConversation.copy(enabled = false)).nativeVoiceSelection())
    }

    @Test fun `Gemini native model selection matches the documented models prefix`() {
        val model = Model(modelId = "models/native-voice-test")
        val provider = ProviderSetting.Google(enabled = true, apiKey = "fixture-not-a-real-key", models = listOf(model))
        val settings = Settings(providers = listOf(provider), voiceConversation = RealtimeVoiceSetting(enabled = true,
            providerId = provider.id.toString(), modelId = "native-voice-test"))
        assertEquals(model.id, settings.nativeVoiceSelection()!!.first.id)
    }

    @Test fun `enrollment cannot count a sample before its durable save succeeds`() = runBlocking {
        val enrollment = WakeEnrollment()
        val pcm = WakeTestAudio.phrase()
        WakeSampleQuality.inspect(pcm)
        try {
            enrollment.commit(listOf(pcm)) { throw IOException("storage unavailable") }
            fail("save must fail")
        } catch (_: IOException) { }
        assertTrue(enrollment.samples.isEmpty())
        enrollment.commit(listOf(pcm)) { assertTrue(enrollment.samples.isEmpty()) }
        assertArrayEquals(pcm, enrollment.samples.single())
    }

    @Test fun `old epochs are rejected after cancellation`() {
        val turns = AssistantTurnEpoch()
        val first = turns.next()
        assertTrue(turns.valid(first))
        val second = turns.next()
        assertFalse(turns.valid(first))
        assertTrue(turns.valid(second))
    }
}
