package me.rerere.zeroagent.ui.pages.setting.components

import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.ai.provider.RealtimeVoiceSetting
import me.rerere.zeroagent.data.ai.realtime.RealtimeModelOption
import me.rerere.zeroagent.data.datastore.Settings
import org.junit.Assert.*
import org.junit.Test

class ZeroAgent12VoiceProviderTest {
    @Test
    fun `Gemini can be added after OpenAI without creating models`() {
        val openai = ProviderSetting.OpenAI(apiKey = "fixture-openai")
        val result = Settings(providers = listOf(openai)).withNativeVoiceProvider(RealtimeVoiceProtocol.GEMINI)
        val gemini = result.providers.filterIsInstance<ProviderSetting.Google>().single()

        assertEquals(2, result.providers.size)
        assertEquals(openai, result.providers.first())
        assertEquals(gemini.id.toString(), result.voiceConversation.providerId)
        assertFalse(result.voiceConversation.enabled)
        assertEquals("", result.voiceConversation.modelId)
        assertTrue(gemini.models.isEmpty())
        assertEquals("", gemini.apiKey)
        assertEquals(result, result.withNativeVoiceProvider(RealtimeVoiceProtocol.GEMINI))
    }

    @Test
    fun `existing Gemini account keeps its id keys endpoint models and headers`() {
        val google = ProviderSetting.Google(
            apiKey = "fixture-google",
            baseUrl = "https://example.test/v1beta",
            models = listOf(Model(modelId = "existing-chat")),
            customHeaders = listOf(CustomHeader("X-Fixture", "shared")),
        )
        val openai = ProviderSetting.OpenAI(apiKey = "fixture-openai")
        val result = Settings(providers = listOf(openai, google))
            .withNativeVoiceProvider(RealtimeVoiceProtocol.GEMINI)

        assertEquals(listOf(openai, google), result.providers)
        assertEquals(google.id.toString(), result.voiceConversation.providerId)
    }

    @Test
    fun `disabled configured Gemini is reused before an empty account`() {
        val empty = ProviderSetting.Google(enabled = false)
        val configured = ProviderSetting.Google(enabled = false, apiKey = "fixture-shared")
        val result = Settings(providers = listOf(empty, configured))
            .withNativeVoiceProvider(RealtimeVoiceProtocol.GEMINI)

        assertEquals(listOf(empty, configured.copy(enabled = true)), result.providers)
        assertEquals(configured.id.toString(), result.voiceConversation.providerId)
    }

    @Test
    fun `existing disabled OpenAI is reused and stale Gemini selection is cleared`() {
        val openai = ProviderSetting.OpenAI(enabled = false, apiKey = "fixture-shared")
        val result = Settings(
            providers = listOf(openai),
            voiceConversation = RealtimeVoiceSetting(
                enabled = true, modelId = "old-model", providerId = "old-account",
                protocol = RealtimeVoiceProtocol.GEMINI, endpoint = "wss://example.test/old",
            ),
        ).withNativeVoiceProvider(RealtimeVoiceProtocol.OPENAI)

        assertEquals(listOf(openai.copy(enabled = true)), result.providers)
        assertEquals(openai.id.toString(), result.voiceConversation.providerId)
        assertEquals(RealtimeVoiceProtocol.AUTO, result.voiceConversation.protocol)
        assertEquals("", result.voiceConversation.endpoint)
        assertEquals("", result.voiceConversation.modelId)
        assertFalse(result.voiceConversation.enabled)
    }

    @Test
    fun `Vertex and service accounts are preserved instead of repurposed for Gemini Live`() {
        val vertex = ProviderSetting.Google(vertexAI = true, apiKey = "fixture-vertex")
        val service = ProviderSetting.Google(useServiceAccount = true)
        val result = Settings(providers = listOf(vertex, service))
            .withNativeVoiceProvider(RealtimeVoiceProtocol.GEMINI)

        assertEquals(3, result.providers.size)
        assertEquals(listOf(vertex, service), result.providers.take(2))
        val native = result.providers.last() as ProviderSetting.Google
        assertFalse(native.vertexAI)
        assertFalse(native.useServiceAccount)
        assertEquals("", native.apiKey)
        assertTrue(native.models.isEmpty())
    }

    @Test
    fun `discovered native model is added once to the same shared account`() {
        val google = ProviderSetting.Google(apiKey = "fixture-shared")
        val selected = RealtimeModelOption("fixture-live-from-catalog", "Catalog live model")
        val result = Settings(providers = listOf(google))
            .withVoiceModel(google, selected, RealtimeVoiceProtocol.GEMINI)
        val saved = result.providers.single() as ProviderSetting.Google

        assertEquals(google.id, saved.id)
        assertEquals(google.apiKey, saved.apiKey)
        assertEquals(selected.id, saved.models.single().modelId)
        assertEquals(selected.id, result.voiceConversation.modelId)
        assertEquals(saved.id.toString(), result.voiceConversation.providerId)
        assertTrue(result.voiceConversation.enabled)
        assertEquals(result, result.withVoiceModel(saved, selected, RealtimeVoiceProtocol.GEMINI))
    }

    @Test
    fun `catalog selection reuses an existing model with the models prefix`() {
        val existing = Model(modelId = "models/fixture-live-from-catalog")
        val google = ProviderSetting.Google(models = listOf(existing))
        val result = Settings(providers = listOf(google)).withVoiceModel(
            google, RealtimeModelOption("fixture-live-from-catalog", "Catalog live model"),
            RealtimeVoiceProtocol.GEMINI,
        )

        assertEquals(listOf(existing), result.providers.single().models)
    }
}
