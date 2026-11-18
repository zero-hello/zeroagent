package me.rerere.zeroagent.data.ai.tools
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlin.uuid.Uuid
class ConversationAccessTest {
    private val store = MemoryAccessStore()
    @Before fun reset() { ConversationAccess.initialize(store) }
    @After fun finish() { ConversationAccess.initialize(MemoryAccessStore()) }

    @Test fun `full access requires confirmation and becomes the saved default for new chats`() {
        val id = Uuid.random(); val other = Uuid.random()
        assertFalse(ConversationAccess.set(id, ConversationAccessMode.FULL))
        assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(id))
        assertTrue(ConversationAccess.set(id, ConversationAccessMode.FULL, true))
        assertEquals(ConversationAccessMode.FULL, ConversationAccess.mode(other))
        ToolApprovalAllowList.grantForChat(id, "tap")
        ConversationAccess.set(id, ConversationAccessMode.ASK)
        assertFalse(ToolApprovalAllowList.isAllowedForChat(id, "tap"))
        assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(id))
    }
    @Test fun `restart restores explicit chat choices and the latest default`() {
        val old = Uuid.random()
        val selected = Uuid.random()
        ConversationAccess.set(old, ConversationAccessMode.ASK)
        ConversationAccess.set(selected, ConversationAccessMode.FULL, true)
        ToolApprovalAllowList.grantForChat(selected, "tap")
        ConversationAccess.initialize(store)
        assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(old))
        assertEquals(ConversationAccessMode.FULL, ConversationAccess.mode(selected))
        assertEquals(ConversationAccessMode.FULL, ConversationAccess.mode(Uuid.random()))
        ToolApprovalAllowList.clearChat(selected)
    }
    @Test fun `failed persistence never changes the live mode or revokes existing grants`() {
        val id = Uuid.random()
        ConversationAccess.set(id, ConversationAccessMode.ASK)
        ToolApprovalAllowList.grantForChat(id, "tap")
        val snapshot = ConversationAccess.snapshot(id)
        store.failWrites = true
        assertFalse(ConversationAccess.set(id, ConversationAccessMode.FULL, true))
        assertEquals(snapshot, ConversationAccess.snapshot(id))
        assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(Uuid.random()))
        assertTrue(ToolApprovalAllowList.isAllowedForChat(id, "tap"))
        ToolApprovalAllowList.clearChat(id)
    }
    @Test fun `three modes gate the real policy and root always asks`() {
        fun allow(name: String, mode: ConversationAccessMode) = ToolApprovalPolicy.shouldAutoApprove(name, emptySet(), true, false, false, accessMode = mode)
        assertFalse(allow("tap", ConversationAccessMode.ASK))
        assertFalse(allow("tap", ConversationAccessMode.ASSISTED))
        assertFalse(allow("get_battery_info", ConversationAccessMode.ASSISTED))
        assertTrue(allow("tap", ConversationAccessMode.FULL))
        assertFalse(allow("run_root_command", ConversationAccessMode.FULL))
        assertFalse(allow("ask_user", ConversationAccessMode.FULL))
        assertTrue(ToolApprovalPolicy.shouldAutoApprove("tap", emptySet(), false, false, true, accessMode = ConversationAccessMode.ASK))
    }
}

private class MemoryAccessStore : ConversationAccessStore {
    private var saved = SavedConversationAccess()
    var failWrites = false
    override fun load() = saved
    override fun save(id: Uuid, mode: ConversationAccessMode): Boolean {
        if (failWrites) return false
        saved = SavedConversationAccess(mode, saved.modes + (id to mode))
        return true
    }
}
