package me.rerere.zeroagent.data.model
import me.rerere.zeroagent.data.ai.tools.LocalToolOption
import org.junit.Assert.*
import org.junit.Test
class PhoneControlSetupTest {
    @Test fun loadingAgentCoreDoesNotEnablePhoneToolsByItself() {
        assertFalse(Assistant(enabledSkills = setOf("agent-core")).hasPhoneControlTools())
    }
    @Test fun explicitSetupAddsToolsAndCoreWithoutRemovingExistingRestrictions() {
        val before = Assistant(enabledSkills = setOf("my-skill"), disabledLocalTools = setOf("tap", "take_screenshot"))
        val after = before.withPhoneControlTools()
        assertTrue(after.hasPhoneControlTools())
        assertTrue(LocalToolOption.TimeInfo in after.localTools)
        assertEquals(setOf("my-skill", "agent-core"), after.enabledSkills)
        assertEquals(before.disabledLocalTools, after.disabledLocalTools)
        assertEquals(before.id, after.id)
    }
    @Test fun repeatedSetupIsIdempotent() {
        val once = Assistant().withPhoneControlTools()
        assertEquals(once, once.withPhoneControlTools())
    }
}
