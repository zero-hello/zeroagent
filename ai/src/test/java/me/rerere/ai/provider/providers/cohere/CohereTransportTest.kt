package me.rerere.ai.provider.providers.cohere

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.util.HttpException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/** Real provider transport with interception before DNS: no account or billable calls. */
class CohereTransportTest {
    private val model = Model(modelId = "fixture")
    private val setting = ProviderSetting.OpenAI(
        baseUrl = "https://cohere.invalid/v2/chat", apiKey = "fixture-key",
        protocol = OpenAIProtocol.COHERE_V2,
    )
    private val messages = listOf(UIMessage.user("Hello"))

    @Test fun `native request resolves complete endpoint and uses Bearer authentication`() = runBlocking {
        Transport { 200 to """{"id":"r1","finish_reason":"COMPLETE","message":{"content":[{"type":"text","text":"Hi"}]}}""" }.use { transport ->
            val result = transport.provider.generateText(setting, messages, TextGenerationParams(model, topP = .8f))
            assertEquals("Hi", result.message.toText())
            val request = transport.requests.single()
            assertEquals("/v2/chat", request.url.encodedPath)
            assertEquals("Bearer fixture-key", request.header("Authorization"))
            assertEquals("application/json", request.header("Accept"))
            val body = transport.bodies.single()!!
            assertFalse(body["stream"]!!.jsonPrimitive.boolean)
            assertEquals(.8f, body["p"]!!.jsonPrimitive.float, 0f)
            assertFalse(body.containsKey("top_p"))
        }
    }

    @Test fun `model discovery follows pagination and removes deprecated and duplicate models`() = runBlocking {
        Transport { request ->
            200 to if (request.url.queryParameter("page_token") == null)
                """{"models":[{"name":"current","features":["tool_use"]},{"name":"old","is_deprecated":true}],"next_page_token":"second"}"""
            else """{"models":[{"name":"current"},{"name":"other"}]}"""
        }.use { transport ->
            val models = transport.provider.listModels(setting)
            assertEquals(listOf("current", "other"), models.map { it.modelId })
            assertTrue(ModelAbility.TOOL in models.first().abilities)
            assertEquals(2, transport.requests.size)
            assertTrue(transport.requests.all { it.url.encodedPath == "/v1/models" && it.url.queryParameter("endpoint") == "chat" })
            assertEquals("second", transport.requests.last().url.queryParameter("page_token"))
        }
    }

    @Test fun `repeated pagination fails instead of looping forever`() = runBlocking {
        Transport { 200 to """{"models":[],"next_page_token":"same"}""" }.use { transport ->
            val error = runCatching { transport.provider.listModels(setting) }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals(2, transport.requests.size)
        }
    }

    @Test fun `native SSE completes with text and usage`() = runBlocking {
        val payload = listOf(
            """{"type":"content-delta","index":0,"delta":{"message":{"content":{"text":"Hi"}}}}""",
            """{"type":"message-end","delta":{"finish_reason":"COMPLETE","usage":{"tokens":{"input_tokens":2,"output_tokens":1}}}}""",
        ).joinToString("") { "data: $it\n\n" }
        Transport(stream = true) { 200 to payload }.use { transport ->
            val chunks = withTimeout(5_000) { transport.provider.streamText(setting, messages, TextGenerationParams(model)).toList() }
            assertEquals("Hi", chunks.filterIsInstance<StreamChunk.TextDelta>().single().text)
            assertEquals(3, chunks.filterIsInstance<StreamChunk.Usage>().single().usage.totalTokens)
            assertEquals(1, chunks.filterIsInstance<StreamChunk.Finish>().size)
            assertTrue(transport.bodies.single()!!["stream"]!!.jsonPrimitive.boolean)
            assertEquals("text/event-stream", transport.requests.single().header("Accept"))
        }
    }

    @Test fun `truncated SSE is an error rather than a successful empty reply`() = runBlocking {
        Transport(stream = true) { 200 to "data: {\"type\":\"message-start\"}\n\n" }.use { transport ->
            val error = runCatching { withTimeout(5_000) { transport.provider.streamText(setting, messages, TextGenerationParams(model)).toList() } }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
            assertTrue(error!!.message.orEmpty().contains("中斷"))
        }
    }

    @Test fun `HTTP errors retain status without leaking response secrets in text and stream`() = runBlocking {
        for (status in listOf(401, 402, 403, 404, 429, 500)) {
            Transport { status to "fixture-key private provider error" }.use { transport ->
                val error = runCatching { transport.provider.generateText(setting, messages, TextGenerationParams(model)) }.exceptionOrNull()
                assertTrue(error is HttpException)
                assertEquals(status, (error as HttpException).statusCode)
                assertFalse(error.message.orEmpty().contains("fixture-key"))
            }
            Transport(stream = true) { status to "fixture-key private provider error" }.use { transport ->
                val error = runCatching { withTimeout(5_000) { transport.provider.streamText(setting, messages, TextGenerationParams(model)).toList() } }.exceptionOrNull()
                assertTrue(error is HttpException)
                assertEquals(status, (error as HttpException).statusCode)
                assertFalse(error.message.orEmpty().contains("fixture-key"))
            }
        }
    }

    private class Transport(
        private val stream: Boolean = false,
        reply: (Request) -> Pair<Int, String>,
    ) : Closeable {
        val requests = CopyOnWriteArrayList<Request>()
        val bodies = CopyOnWriteArrayList<JsonObject?>()
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            bodies += request.body?.let { body ->
                val buffer = Buffer(); body.writeTo(buffer)
                Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            }
            val (status, payload) = reply(request)
            val contentType = if (stream) "text/event-stream" else "application/json"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .header("Content-Type", contentType).body(payload.toResponseBody(contentType.toMediaType())).build()
        }.build()
        val provider = CohereProvider(client)
        override fun close() {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }
}
