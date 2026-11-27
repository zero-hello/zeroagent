package me.rerere.ai.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCapabilitiesTest {
    private val image = Model(modelId = "gpt-image-1.5", type = ModelType.IMAGE,
        inputModalities = listOf(Modality.TEXT, Modality.IMAGE), outputModalities = listOf(Modality.IMAGE))
    @Test fun cohereCannotGainImagesByChangingModelMetadata() {
        assertFalse(ProviderSetting.OpenAI(protocol = OpenAIProtocol.COHERE_V2).supportsImageGeneration(image))
        assertFalse(ProviderSetting.OpenAI(protocol = OpenAIProtocol.COHERE_V2).supportsImageGeneration(image, editing = true))
    }
    @Test fun textOrVisionInputAloneIsInsufficient() {
        assertFalse(ProviderSetting.OpenAI().supportsImageGeneration(Model(modelId = "chat", inputModalities = listOf(Modality.IMAGE))))
    }
    @Test fun disabledOrUnsupportedAdaptersAreExcluded() {
        assertFalse(ProviderSetting.OpenAI(enabled = false).supportsImageGeneration(image))
        assertFalse(ProviderSetting.Claude().supportsImageGeneration(image))
        assertFalse(ProviderSetting.GeminiOAuth(enabled = true).supportsImageGeneration(image))
    }
    @Test fun editingRequiresAnImplementedEndpoint() {
        assertTrue(ProviderSetting.OpenAI().supportsImageGeneration(image, editing = true))
        assertFalse(ProviderSetting.Google().supportsImageGeneration(image, editing = true))
    }
    @Test fun providerOverrideCannotBypassProtocolGate() {
        assertFalse(ProviderSetting.OpenAI().supportsImageGeneration(image.copy(
            providerOverwrite = ProviderSetting.OpenAI(protocol = OpenAIProtocol.COHERE_V2))))
    }
    @Test fun retiredOfficialModelsAreNotOfferedButCompatibleEndpointsKeepTheirOwnSupport() {
        assertFalse(ProviderSetting.OpenAI().supportsImageGeneration(image.copy(modelId = "gpt-image-1")))
        assertFalse(ProviderSetting.OpenAI().supportsImageGeneration(image.copy(modelId = "dall-e-3")))
        assertTrue(ProviderSetting.OpenAI(baseUrl = "https://images.example/v1").supportsImageGeneration(image.copy(modelId = "dall-e-3")))
    }
}
