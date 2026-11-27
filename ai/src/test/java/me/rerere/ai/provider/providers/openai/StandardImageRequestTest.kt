package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.*
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.ImageAspectRatio
import org.junit.Assert.*
import org.junit.Test

class StandardImageRequestTest {
    private val compatible = "https://images.example/v1"
    private fun params(id: String, ratio: ImageAspectRatio = ImageAspectRatio.SQUARE, count: Int = 1) =
        ImageGenerationParams(model = Model(modelId = id), prompt = "fixture prompt", aspectRatio = ratio, numOfImages = count)

    @Test fun legacyDalle2OnlyUsesSquareSizesAndBase64() {
        val body = standardImageRequest(compatible, params("dall-e-2", count = 3))
        assertEquals("1024x1024", body["size"]?.jsonPrimitive?.content)
        assertEquals(3, body["n"]?.jsonPrimitive?.int)
        assertEquals("b64_json", body["response_format"]?.jsonPrimitive?.content)
    }
    @Test(expected = IllegalArgumentException::class) fun dalle2DoesNotPretendToSupportLandscape() {
        standardImageRequest(compatible, params("dall-e-2", ImageAspectRatio.LANDSCAPE))
    }
    @Test fun legacyDalle3UsesItsOwnDimensionsAndOneImagePerRequest() {
        assertEquals("1792x1024", standardImageRequest(compatible, params("dall-e-3", ImageAspectRatio.LANDSCAPE, 3))["size"]?.jsonPrimitive?.content)
        val portrait = standardImageRequest(compatible, params("dall-e-3", ImageAspectRatio.PORTRAIT, 3))
        assertEquals("1024x1792", portrait["size"]?.jsonPrimitive?.content)
        assertEquals(1, portrait["n"]?.jsonPrimitive?.int)
        assertEquals(3, standardImageRequestCopies("dall-e-3", 3))
    }
    @Test fun gptImageDoesNotReceiveUnsupportedResponseFormat() {
        val body = standardImageRequest("https://api.openai.com/v1", params("gpt-image-1.5", ImageAspectRatio.LANDSCAPE, 2))
        assertEquals("1536x1024", body["size"]?.jsonPrimitive?.content)
        assertEquals(2, body["n"]?.jsonPrimitive?.int)
        assertFalse("response_format" in body)
    }
    @Test(expected = IllegalArgumentException::class) fun explicitUnsupportedGptResponseFormatIsExplained() {
        standardImageRequest(compatible, params("gpt-image-1.5").copy(customBody = listOf(CustomBody("response_format", JsonPrimitive("b64_json")))))
    }
    @Test fun unknownCompatibleModelsKeepOnlySafeFieldsAndUserParameters() {
        val body = standardImageRequest(compatible, params("vendor-image-v7", ImageAspectRatio.LANDSCAPE, 4).copy(
            customBody = listOf(CustomBody("resolution", JsonPrimitive("vendor-native")))))
        assertEquals(setOf("model", "prompt", "resolution"), body.keys)
        assertEquals("vendor-native", body["resolution"]?.jsonPrimitive?.content)
    }
    @Test fun retirementMatchesOfficialHostOnly() {
        assertTrue(isRetiredOfficialOpenAIImageModel("https://api.openai.com/v1", "dall-e-2"))
        assertTrue(isRetiredOfficialOpenAIImageModel("https://api.openai.com/v1", "gpt-image-1"))
        assertFalse(isRetiredOfficialOpenAIImageModel(compatible, "dall-e-2"))
        assertFalse(isRetiredOfficialOpenAIImageModel("https://api.openai.com.evil.invalid/v1", "gpt-image-1"))
        assertFalse(isRetiredOfficialOpenAIImageModel("https://api.openai.com/v1", "gpt-image-1-mini"))
    }
    @Test(expected = IllegalArgumentException::class) fun retiredOfficialRequestsFailBeforeNetwork() {
        standardImageRequest("https://api.openai.com/v1", params("dall-e-3"))
    }
}
