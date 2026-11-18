package me.rerere.tts.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ZeroAgentSpeechCatalogTest {
    @Test fun `voice catalogs offer mainstream services and system speech only`() {
        assertEquals(setOf(TTSProviderSetting.OpenAI::class, TTSProviderSetting.Gemini::class,
            TTSProviderSetting.SystemTTS::class, TTSProviderSetting.Groq::class,
            TTSProviderSetting.XAI::class, TTSProviderSetting.ElevenLabs::class), TTSProviderSetting.Types.toSet())

    }
}
