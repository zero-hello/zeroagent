package me.rerere.zeroagent.data.ai.realtime
import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import org.junit.Assert.*
import org.junit.Test
class RealtimeModelCatalogTest {
    @Test fun `OpenAI catalog excludes transcription and ordinary text models`() {
        val json = Json.parseToJsonElement("""{"data":[{"id":"gpt-realtime"},{"id":"gpt-4o-mini-transcribe"},{"id":"gpt-4o"},{"id":"gpt-4o-realtime-preview"}]}""").jsonObject
        assertEquals(listOf("gpt-4o-realtime-preview", "gpt-realtime"), RealtimeModelCatalog.parse(json, RealtimeVoiceProtocol.OPENAI).map { it.id })
    }
    @Test fun `Gemini retains bidi only models omitted by ordinary chat listing`() {
        val json = Json.parseToJsonElement("""{"models":[{"name":"models/native-test","supportedGenerationMethods":["bidiGenerateContent"]},{"name":"models/text","supportedGenerationMethods":["generateContent"]},{"name":"models/tts","supportedGenerationMethods":["generateContent"]}]}""").jsonObject
        assertEquals(listOf("native-test"), RealtimeModelCatalog.parse(json, RealtimeVoiceProtocol.GEMINI).map { it.id })
    }
    @Test fun `a separate voice provider does not inherit another providers secret headers`() {
        val voiceProvider = ProviderSetting.Google(apiKey = "test-voice")
        val textProvider = ProviderSetting.OpenAI(apiKey = "test-text")
        val model = Model(modelId = "text", providerOverwrite = textProvider,
            customHeaders = listOf(CustomHeader("X-Private", "text-only-secret")),
            realtimeVoice = RealtimeVoiceSetting(modelId = "native", providerId = voiceProvider.id.toString()))
        val connection = RealtimeConnections.create(model, voiceProvider)
        assertFalse(connection.headers.containsKey("X-Private"))
    }
    @Test fun `native model selection is independent of text model`() {
        val model = Model(modelId = "text-model", realtimeVoice = RealtimeVoiceSetting(modelId = "gpt-realtime"))
        val c = RealtimeConnections.create(model, ProviderSetting.OpenAI(apiKey = "test-only"))
        assertTrue(c.url.contains("model=gpt-realtime"));assertFalse(c.url.contains("text-model"))
        assertTrue(GeminiLiveProtocol.setup(model, "test").toString().contains("models/gpt-realtime"))
    }

    @Test fun `explicit Gemini methods exclude misleading live and native audio names`() {
        // https://ai.google.dev/api/models — supportedGenerationMethods is capability metadata.
        val json = Json.parseToJsonElement("""{"models":[
            {"name":"models/gemini-live-text-only","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-native-audio-tts","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-live-disabled","supportedGenerationMethods":[]},
            {"name":"models/gemini-bidi-only","supportedGenerationMethods":["bidiGenerateContent"]},
            {"name":"models/gateway-live"}
        ]}""").jsonObject
        assertEquals(listOf("gateway-live", "gemini-bidi-only"), RealtimeModelCatalog.parse(json, RealtimeVoiceProtocol.GEMINI).map { it.id })
    }
}
