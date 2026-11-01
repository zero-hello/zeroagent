package me.rerere.zeroagent.ui.pages.setting.components

import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.ai.provider.RealtimeVoiceSetting
import me.rerere.zeroagent.data.ai.realtime.RealtimeModelOption
import me.rerere.zeroagent.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceModelSelectionTest {
    private fun select(provider: ProviderSetting.Google, voice: String): Settings = Settings(
        providers = listOf(provider),
        voiceConversation = RealtimeVoiceSetting(providerId = provider.id.toString(), voice = voice,
            protocol = RealtimeVoiceProtocol.GEMINI),
    ).withVoiceModel(provider, RealtimeModelOption("gemini-test-live", "Test Live"), RealtimeVoiceProtocol.GEMINI)

    @Test fun obsoleteMainVoiceDoesNotBreakOfficialGeminiCalls() {
        assertEquals("", select(ProviderSetting.Google(), "main").voiceConversation.voice)
        assertEquals("Kore", select(ProviderSetting.Google(), "Kore").voiceConversation.voice)
    }
    @Test fun customGatewayVoiceNamesRemainUserControlled() {
        val gateway = ProviderSetting.Google(baseUrl = "https://voice.example/v1beta")
        assertEquals("main", select(gateway, "main").voiceConversation.voice)
    }
}
