package me.rerere.zeroagent.ui.pages.chat

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModelSetupTest {
    private val chatModel = Model(modelId = "chat-test", type = ModelType.CHAT)

    @Test fun `prepopulated cloud models still need a credential`() {
        val provider = ProviderSetting.OpenAI(enabled = true, models = listOf(chatModel), builtIn = true)
        assertFalse(provider.isReadyForOnboarding())
        assertTrue(provider.copy(apiKey = "example-test-key").isReadyForOnboarding())
        assertFalse(provider.copy(apiKey = "example-test-key", enabled = false).isReadyForOnboarding())
    }

    @Test fun `explicit loopback and installed local chat models are accepted`() {
        val local = ProviderSetting.OpenAI(enabled = true, baseUrl = "http://127.0.0.1:8080/v1", models = listOf(chatModel))
        assertTrue(local.isReadyForOnboarding())
        assertTrue(ProviderSetting.LlamaCppLocal(enabled = true, models = listOf(chatModel)).isReadyForOnboarding())
        assertFalse(ProviderSetting.LlamaCppLocal(enabled = true).isReadyForOnboarding())
    }
}