package me.rerere.zeroagent.ui.pages.assistant.detail

import org.junit.Assert.*
import org.junit.Test

class LocalToolSelectionTest {
    private val tools = mapOf("phone" to listOf("tap", "swipe"), "web" to listOf("fetch"), "nfc" to listOf("read_nfc"))
    @Test fun `select all restores individual switches only in available groups`() {
        val selected = selectLocalTools(listOf("phone"), setOf("tap", "fetch", "read_nfc", "unknown"), tools, listOf("phone", "web"), true)
        assertEquals(listOf("phone", "web"), selected.groups)
        assertEquals(setOf("read_nfc", "unknown"), selected.disabledTools)
    }
    @Test fun `deselect all disables known groups without erasing future saved groups`() {
        val selected = selectLocalTools(listOf("phone", "future"), setOf("unknown"), tools, listOf("phone", "web"), false)
        assertEquals(listOf("future"), selected.groups)
        assertEquals(setOf("tap", "swipe", "fetch", "read_nfc", "unknown"), selected.disabledTools)
    }
}
