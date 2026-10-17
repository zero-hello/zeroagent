package me.rerere.zeroagent.ui.pages.chat
import me.rerere.zeroagent.ui.components.ai.completion.*
import org.junit.Assert.*
import org.junit.Test
class ChatSlashCommandTest {
    @Test fun `only explicit commands execute`() {
        assertEquals(ChatSlashCommand.COMPACT, parseSlashCommand("/compact")?.command)
        assertEquals("ship the app", parseSlashCommand("/goal ship the app")?.argument)
        assertNull(parseSlashCommand("/home/user/file"))
        assertNull(parseSlashCommand("Explain /plan"))
        assertNull(parseSlashCommand("/plan\nordinary content"))
    }
    @Test fun `plan and goal keep the users system prompt and each other`() {
        val start = "  My instructions\n\n"
        val goal = updateChatDirective(start, "goal", "Ship")
        val plan = updateChatDirective(goal, "plan", "Plan first")
        assertTrue(plan!!.contains("My instructions")); assertTrue(plan.contains("Ship"))
        val cleared = updateChatDirective(plan, "plan", null)!!
        assertFalse(cleared.contains("Plan first"));assertTrue(cleared.contains("Ship"))
        assertEquals(start, updateChatDirective(cleared, "goal", null))
    }
}
