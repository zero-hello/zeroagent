package me.rerere.tts.provider

import me.rerere.asr.ASRProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class ZeroAgentSpeechCatalogTest {
    @Test fun `voice catalogs offer mainstream services and system speech only`() {
        assertEquals(setOf(TTSProviderSetting.OpenAI::class, TTSProviderSetting.Gemini::class,
            TTSProviderSetting.SystemTTS::class, TTSProviderSetting.Groq::class,
            TTSProviderSetting.XAI::class, TTSProviderSetting.ElevenLabs::class), TTSProviderSetting.Types.toSet())
        assertEquals(listOf(ASRProviderSetting.OpenAIRealtime::class, ASRProviderSetting.Cloud::class), ASRProviderSetting.Types)
        assertEquals(setOf("GROQ", "DEEPGRAM", "ASSEMBLYAI", "GOOGLE_CLOUD", "AZURE", "ELEVENLABS", "GEMINI"),
            me.rerere.asr.CloudSpeechService.entries.map { it.name }.toSet())
    }
}
