package me.rerere.zeroagent.data.ai.realtime

import org.junit.Assert.*
import org.junit.Test

class NativeVoiceActivityTest {
    @Test fun `native conversations suppress standalone TTS until every session ends`() {
        val initial = NativeVoiceActivity.active.value
        val first = NativeVoiceActivity.acquire(); val second = NativeVoiceActivity.acquire()
        assertEquals(initial + 2, NativeVoiceActivity.active.value)
        first.close(); first.close(); assertEquals(initial + 1, NativeVoiceActivity.active.value)
        second.close(); assertEquals(initial, NativeVoiceActivity.active.value)
    }
}
