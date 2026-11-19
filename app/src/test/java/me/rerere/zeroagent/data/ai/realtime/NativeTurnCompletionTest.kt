package me.rerere.zeroagent.data.ai.realtime

import org.junit.Assert.*
import org.junit.Test

class NativeTurnCompletionTest {
    @Test fun `text mode waits for both server completion and drained playback`() {
        val turn = NativeTurnCompletion(); turn.begin()
        assertFalse(turn.finish(false, false)); assertFalse(turn.finish(true, true))
        assertTrue(turn.finish(true, false)); assertFalse(turn.finish(true, false))
    }
    @Test fun `barge in never converts a cancelled response into a completed answer`() {
        val turn = NativeTurnCompletion(); turn.begin(); turn.cancel()
        assertFalse(turn.finish(true, false))
        turn.begin(); assertTrue(turn.finish(true, false))
    }
    @Test fun `transcription deltas and text-only responses stream before completion`() {
        val delta = OpenAIRealtimeProtocol.events(obj("type" to str("conversation.item.input_audio_transcription.delta"), "delta" to str("hello")))
        assertEquals("hello", (delta.single() as RealtimeEvent.Transcript).text)
        val text = OpenAIRealtimeProtocol.text("typed", audioResponse = false)
        assertEquals("text", text.last().o("response").a("output_modalities").single().toString().trim('"'))
        assertTrue(OpenAIRealtimeProtocol.events(obj("type" to str("response.done"), "response" to obj("status" to str("cancelled")))).isEmpty())
    }
}
