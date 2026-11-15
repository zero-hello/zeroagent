package me.rerere.zeroagent.data.provider

import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.datastore.DEFAULT_PROVIDERS
import org.junit.Assert.*
import org.junit.Test

class ProviderSettingEditorTest {
    @Test fun `format conversion preserves configured URLs even for official hosts`() {
        listOf("https://api.openai.com/v1", "https://proxy.example/custom/messages?tenant=zero", "unfinished url").forEach { url ->
            val original = ProviderSetting.OpenAI(name = "Proxy", apiKey = "test-key", baseUrl = url, enabled = false)
            val converted = original.withApiFormat(ProviderApiFormat.ANTHROPIC_MESSAGES) as ProviderSetting.Claude
            assertEquals(url, converted.baseUrl)
            assertEquals(original.apiKey, converted.apiKey)
            assertEquals(original.id, converted.id)
            assertEquals(original.name, converted.name)
            assertEquals(original.models, converted.models)
            assertEquals(original.customHeaders, converted.customHeaders)
            assertFalse(converted.enabled)
        }
    }

    @Test fun `OpenAI format selection changes only response mode`() {
        val original = ProviderSetting.OpenAI(apiKey = "test-key", baseUrl = "https://proxy.example/v1", chatCompletionsPath = "/custom/chat", responsesPath = "/custom/responses")
        val responses = original.withApiFormat(ProviderApiFormat.OPENAI_RESPONSES)
        assertEquals(original.copy(useResponseApi = true), responses)
        assertEquals(original, responses.withApiFormat(ProviderApiFormat.CHAT_COMPLETIONS))
    }

    @Test fun `legacy Google credentials and endpoint survive explicit conversion`() {
        val legacy = ProviderSetting.Google(apiKey = "legacy-key", baseUrl = "https://proxy.example/v1beta")
        assertSame(legacy, legacy.convertTo(ProviderSetting.Google::class))
        val converted = legacy.withApiFormat(ProviderApiFormat.OPENAI_RESPONSES) as ProviderSetting.OpenAI
        assertEquals(legacy.baseUrl, converted.baseUrl)
        assertEquals(legacy.apiKey, converted.apiKey)
        assertEquals(legacy.id, converted.id)
        assertEquals(legacy.models, converted.models)
        assertTrue(converted.useResponseApi)
    }

    @Test fun `reset changes only the endpoint and preserves identity credentials and metadata`() {
        val original = ProviderSetting.OpenAI(name = "Proxy", apiKey = "test-key", baseUrl = "https://proxy.example/custom", enabled = false)
        assertEquals(original.copy(baseUrl = ProviderSetting.OpenAI().baseUrl), original.resetBaseUrlToDefault())
        assertFalse(original.isUsingDefaultBaseUrl())
        assertTrue(original.resetBaseUrlToDefault().isUsingDefaultBaseUrl())
    }

    @Test fun `built in reset uses the provider catalog endpoint`() {
        val preset = DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.OpenAI>().first()
        val edited = preset.copy(baseUrl = "https://proxy.example/custom", apiKey = "test-key")
        assertEquals(edited.copy(baseUrl = preset.baseUrl), edited.resetBaseUrlToDefault())
    }

    @Test fun `providers without editable URLs retain the same instance`() {
        listOf(ProviderSetting.AICore(), ProviderSetting.LiteRtLocal(), ProviderSetting.LlamaCppLocal(), ProviderSetting.Codex(), ProviderSetting.Grok(), ProviderSetting.GeminiOAuth()).forEach {
            assertSame(it, it.resetBaseUrlToDefault())
            assertTrue(it.isUsingDefaultBaseUrl())
        }
    }

    @Test fun `same type conversion is identity and unsupported targets still fail`() {
        val provider = ProviderSetting.OpenAI()
        assertSame(provider, provider.convertTo(ProviderSetting.OpenAI::class))
        assertThrows(IllegalStateException::class.java) { provider.convertTo(ProviderSetting.LiteRtLocal::class) }
    }

    @Test fun `generic and Google endpoints retain their distinct validation policies`() {
        assertTrue("https://proxy.example/v1".isValidProviderBaseUrl())
        assertFalse("not a url".isValidProviderBaseUrl())
        assertFalse(googleBaseUrlHasError(""))
        assertFalse(googleBaseUrlHasError("https://proxy.example/v1beta"))
        assertTrue(googleBaseUrlHasError("https://proxy.example/v1"))
        assertTrue(googleBaseUrlHasError("not a url/v1beta"))
        assertTrue(googleBaseUrlHasError("https://proxy.example/v1beta/"))
    }
}
