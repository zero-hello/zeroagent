package me.rerere.zeroagent.data.provider

import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.datastore.DEFAULT_PROVIDERS
import org.junit.Assert.*
import org.junit.Test

class ProviderSettingEditorTest {
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
