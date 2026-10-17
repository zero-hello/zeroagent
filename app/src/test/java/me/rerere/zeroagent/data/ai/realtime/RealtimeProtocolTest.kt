package me.rerere.zeroagent.data.ai.realtime

import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import org.junit.Assert.*
import org.junit.Test

class RealtimeProtocolTest {
    private val model = Model(modelId = "voice-model", realtimeVoice = RealtimeVoiceSetting(enabled = true))
    @Test fun `ordinary chat compatibility never proves realtime capability`() {
        val connection = RealtimeConnections.create(model, ProviderSetting.OpenAI(apiKey = "test-only"))
        assertTrue(connection.url.startsWith("wss://api.openai.com/v1/realtime?"))
        assertEquals("Bearer test-only", connection.headers["Authorization"])
        assertFalse(connection.url.contains("test-only"))
        assertFalse(Model().realtimeVoice.enabled)
    }
    @Test fun `custom endpoint preserves model query but rejects plaintext credentials`() {
        val custom = model.copy(realtimeVoice = model.realtimeVoice.copy(endpoint = "wss://gateway.example/api/voice?route=one"))
        val url = RealtimeConnections.create(custom, ProviderSetting.OpenAI(apiKey = "test-only")).url
        assertTrue(url.contains("route=one")); assertTrue(url.contains("model=voice-model"))
        try {
            RealtimeConnections.create(custom.copy(realtimeVoice = custom.realtimeVoice.copy(endpoint = "ws://gateway.example/voice")), ProviderSetting.OpenAI(apiKey = "test-only"))
            fail("must reject plaintext")
        } catch (e: IllegalArgumentException) { assertEquals("secure_endpoint_required", e.message) }
    }
    @Test fun `only confirmed setup marks OpenAI ready`() {
        assertTrue(OpenAIRealtimeProtocol.events(obj("type" to str("session.created"))).isEmpty())
        assertEquals(listOf(RealtimeEvent.Ready), OpenAIRealtimeProtocol.events(obj("type" to str("session.updated"))))
        val setup = OpenAIRealtimeProtocol.setup(model, "identity").o("session")
        assertEquals("realtime", setup.s("type"))
        assertEquals("audio", setup.a("output_modalities").single().jsonPrimitive.content)
        assertEquals(24000, setup.o("audio").o("input").o("format")["rate"]!!.jsonPrimitive.int)
    }
    @Test fun `OpenAI text creates native audio response and interruption truncates heard audio`() {
        val text = OpenAIRealtimeProtocol.text("hello")
        assertEquals("conversation.item.create", text.first().s("type"))
        assertEquals("response.create", text.last().s("type"))
        val cancel = OpenAIRealtimeProtocol.interrupt("item-1", 120)
        assertEquals("response.cancel", cancel.first().s("type"))
        assertEquals(120, cancel.last()["audio_end_ms"]!!.jsonPrimitive.int)
        assertEquals(1, OpenAIRealtimeProtocol.interrupt(null, 0).size)
    }
    @Test fun `Gemini setup and interruption respect automatic server activity detection`() {
        val setup = GeminiLiveProtocol.setup(model, "identity").o("setup")
        assertEquals("models/voice-model", setup.s("model"))
        assertTrue("inputAudioTranscription" in setup)
        assertEquals("START_OF_ACTIVITY_INTERRUPTS", setup.o("realtimeInputConfig").s("activityHandling"))
        assertTrue(GeminiLiveProtocol.interrupt(null, 0).isEmpty())
        val events = GeminiLiveProtocol.events(Json.parseToJsonElement("""{"serverContent":{"interrupted":true,"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AA=="}}]},"turnComplete":true}}""").jsonObject)
        assertTrue(events.first() is RealtimeEvent.SpeechStarted)
        assertEquals(24000, (events[1] as RealtimeEvent.Audio).rate)
        assertTrue(events.last() is RealtimeEvent.ResponseDone)
    }
    @Test fun `failed response surfaces an error instead of false success`() {
        val events = OpenAIRealtimeProtocol.events(Json.parseToJsonElement("""{"type":"response.done","response":{"status":"failed"}}""").jsonObject)
        assertTrue(events.single() is RealtimeEvent.Error)
    }
    @Test fun `PCM decoder handles signed little endian and odd empty payloads`() {
        assertEquals(0f, pcmLevels(byteArrayOf()).amplitude)
        assertEquals(0f, pcmLevels(byteArrayOf(1)).amplitude)
        assertEquals(1f, pcmLevels(byteArrayOf(0, -128)).amplitude, .0001f)
        assertEquals(.5f, pcmLevels(byteArrayOf(0, 64)).amplitude, .0001f)
    }
}
