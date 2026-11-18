package me.rerere.zeroagent.data.productivity

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ConversationSearchFilterTest {
    @Test fun `filter operators preserve the remaining search text`() {
        val result = ConversationSearchFilter.parse("invoice after:2026-01-01 model:\"GPT example\" type:document tag:工作")
        assertEquals("invoice", result.text)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.after)
        assertEquals("GPT example", result.model)
        assertEquals("document", result.type)
        assertEquals("工作", result.tag)
    }
    @Test fun `invalid dates and attachment kinds are rejected`() {
        assertTrue(runCatching { ConversationSearchFilter.parse("after:2026-99-99") }.isFailure)
        assertTrue(runCatching { ConversationSearchFilter.parse("type:executable") }.isFailure)
    }
}
