package me.rerere.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderBalanceCapabilityTest {
    @Test
    fun unsupportedBalanceLookupReturnsNoValue() = runBlocking {
        val balance = UnsupportedBalanceProvider().getBalance(ProviderSetting.OpenAI())

        assertNull(balance)
    }

    private class UnsupportedBalanceProvider : Provider<ProviderSetting.OpenAI> {
        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): TextGenerationResult = error("Not used by this test")

        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<StreamChunk> = emptyFlow()

        override suspend fun generateImage(
            providerSetting: ProviderSetting,
            params: ImageGenerationParams,
        ): Flow<ImageGenerationItem> = emptyFlow()
    }
}
