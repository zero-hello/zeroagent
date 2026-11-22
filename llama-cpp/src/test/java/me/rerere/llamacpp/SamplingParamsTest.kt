package me.rerere.llamacpp

import org.junit.Assert.*
import org.junit.Test

class SamplingParamsTest {
    @Test fun `greedy and top k disable values are accepted`() {
        assertEquals(0f, SamplingParams(temperature = 0f, topK = 0).temperature)
    }
    @Test fun `nonfinite and out of range sampler input is rejected`() {
        assertTrue(runCatching { SamplingParams(temperature = Float.NaN) }.isFailure)
        assertTrue(runCatching { SamplingParams(topP = 2f) }.isFailure)
        assertTrue(runCatching { SamplingParams(topK = -1) }.isFailure)
    }
}
