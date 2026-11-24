package me.rerere.zeroagent.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatContextMeterTest {
    @Test
    fun `usage reflects size while clamping to the progress bar range`() {
        assertEquals(0.5f, estimatedContextUsage(4_000, 8_000), 0.0001f)
        assertEquals(1f, estimatedContextUsage(16_000, 8_000), 0f)
        assertEquals(0f, estimatedContextUsage(-1, 8_000), 0f)
        assertEquals(0f, estimatedContextUsage(2_000, 0), 0f)
    }
}
