package me.rerere.ai.provider.providers.cohere

import kotlinx.serialization.json.*
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.*
import me.rerere.ai.ui.*
import me.rerere.ai.util.json
import org.junit.Assert.*
import org.junit.Test

class CohereProtocolTest {
    private fun obj(value: String) = json.parseToJsonElement(value).jsonObject
    @Test fun `native body uses p and messages with tool results`() {
        val model = Model(modelId = "fixture", abilities = listOf(ModelAbility.TOOL))
        val messages = listOf(UIMessage.user("Hello"), UIMessage.assistant("").copy(parts = listOf(
            UIMessagePart.Tool("call-1", "clock", "{}", output = listOf(UIMessagePart.Text("12:00"))),
        )))
        val params = TextGenerationParams(model, topP = .9f, tools = listOf(Tool("clock", "Time", execute = { emptyList() })))
        val body = CohereProtocol.request(messages, params, true)
        assertEquals(.9f, body["p"]!!.jsonPrimitive.float, 0f)
        assertFalse(body.containsKey("top_p"))
        val list = body["messages"]!!.jsonArray
        assertEquals("tool", list.last().jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("call-1", list.last().jsonObject["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("clock", body["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }
    @Test fun `native response parses text tools and token counts`() {
        val result = CohereProtocol.response(obj("""{"id":"r1","finish_reason":"TOOL_CALL","message":{"content":[{"type":"text","text":"Checking"}],"tool_calls":[{"id":"c1","function":{"name":"clock","arguments":"{}"}}]},"usage":{"tokens":{"input_tokens":10,"output_tokens":3}}}"""), Model(modelId = "fixture"))
        assertEquals("Checking", result.message.parts.filterIsInstance<UIMessagePart.Text>().single().text)
        assertEquals("clock", result.message.getTools().single().toolName)
        assertEquals(13, result.usage!!.totalTokens)
    }
    @Test fun `fragmented native stream preserves text and tool lifecycle`() {
        val decoder = CohereStreamDecoder("fixture")
        val chunks = listOf(
            """{"type":"content-delta","index":0,"delta":{"message":{"content":{"text":"Hi"}}}}""",
            """{"type":"tool-call-start","index":0,"delta":{"message":{"tool_calls":{"id":"c1","function":{"name":"clock","arguments":""}}}}}""",
            """{"type":"tool-call-delta","index":0,"delta":{"message":{"tool_calls":{"function":{"arguments":"{}"}}}}}""",
            """{"type":"tool-call-end","index":0}""",
            """{"type":"message-end","delta":{"finish_reason":"TOOL_CALL","usage":{"tokens":{"input_tokens":4,"output_tokens":2}}}}""",
        ).flatMap { decoder.decode(obj(it)) }
        assertEquals("Hi", chunks.filterIsInstance<StreamChunk.TextDelta>().single().text)
        assertEquals("{}", chunks.filterIsInstance<StreamChunk.ToolCallDelta>().single().inputDelta)
        assertEquals("c1", chunks.filterIsInstance<StreamChunk.ToolCallEnd>().single().id)
        assertEquals(6, chunks.filterIsInstance<StreamChunk.Usage>().single().usage.totalTokens)
        assertTrue(decoder.finished)
    }
    @Test(expected = IllegalArgumentException::class) fun `unsupported attachments do not silently disappear`() {
        CohereProtocol.request(listOf(UIMessage.user("").copy(parts = listOf(UIMessagePart.Image("https://example.com/a.png")))), TextGenerationParams(Model(modelId = "fixture")), false)
    }
    @Test(expected = IllegalArgumentException::class) fun `provider error finish is not a successful empty response`() {
        CohereStreamDecoder("fixture").decode(obj("""{"type":"message-end","delta":{"finish_reason":"ERROR"}}"""))
    }
    @Test(expected = IllegalArgumentException::class) fun `empty normal response explains the missing output`() {
        CohereProtocol.response(obj("""{"message":{"content":[]},"finish_reason":"COMPLETE"}"""), Model(modelId = "fixture"))
    }
    @Test(expected = IllegalArgumentException::class) fun `empty successful stream is rejected`() {
        CohereStreamDecoder("fixture").decode(obj("""{"type":"message-end","delta":{"finish_reason":"COMPLETE"}}"""))
    }
    @Test fun `tool only stream is a valid response`() {
        val decoder = CohereStreamDecoder("fixture")
        decoder.decode(obj("""{"type":"tool-call-start","index":0,"delta":{"message":{"tool_calls":{"id":"c1","function":{"name":"clock","arguments":"{}"}}}}}"""))
        val end = decoder.decode(obj("""{"type":"message-end","delta":{"finish_reason":"TOOL_CALL"}}"""))
        assertTrue(decoder.finished)
        assertEquals("c1", end.filterIsInstance<StreamChunk.ToolCallEnd>().single().id)
    }
}
