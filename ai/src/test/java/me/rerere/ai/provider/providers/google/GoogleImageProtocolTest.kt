package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import org.junit.Assert.*
import org.junit.Test

class GoogleImageProtocolTest {
    @Test fun `native Gemini image models use generateContent and Imagen uses predict`() {
        assertTrue(usesGeminiImageEndpoint(Model(modelId = "gemini-fixture-image")))
        assertFalse(usesGeminiImageEndpoint(Model(modelId = "imagen-fixture")))
    }
    @Test fun `image request forwards model-owned sampling and safety overrides`() {
        val m = Model(modelId = "gemini-fixture-image", generation = ModelGenerationSettings(systemPrompt = "Paint carefully", topK = 12, temperature = .5f))
        val safety = GeminiSafetySettings(mapOf("HARM_CATEGORY_HARASSMENT" to GeminiSafetyThreshold.BLOCK_ONLY_HIGH))
        val body = geminiImageRequest(ImageGenerationParams(m, "A mountain"), safety)
        assertEquals(12, body["generationConfig"]!!.jsonObject["topK"]!!.jsonPrimitive.int)
        assertEquals("Paint carefully", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("BLOCK_ONLY_HIGH", body["safetySettings"]!!.jsonArray[0].jsonObject["threshold"]!!.jsonPrimitive.content)
    }
    @Test fun `Gemini image output extracts inline images and ignores text`() {
        val root = Json.parseToJsonElement("""{"candidates":[{"content":{"parts":[{"text":"description"},{"inlineData":{"mimeType":"image/png","data":"Zml4dHVyZQ=="}}]}}]}""").jsonObject
        val images = geminiGeneratedImages(root); assertEquals(1, images.size); assertEquals("image/png", images.single().mimeType)
    }
    @Test fun `catalog image declarations do not claim text output for image-only models`() {
        val image = Model(modelId = "gpt-image-fixture").withCatalogCapabilities()
        assertEquals(ModelType.IMAGE, image.type); assertEquals(listOf(Modality.IMAGE), image.outputModalities)
        val gemini = Model(modelId = "gemini-fixture-image").withCatalogCapabilities()
        assertTrue(Modality.IMAGE in gemini.outputModalities); assertTrue(Modality.TEXT in gemini.outputModalities)
    }
}
