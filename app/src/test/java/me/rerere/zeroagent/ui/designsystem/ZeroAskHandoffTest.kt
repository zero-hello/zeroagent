package me.rerere.zeroagent.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZeroAskHandoffTest {
    @Test
    fun draftCanOnlyBeConsumedOnceByTheIntendedChat() {
        val draft = ZeroAskDraft("選取的文字", "解釋一下")
        val token = ZeroAskHandoff.offer("chat-a", draft)
        assertNull(ZeroAskHandoff.take(null, "chat-a"))
        assertNull(ZeroAskHandoff.take(token, "chat-b"))
        assertEquals(draft, ZeroAskHandoff.take(token, "chat-a"))
        assertNull(ZeroAskHandoff.take(token, "chat-a"))
    }

    @Test
    fun quoteKeepsMarkdownInsideQuoteBlock() {
        assertEquals("> first\n> \n> # heading", quotedChatText("first\n\n# heading"))
    }
}