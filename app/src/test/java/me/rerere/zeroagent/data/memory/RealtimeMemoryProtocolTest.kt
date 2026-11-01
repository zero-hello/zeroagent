package me.rerere.zeroagent.data.memory

import kotlinx.serialization.json.*
import me.rerere.ai.provider.Model
import me.rerere.zeroagent.data.ai.realtime.*
import org.junit.Assert.*
import org.junit.Test

class RealtimeMemoryProtocolTest {
    @Test fun openaiFunctionTurnDoesNotPrematurelyCompleteAnswer() {
        val json = Json.parseToJsonElement("""{"type":"response.done","response":{"status":"completed","output":[{"type":"function_call","name":"zeroagent_recall","call_id":"c1","arguments":"{\"query\":\"tea\"}"}]}}""") as JsonObject
        val events = OpenAIRealtimeProtocol.events(json)
        assertEquals(1, events.size)
        assertEquals("c1", (events.first() as RealtimeEvent.MemoryCall).id)
        assertFalse(events.contains(RealtimeEvent.ResponseDone))
    }
    @Test fun geminiUsesToolResponseAndSupportsCancellation() {
        val json = Json.parseToJsonElement("""{"toolCall":{"functionCalls":[{"id":"c2","name":"zeroagent_recall","args":{"query":"tea"}}]}}""") as JsonObject
        assertEquals("c2", (GeminiLiveProtocol.events(json).first() as RealtimeEvent.MemoryCall).id)
        val output = GeminiLiveProtocol.memoryResult("c2", MEMORY_TOOL, "untrusted history", true).single()
        assertTrue(output.toString().contains("functionResponses"))
        val cancel = Json.parseToJsonElement("""{"toolCallCancellation":{"ids":["c2"]}}""") as JsonObject
        assertEquals(listOf("c2"), (GeminiLiveProtocol.events(cancel).first() as RealtimeEvent.MemoryCancelled).ids)
    }
    @Test fun memoryToolDoesNotExposePhoneActions() {
        for (p in listOf(OpenAIRealtimeProtocol, GeminiLiveProtocol)) {
            val setup = p.withMemoryTool(p.setup(Model(modelId = "test"), "user prompt")).toString()
            assertTrue(setup.contains(MEMORY_TOOL))
            assertFalse(setup.contains("tap_screen"))
            assertTrue(setup.contains("user prompt"))
        }
    }
    @Test fun openaiTextModeToolContinuationRemainsTextOnly() {
        val messages = OpenAIRealtimeProtocol.memoryResult("c", MEMORY_TOOL, "no recall", false)
        assertTrue(messages.first().toString().contains("function_call_output"))
        assertTrue(messages.last().toString().contains("\"text\""))
        assertFalse(messages.last().toString().contains("\"audio\""))
        assertTrue(messages.last().toString().contains("\"tool_choice\":\"auto\""))
    }
}
