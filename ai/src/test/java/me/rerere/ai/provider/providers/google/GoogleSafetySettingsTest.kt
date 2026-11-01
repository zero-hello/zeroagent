package me.rerere.ai.provider.providers.google

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import me.rerere.ai.ui.UIMessage
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class GoogleSafetySettingsTest {
    private val harassment = "HARM_CATEGORY_HARASSMENT"
    private val hate = "HARM_CATEGORY_HATE_SPEECH"
    private val civic = "HARM_CATEGORY_CIVIC_INTEGRITY"
    private val supportedCategories = listOf(
        harassment, hate, "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT",
    )
    private val model = Model(modelId = "gemini-fixture")
    private val imageModel = Model(modelId = "gemini-fixture-image")
    private val messages = listOf(UIMessage.user("A mountain"))

    @Test
    fun `every provider threshold reaches normal streaming and Gemini image requests`() = runBlocking {
        Transport().use { transport ->
            for (threshold in GeminiSafetyThreshold.entries) {
                val safety = GeminiSafetySettings(supportedCategories.associateWith { threshold })
                val setting = setting(safety)
                transport.provider.generateText(setting, messages, TextGenerationParams(model))
                transport.stream(setting, TextGenerationParams(model))
                transport.provider.generateImage(setting, ImageGenerationParams(imageModel, "A mountain")).toList()
                transport.requests.takeLast(3).forEach { (_, body) ->
                    assertEquals(safety.thresholds.mapValues { it.value.name }, thresholds(body))
                }
                assertTrue(transport.requests[transport.requests.lastIndex - 1].first.queryParameter("alt") == "sse")
            }
        }
    }

    @Test
    fun `request safety overrides provider per category without mutating saved model or settings`() = runBlocking {
        Transport().use { transport ->
            val safety = GeminiSafetySettings(mapOf(
                harassment to GeminiSafetyThreshold.BLOCK_ONLY_HIGH,
                hate to GeminiSafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
            ))
            val explicit = GeminiSafetySettings(mapOf(harassment to GeminiSafetyThreshold.BLOCK_NONE))
            val configuredModel = model.copy(generation = ModelGenerationSettings(temperature = .4f, topK = 12))
            val setting = setting(safety).copy(models = listOf(configuredModel))
            val params = TextGenerationParams(configuredModel, geminiSafety = explicit)
            transport.provider.generateText(setting, messages, params)
            transport.stream(setting, params)
            transport.requests.forEach { (_, body) ->
                assertEquals("BLOCK_NONE", thresholds(body)[harassment])
                assertEquals("BLOCK_MEDIUM_AND_ABOVE", thresholds(body)[hate])
                assertEquals(12, body["generationConfig"]!!.jsonObject["topK"]!!.jsonPrimitive.int)
                assertEquals(.4f, body["generationConfig"]!!.jsonObject["temperature"]!!.jsonPrimitive.float, 0f)
            }
            assertEquals(explicit, params.geminiSafety)
            assertEquals(safety, setting.safetySettings)
            assertEquals(listOf(configuredModel), setting.models)
        }
    }

    @Test
    fun `partial and empty provider settings have identical text stream and image defaults`() = runBlocking {
        Transport().use { transport ->
            for (safety in listOf(GeminiSafetySettings(), GeminiSafetySettings(mapOf(harassment to GeminiSafetyThreshold.BLOCK_LOW_AND_ABOVE)))) {
                val setting = setting(safety)
                transport.provider.generateText(setting, messages, TextGenerationParams(model))
                transport.stream(setting, TextGenerationParams(model))
                transport.provider.generateImage(setting, ImageGenerationParams(imageModel, "A mountain")).toList()
                val bodies = transport.requests.takeLast(3).map { it.second }
                assertEquals(bodies[0]["safetySettings"], bodies[1]["safetySettings"])
                assertEquals(bodies[0]["safetySettings"], bodies[2]["safetySettings"])
                assertEquals(supportedCategories.toSet(), thresholds(bodies[2]).keys)
                supportedCategories.forEach { category ->
                    assertEquals(safety.thresholds[category]?.name ?: "OFF", thresholds(bodies[2])[category])
                }
            }
        }
    }

    @Test
    fun `model custom safety body keeps final precedence including an empty array`() = runBlocking {
        Transport().use { transport ->
            val safety = GeminiSafetySettings(mapOf(harassment to GeminiSafetyThreshold.BLOCK_LOW_AND_ABOVE))
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
                transport.provider.generateText(setting(safety), messages, params)
                transport.stream(setting(safety), params)
                transport.provider.generateImage(setting(safety), ImageGenerationParams(
                    configuredModel.copy(modelId = imageModel.modelId), "A mountain", customBody = configuredModel.customBodies,
                )).toList()
                transport.requests.takeLast(3).forEach { (_, body) -> assertEquals(array, body["safetySettings"]) }
                assertEquals(array, configuredModel.customBodies.single().value)
            }
        }
    }

    @Test
    fun `deprecated and unsupported saved categories are not auto emitted or deleted`() = runBlocking {
        Transport().use { transport ->
            val safety = GeminiSafetySettings(mapOf(
                civic to GeminiSafetyThreshold.BLOCK_NONE,
                "HARM_CATEGORY_UNSPECIFIED" to GeminiSafetyThreshold.OFF,
                "HARM_CATEGORY_IMAGE_HATE" to GeminiSafetyThreshold.BLOCK_ONLY_HIGH,
                harassment to GeminiSafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
            ))
            val setting = setting(safety)
            transport.provider.generateText(setting, messages, TextGenerationParams(model))
            transport.stream(setting, TextGenerationParams(model))
            transport.provider.generateImage(setting, ImageGenerationParams(imageModel, "A mountain")).toList()
            transport.requests.forEach { (_, body) ->
                assertEquals(supportedCategories.toSet(), thresholds(body).keys)
                assertEquals("BLOCK_MEDIUM_AND_ABOVE", thresholds(body)[harassment])
                assertFalse(thresholds(body).containsKey(civic))
            }
            assertEquals(4, setting.safetySettings.thresholds.size)
            assertEquals(GeminiSafetyThreshold.BLOCK_NONE, setting.safetySettings.thresholds[civic])
        }
    }

    @Test
    fun `image output through text generation keeps safety and response modalities`() {
        val safety = GeminiSafetySettings(mapOf(harassment to GeminiSafetyThreshold.BLOCK_NONE))
        val multimodal = model.copy(outputModalities = listOf(Modality.TEXT, Modality.IMAGE))
        Transport().use { transport ->
            val body = transport.provider.buildCompletionRequestBody(messages, TextGenerationParams(multimodal, geminiSafety = safety))
            assertEquals("BLOCK_NONE", thresholds(body)[harassment])
            assertEquals(listOf("TEXT", "IMAGE"), body["generationConfig"]!!.jsonObject["responseModalities"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test
    fun `Code Assist builder honors its category subset without deprecated or duplicate filters`() {
        Transport().use { transport ->
            assertEquals(supportedCategories, GOOGLE_SAFETY_CATEGORIES)
            assertEquals(supportedCategories, CODE_ASSIST_SAFETY_CATEGORIES)
            val body = transport.provider.buildCompletionRequestBody(
                messages, TextGenerationParams(model), listOf(harassment, harassment, civic, "unknown"),
            )
            assertEquals(mapOf(harassment to "OFF"), thresholds(body))
            assertEquals(1, body["safetySettings"]!!.jsonArray.size)
        }
    }

    @Test
    fun `Vertex API key transport uses the same safety body for text stream and Gemini images`() = runBlocking {
        Transport().use { transport ->
            val setting = setting(GeminiSafetySettings(mapOf(hate to GeminiSafetyThreshold.BLOCK_ONLY_HIGH))).copy(vertexAI = true)
            transport.provider.generateText(setting, messages, TextGenerationParams(model))
            transport.stream(setting, TextGenerationParams(model))
            transport.provider.generateImage(setting, ImageGenerationParams(imageModel, "A mountain")).toList()
            transport.requests.forEach { (url, body) ->
                assertEquals("aiplatform.googleapis.com", url.host)
                assertEquals("BLOCK_ONLY_HIGH", thresholds(body)[hate])
            }
        }
    }

    @Test
    fun `Imagen predict keeps its separate parameters without Gemini safety settings`() = runBlocking {
        Transport().use { transport ->
            transport.provider.generateImage(setting(GeminiSafetySettings()), ImageGenerationParams(
                Model(modelId = "imagen-fixture"), "A mountain",
            )).toList()
            val (url, body) = transport.requests.single()
            assertTrue(url.encodedPath.endsWith(":predict"))
            assertFalse(body.containsKey("safetySettings"))
            assertEquals(1, body["parameters"]!!.jsonObject["sampleCount"]!!.jsonPrimitive.int)
        }
    }

    private fun setting(safety: GeminiSafetySettings) = ProviderSetting.Google(
        baseUrl = "https://gemini.invalid/v1beta", safetySettings = safety,
    )

    private fun thresholds(body: JsonObject): Map<String, String> = body["safetySettings"]!!.jsonArray.associate {
        val entry = it.jsonObject
        entry["category"]!!.jsonPrimitive.content to entry["threshold"]!!.jsonPrimitive.content
    }

    /** Intercepts before DNS or sockets; no credentials, network, or billable APIs. */
    private inner class Transport : Closeable {
        val requests = CopyOnWriteArrayList<Pair<HttpUrl, JsonObject>>()
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            requests += request.url to Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            val stream = request.url.queryParameter("alt") == "sse"
            val payload = when {
                request.url.encodedPath.endsWith(":predict") -> """{"predictions":[{"bytesBase64Encoded":"Zml4dHVyZQ==","mimeType":"image/png"}]}"""
                request.url.encodedPath.contains("fixture-image") -> """{"candidates":[{"content":{"role":"model","parts":[{"inlineData":{"mimeType":"image/png","data":"Zml4dHVyZQ=="}}]},"finishReason":"STOP"}]}"""
                else -> """{"candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]},"finishReason":"STOP"}]}"""
            }
            val contentType = if (stream) "text/event-stream" else "application/json"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", contentType)
                .body((if (stream) "data: $payload\n\n" else payload).toResponseBody(contentType.toMediaType()))
                .build()
        }.build()
        val provider = GoogleProvider(client)

        suspend fun stream(setting: ProviderSetting.Google, params: TextGenerationParams) = withTimeout(5_000) {
            provider.streamText(setting, messages, params).toList()
        }

        override fun close() {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }
}
