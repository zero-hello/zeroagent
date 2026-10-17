package me.rerere.zeroagent.data.ai.realtime

import org.junit.Assert.*
import org.junit.Test

class VoiceActivityGateTest {
    @Test fun `single noise frame does not interrupt but sustained voice does`() {
        val gate = VoiceActivityGate()
        assertNull(gate.accept(.1f, true, true))
        assertNull(gate.accept(.001f, true, true))
        repeat(2) { assertNull(gate.accept(.1f, true, true)) }
        assertEquals(VoiceActivityGate.Edge.START, gate.accept(.1f, true, true))
        repeat(24) { assertNull(gate.accept(0f, false, true)) }
        assertEquals(VoiceActivityGate.Edge.END, gate.accept(0f, false, true))
    }
    @Test fun `quiet speech remains detectable after a long spoken turn`() {
        val gate = VoiceActivityGate()
        repeat(200) { gate.accept(.06f, false, true) }
        repeat(25) { gate.accept(0f, false, true) }
        repeat(2) { assertNull(gate.accept(.04f, false, true)) }
        assertEquals(VoiceActivityGate.Edge.START, gate.accept(.04f, false, true))
    }
    @Test fun `muting resets the incomplete activity state`() {
        val gate = VoiceActivityGate()
        repeat(3) { gate.accept(.1f, false, true) }
        gate.reset()
        repeat(30) { assertNull(gate.accept(0f, false, true)) }
    }
}
