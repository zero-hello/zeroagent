package me.rerere.ai.provider.stream

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.provider.providers.openai.ResponseAPI
import me.rerere.ai.provider.providers.google.GoogleProvider
import me.rerere.ai.provider.providers.claude.ClaudeProvider
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Real provider request/decoder/callback paths, with every HTTP response supplied in-process. */
class ProviderBoundedSseTest {
    private val count = SSE_BUFFER_CAPACITY * 3
    private val params = TextGenerationParams(model = Model(modelId = "fixture-model"))
    private val messages = listOf(UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("fixture"))))
    private fun event(json: String, type: String? = null) = (type?.let { "event: $it\n" } ?: "") + "data: $json\n\n"

    private fun verify(body: String, stream: suspend (OkHttpClient) -> Flow<StreamChunk>) = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        try {
            val chunks = withTimeout(8000) { stream(client).onEach { delay(1) }.toList() }
            assertEquals((0 until count).map { "d$it|" }, chunks.filterIsInstance<StreamChunk.TextDelta>().map { it.text })
            assertEquals(1, chunks.filterIsInstance<StreamChunk.Finish>().size)
            assertTrue(chunks.indexOfLast { it is StreamChunk.TextDelta } < chunks.indexOfFirst { it is StreamChunk.Finish })
        } finally { client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun `chat completions preserves ordered deltas under slow consumption`() {
        val body = (0 until count).joinToString("") { event("""{"id":"fixture","model":"fixture-model","choices":[{"index":0,"delta":{"content":"d$it|"}}]}""") } +
            event("""{"id":"fixture","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""") + event("[DONE]")
        verify(body) { client -> ChatCompletionsAPI(client, KeyRoulette.default()).streamText(
            ProviderSetting.OpenAI(baseUrl = "https://sse.invalid", apiKey = "fixture-not-a-key"), messages, params) }
    }

    @Test fun `responses preserves ordered deltas under slow consumption`() {
        val body = (0 until count).joinToString("") { event("""{"type":"response.output_text.delta","item_id":"fixture-item","content_index":0,"delta":"d$it|"}""") } +
            event("""{"type":"response.completed","response":{"id":"fixture","model":"fixture-model","status":"completed"}}""")
        verify(body) { client -> ResponseAPI(client).streamText(
            ProviderSetting.OpenAI(baseUrl = "https://sse.invalid", apiKey = "fixture-not-a-key"), messages, params) }
    }

    @Test fun `Gemini preserves ordered deltas and onClosed tail under slow consumption`() {
        val body = (0 until count).joinToString("") { event("""{"candidates":[{"content":{"role":"model","parts":[{"text":"d$it|"}]}}]}""") } +
            event("""{"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[]}}]}""")
        verify(body) { client -> GoogleProvider(client).streamText(
            ProviderSetting.Google(baseUrl = "https://sse.invalid", apiKey = "fixture-not-a-key"), messages, params) }
    }

    @Test fun `Claude preserves ordered deltas under slow consumption`() {
        val body = event("""{"type":"message_start","message":{"id":"fixture","model":"fixture-model"}}""", "message_start") +
            event("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""", "content_block_start") +
            (0 until count).joinToString("") { event("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"d$it|"}}""", "content_block_delta") } +
            event("""{"type":"content_block_stop","index":0}""", "content_block_stop") +
            event("""{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""", "message_delta") +
            event("""{"type":"message_stop"}""", "message_stop")
        verify(body) { client -> ClaudeProvider(client).streamText(
            ProviderSetting.Claude(baseUrl = "https://sse.invalid", apiKey = "fixture-not-a-key"), messages, params) }
    }
}
