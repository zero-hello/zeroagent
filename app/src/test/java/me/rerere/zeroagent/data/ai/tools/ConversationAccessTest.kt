package me.rerere.zeroagent.data.ai.tools
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid
class ConversationAccessTest {
    @Test fun `full access requires explicit confirmation and stays scoped to one chat`() {
        val id = Uuid.random(); val other = Uuid.random()
        assertFalse(ConversationAccess.set(id, ConversationAccessMode.FULL))
        assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(id))
        assertTrue(ConversationAccess.set(id, ConversationAccessMode.FULL, true))
        assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(other))
        ToolApprovalAllowList.grantForChat(id, "tap")
        ConversationAccess.set(id, ConversationAccessMode.ASK)
        assertFalse(ToolApprovalAllowList.isAllowedForChat(id, "tap"))
        assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(id))
    }
    @Test fun `three modes gate the real policy and root always asks`() {
        fun allow(name: String, mode: ConversationAccessMode) = ToolApprovalPolicy.shouldAutoApprove(name, emptySet(), true, false, false, accessMode = mode)
        assertFalse(allow("tap", ConversationAccessMode.ASK))
        assertFalse(allow("tap", ConversationAccessMode.ASSISTED))
        assertTrue(allow("get_battery_info", ConversationAccessMode.ASSISTED))
        assertTrue(allow("tap", ConversationAccessMode.FULL))
        assertFalse(allow("run_root_command", ConversationAccessMode.FULL))
        assertFalse(allow("ask_user", ConversationAccessMode.FULL))
        assertTrue(ToolApprovalPolicy.shouldAutoApprove("tap", emptySet(), false, false, true, accessMode = ConversationAccessMode.ASK))
    }
}
