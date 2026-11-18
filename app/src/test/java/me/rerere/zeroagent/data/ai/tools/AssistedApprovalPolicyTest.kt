package me.rerere.zeroagent.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/** ASSISTED delegates each call to a reviewer; a static tool-name policy cannot approve it. */
class AssistedApprovalPolicyTest {

    private fun staticApproval(
        toolName: String,
        mode: ConversationAccessMode = ConversationAccessMode.ASSISTED,
        blacklist: Set<String> = emptySet(),
        allowedForChat: Boolean = false,
        yolo: Boolean = false,
    ) = ToolApprovalPolicy.shouldAutoApprove(
        toolName = toolName,
        blacklist = blacklist,
        yolo = yolo,
        headless = false,
        allowedForChat = allowedForChat,
        accessMode = mode,
    )

    @Test
    fun `an ordinary read still needs independent review before automatic execution`() {
        assertFalse(staticApproval("get_battery_info"))
        assertFalse(staticApproval("read_file"))
    }

    @Test
    fun `a previous chat grant cannot bypass review for the next call`() {
        assertFalse(staticApproval("tap", allowedForChat = true))
        assertFalse(staticApproval("get_battery_info", allowedForChat = true))
    }

    @Test
    fun `legacy yolo cannot bypass review in an attended assisted conversation`() {
        assertFalse(staticApproval("get_battery_info", yolo = true))
        assertFalse(staticApproval("tap", allowedForChat = true, yolo = true))
    }

    @Test
    fun `an unknown tool is not trusted merely because no static list mentions it`() {
        assertFalse(staticApproval("new_vendor_export_everything"))
    }

    @Test
    fun `explicit blacklist entries never gain a static assisted approval`() {
        assertFalse(
            staticApproval(
                "read_file",
                blacklist = setOf("read_file"),
                allowedForChat = true,
                yolo = true,
            )
        )
    }

    @Test
    fun `root and requests for user input retain fresh human confirmation in every attended mode`() {
        ConversationAccessMode.entries.forEach { mode ->
            assertFalse("root in $mode", staticApproval("run_root_command", mode, allowedForChat = true, yolo = true))
            assertFalse("ask_user in $mode", staticApproval("ask_user", mode, allowedForChat = true, yolo = true))
        }
    }

    @Test
    fun `ask and assisted preserve hard floor and opaque MCP gates despite grants and yolo`() {
        for (mode in listOf(ConversationAccessMode.ASK, ConversationAccessMode.ASSISTED)) {
            for (toolName in ToolApprovalDefaults.NO_ALWAYS_ALLOW + "mcp__server__arbitrary") {
                assertFalse(toolName + " in " + mode, staticApproval(toolName, mode, allowedForChat = true, yolo = true))
            }
        }
    }

    @Test
    fun `every actual mode change clears old chat grants without affecting another conversation`() {
        for (from in ConversationAccessMode.entries) {
            for (to in ConversationAccessMode.entries.filter { it != from }) {
                val conversation = Uuid.random()
                val otherConversation = Uuid.random()
                try {
                    assertTrue(ConversationAccess.set(conversation, from, highRiskConfirmed = true))
                    ToolApprovalAllowList.grantForChat(conversation, "tap")
                    ToolApprovalAllowList.grantForChat(otherConversation, "tap")

                    assertTrue(ConversationAccess.set(conversation, to, highRiskConfirmed = true))

                    assertEquals(to, ConversationAccess.mode(conversation))
                    assertFalse("stale grant after $from to $to", ToolApprovalAllowList.isAllowedForChat(conversation, "tap"))
                    assertTrue(ToolApprovalAllowList.isAllowedForChat(otherConversation, "tap"))
                } finally {
                    ConversationAccess.set(conversation, ConversationAccessMode.ASK)
                    ToolApprovalAllowList.clearChat(conversation)
                    ToolApprovalAllowList.clearChat(otherConversation)
                }
            }
        }
    }

    @Test
    fun `reselecting the same mode does not revoke existing chat grants`() {
        for (mode in ConversationAccessMode.entries) {
            val conversation = Uuid.random()
            try {
                assertTrue(ConversationAccess.set(conversation, mode, highRiskConfirmed = true))
                ToolApprovalAllowList.grantForChat(conversation, "tap")

                assertTrue(ConversationAccess.set(conversation, mode, highRiskConfirmed = true))

                assertTrue("same mode $mode", ToolApprovalAllowList.isAllowedForChat(conversation, "tap"))
            } finally {
                ConversationAccess.set(conversation, ConversationAccessMode.ASK)
                ToolApprovalAllowList.clearChat(conversation)
            }
        }
    }

    @Test
    fun `rejecting unconfirmed full access preserves the current mode and grants`() {
        val conversation = Uuid.random()
        try {
            assertTrue(ConversationAccess.set(conversation, ConversationAccessMode.ASSISTED))
            ToolApprovalAllowList.grantForChat(conversation, "tap")

            assertFalse(ConversationAccess.set(conversation, ConversationAccessMode.FULL))

            assertEquals(ConversationAccessMode.ASSISTED, ConversationAccess.mode(conversation))
            assertTrue(ToolApprovalAllowList.isAllowedForChat(conversation, "tap"))
        } finally {
            ConversationAccess.set(conversation, ConversationAccessMode.ASK)
            ToolApprovalAllowList.clearChat(conversation)
        }
    }
}
