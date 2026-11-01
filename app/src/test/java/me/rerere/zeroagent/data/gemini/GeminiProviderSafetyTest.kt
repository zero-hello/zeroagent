package me.rerere.zeroagent.data.gemini

import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import me.rerere.ai.provider.providers.google.GoogleProvider
import me.rerere.ai.ui.UIMessage
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

/** Exercises the production OAuth body builder without credentials, accounts, or network. */
class GeminiProviderSafetyTest {
    private val harassment = "HARM_CATEGORY_HARASSMENT"
    private val hate = "HARM_CATEGORY_HATE_SPEECH"
    private val categories = listOf(
        harassment, hate, "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT",
    )
    private val model = Model(modelId = "gemini-fixture")
    private val wire = GoogleProvider(OkHttpClient.Builder().addInterceptor {
        error("Safety request tests must not access the network")
    }.build())

    @Test
    fun `UI OFF default becomes four explicit OFF filters in the OAuth request`() {
        for (overrides in listOf(null, GeminiSafetySettings())) {
            val body = body(ProviderSetting.GeminiOAuth(), TextGenerationParams(model, geminiSafety = overrides))
            assertEquals("test-project", body["project"]!!.jsonPrimitive.content)
            assertEquals(model.modelId, body["model"]!!.jsonPrimitive.content)
            assertEquals(categories.associateWith { "OFF" }, thresholds(body))
            assertEquals(4, body["request"]!!.jsonObject["safetySettings"]!!.jsonArray.size)
        }
    }

    @Test
    fun `all provider thresholds survive OAuth wrapping and missing categories stay OFF`() {
        for (threshold in GeminiSafetyThreshold.entries) {
            val provider = ProviderSetting.GeminiOAuth(
                safetySettings = GeminiSafetySettings(mapOf(harassment to threshold)),
            )
            val expected = categories.associateWith { if (it == harassment) threshold.name else "OFF" }
            assertEquals(expected, thresholds(body(provider, TextGenerationParams(model))))
        }
    }

    @Test
    fun `request overrides provider per category while retaining model configuration`() {
        val safety = GeminiSafetySettings(mapOf(
            harassment to GeminiSafetyThreshold.BLOCK_LOW_AND_ABOVE,
            hate to GeminiSafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
        ))
        val overrides = GeminiSafetySettings(mapOf(harassment to GeminiSafetyThreshold.BLOCK_NONE))
        val configuredModel = model.copy(generation = ModelGenerationSettings(topK = 12, temperature = .4f))
        val provider = ProviderSetting.GeminiOAuth(safetySettings = safety, models = listOf(configuredModel))
        val params = TextGenerationParams(configuredModel, geminiSafety = overrides)
        val request = body(provider, params)

        assertEquals("BLOCK_NONE", thresholds(request)[harassment])
        assertEquals("BLOCK_MEDIUM_AND_ABOVE", thresholds(request)[hate])
        assertEquals(categories.toSet(), thresholds(request).keys)
        val generation = request["request"]!!.jsonObject["generationConfig"]!!.jsonObject
        assertEquals(12, generation["topK"]!!.jsonPrimitive.int)
        assertEquals(.4f, generation["temperature"]!!.jsonPrimitive.float, 0f)
        assertEquals(overrides, params.geminiSafety)
        assertEquals(safety, provider.safetySettings)
        assertEquals(listOf(configuredModel), provider.models)
    }

    @Test
    fun `model custom safety array keeps final precedence including an empty array`() {
        val provider = ProviderSetting.GeminiOAuth(safetySettings = GeminiSafetySettings(
            mapOf(harassment to GeminiSafetyThreshold.BLOCK_LOW_AND_ABOVE),
        ))
        val custom = buildJsonArray {
            add(buildJsonObject { put("category", harassment); put("threshold", "BLOCK_NONE") })
        }
        for (array in listOf(custom, JsonArray(emptyList()))) {
            val configuredModel = model.copy(customBodies = listOf(CustomBody("safetySettings", array)))
            val params = TextGenerationParams(
                configuredModel,
                geminiSafety = GeminiSafetySettings(mapOf(harassment to GeminiSafetyThreshold.BLOCK_ONLY_HIGH)),
                customBody = configuredModel.customBodies,
            )
            assertEquals(array, body(provider, params)["request"]!!.jsonObject["safetySettings"])
            assertEquals(array, configuredModel.customBodies.single().value)
        }
    }

    @Test
    fun `deprecated and unsupported stored filters are omitted without deleting saved values`() {
        val safety = GeminiSafetySettings(mapOf(
            "HARM_CATEGORY_CIVIC_INTEGRITY" to GeminiSafetyThreshold.BLOCK_NONE,
            "HARM_CATEGORY_UNSPECIFIED" to GeminiSafetyThreshold.BLOCK_ONLY_HIGH,
            harassment to GeminiSafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
        ))
        val provider = ProviderSetting.GeminiOAuth(safetySettings = safety)
        val thresholds = thresholds(body(provider, TextGenerationParams(model)))
        assertEquals(categories.toSet(), thresholds.keys)
        assertEquals("BLOCK_MEDIUM_AND_ABOVE", thresholds[harassment])
        assertEquals(3, provider.safetySettings.thresholds.size)
        assertEquals(safety, provider.safetySettings)
    }

    private fun body(provider: ProviderSetting.GeminiOAuth, params: TextGenerationParams): JsonObject =
        buildGeminiOAuthRequestBody(wire, "test-project", provider, listOf(UIMessage.user("Hello")), params)

    private fun thresholds(body: JsonObject): Map<String, String> =
        body["request"]!!.jsonObject["safetySettings"]!!.jsonArray.associate {
            val entry = it.jsonObject
            entry["category"]!!.jsonPrimitive.content to entry["threshold"]!!.jsonPrimitive.content
        }
}
