package me.rerere.ai.provider

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModelGenerationSettingsTest {
    @Test fun `legacy models default to server sampling without injecting a label`() {
        val m = Json.decodeFromString<Model>("""{"modelId":"fixture"}""")
        assertNull(m.generation.temperature); assertNull(m.generation.topK)
        assertEquals("", m.generation.systemPrompt)
        assertEquals("", m.behaviorProfileId)
    }
    @Test fun `model controls and safety overrides survive serialization`() {
        val model = Model(generation = ModelGenerationSettings(systemPrompt = "Custom instruction", temperature = .4f, topK = 19, maxTokens = 900))
        val decoded = Json.decodeFromString<Model>(Json.encodeToString(Model.serializer(), model))
        assertEquals(model.generation, decoded.generation)
        val setting = ProviderSetting.Google(safetySettings = GeminiSafetySettings(mapOf("HARM_CATEGORY_HARASSMENT" to GeminiSafetyThreshold.BLOCK_ONLY_HIGH)))
        val restored = Json.decodeFromString(ProviderSetting.Google.serializer(), Json.encodeToString(ProviderSetting.Google.serializer(), setting))
        assertEquals(setting.safetySettings, restored.safetySettings)
    }
    @Test fun `declared capabilities override inferred sampling but prompts remain supported`() {
        val model = Model(modelId = "custom", supportedParameters = listOf("top_k", "max_output_tokens"))
        assertTrue(ModelParameterCapabilities.supports(model, ProviderSetting.OpenAI(), "top_k"))
        assertTrue(ModelParameterCapabilities.supports(model, ProviderSetting.OpenAI(), "max_tokens"))
        assertTrue(ModelParameterCapabilities.supports(model, ProviderSetting.OpenAI(), "system_prompt"))
        assertFalse(ModelParameterCapabilities.supports(model, ProviderSetting.OpenAI(), "temperature"))
    }
}
