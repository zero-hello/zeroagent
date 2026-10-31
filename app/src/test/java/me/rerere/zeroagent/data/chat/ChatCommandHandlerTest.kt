package me.rerere.zeroagent.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCommandHandlerTest {
    @Test
    fun `ordinary text paths unknown commands and multiline drafts are untouched`() {
        listOf("hello", "/home/user/file", "/unknown", " /plan", "/plan\nordinary content").forEach { text ->
            val fixture = Fixture("User instructions")
            assertFalse(text, fixture.handle(text))
            assertTrue(fixture.events.isEmpty())
            assertEquals("User instructions", fixture.prompt)
        }
    }

    @Test
    fun `UI commands dispatch before clearing the draft without saving or changing access`() {
        mapOf(
            "/compact" to ChatCommandEffect.COMPACT,
            "/image" to ChatCommandEffect.IMAGE,
            "/voice" to ChatCommandEffect.VOICE,
            "/access" to ChatCommandEffect.ACCESS,
            "/help" to ChatCommandEffect.HELP,
        ).forEach { (text, effect) ->
            val fixture = Fixture("User instructions")
            assertTrue(fixture.handle(text))
            assertEquals(listOf("effect:$effect", "clear"), fixture.events)
            assertEquals("User instructions", fixture.prompt)
        }
    }

    @Test
    fun `plan resets access saves exact directive and then clears after feedback`() {
        val fixture = Fixture("User instructions")
        assertTrue(fixture.handle("/PLAN inspect first"))
        assertEquals(
            "User instructions\n\n[zeroagent:plan]\n" +
                "Planning mode: provide a clear plan, inspect information as needed, and ask before making changes. inspect first" +
                "\n[/zeroagent:plan]",
            fixture.prompt,
        )
        assertEquals(listOf("ask", "save", "effect:DIRECTIVE_SAVED", "clear"), fixture.events)
    }

    @Test
    fun `plan without an argument still enables planning with the original trailing space`() {
        val fixture = Fixture(null)
        fixture.handle("/plan")
        assertEquals(
            "[zeroagent:plan]\nPlanning mode: provide a clear plan, inspect information as needed, " +
                "and ask before making changes. \n[/zeroagent:plan]",
            fixture.prompt,
        )
    }

    @Test
    fun `goal saves exact directive without changing access`() {
        val fixture = Fixture(null)
        fixture.handle("/goal  ship the app  ")
        assertEquals(
            "[zeroagent:goal]\nCurrent user goal: ship the app. Work toward it, report real progress, " +
                "and never claim completion without evidence.\n[/zeroagent:goal]",
            fixture.prompt,
        )
        assertEquals(listOf("save", "effect:DIRECTIVE_SAVED", "clear"), fixture.events)
    }

    @Test
    fun `empty goal shows usage and preserves the draft and conversation`() {
        listOf("/goal", "/goal   ").forEach { text ->
            val fixture = Fixture("User instructions")
            assertTrue(fixture.handle(text))
            assertEquals(listOf("effect:GOAL_USAGE"), fixture.events)
            assertEquals("User instructions", fixture.prompt)
        }
    }

    @Test
    fun `turning off a directive preserves user instructions and the other directive`() {
        val original = "  User instructions\n\n"
        val fixture = Fixture(original)
        fixture.handle("/goal ship")
        fixture.handle("/plan inspect")
        fixture.events.clear()
        fixture.handle("/plan OFF")
        assertTrue(fixture.prompt!!.contains("Current user goal: ship."))
        assertFalse(fixture.prompt!!.contains("[zeroagent:plan]"))
        assertEquals(listOf("save", "effect:DIRECTIVE_REMOVED", "clear"), fixture.events)
        fixture.handle("/goal off")
        assertEquals(original, fixture.prompt)
    }

    @Test
    fun `removing an absent directive still saves and returns removed feedback`() {
        val fixture = Fixture(null)
        fixture.handle("/plan off")
        assertNull(fixture.prompt)
        assertEquals(listOf("save", "effect:DIRECTIVE_REMOVED", "clear"), fixture.events)
    }

    @Test
    fun `a failed UI action does not clear the draft`() {
        val fixture = Fixture(null)
        assertThrows(IllegalStateException::class.java) {
            fixture.handler.handle("/voice") { error("Cannot start voice") }
        }
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun `a failed save does not report success or clear the draft`() {
        val events = mutableListOf<String>()
        val handler = ChatCommandHandler(
            currentPrompt = { null },
            resetAccessToAsk = { events += "ask" },
            savePrompt = { error("Cannot save") },
            clearInput = { events += "clear" },
        )
        assertThrows(IllegalStateException::class.java) {
            handler.handle("/plan inspect") { events += "effect:$it" }
        }
        assertEquals(listOf("ask"), events)
    }

    private class Fixture(var prompt: String?) {
        val events = mutableListOf<String>()
        val handler = ChatCommandHandler(
            currentPrompt = { prompt },
            resetAccessToAsk = { events += "ask" },
            savePrompt = { prompt = it; events += "save" },
            clearInput = { events += "clear" },
        )

        fun handle(text: String): Boolean = handler.handle(text) { events += "effect:$it" }
    }
}
