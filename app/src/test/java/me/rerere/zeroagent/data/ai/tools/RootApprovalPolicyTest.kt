package me.rerere.zeroagent.data.ai.tools
import org.junit.Assert.*
import org.junit.Test
class RootApprovalPolicyTest {
    @Test fun rootNeverInheritsAutomaticOrChatApproval() {
        for (yolo in listOf(false, true)) for (headless in listOf(false, true)) for (chat in listOf(false, true)) {
            assertFalse(ToolApprovalPolicy.shouldAutoApprove("run_root_command", emptySet(), yolo, headless, chat))
        }
    }
    @Test fun rootIsInTheUnattendedRefusalFloor() {
        assertTrue("run_root_command" in ToolApprovalDefaults.NO_ALWAYS_ALLOW)
        assertTrue("run_root_command" in HeadlessToolApprovalPolicy.PER_CALL_CONFIRM_TOOL_NAMES)
    }
    @Test fun verifiedRootGrantSkipsRepeatedForegroundPromptsOnly() {
        for (mode in ConversationAccessMode.entries) {
            assertTrue(ToolApprovalPolicy.shouldAutoApprove("run_root_command", emptySet(), false, false, false,
                accessMode = mode, rootAccessGranted = true))
            assertFalse(ToolApprovalPolicy.shouldAutoApprove("run_root_command", emptySet(), true, true, true,
                accessMode = mode, rootAccessGranted = true))
        }
    }
    @Test fun terminalUsesVerifiedRootPreferenceWithoutRelaxingTheNormalPermissionMode() {
        for (mode in ConversationAccessMode.entries) {
            assertTrue(ToolApprovalPolicy.shouldAutoApprove("termux_run_command", emptySet(), false, false, false,
                accessMode = mode, rootAccessGranted = true))
            assertFalse(ToolApprovalPolicy.shouldAutoApprove("termux_run_command", emptySet(), true, true, true,
                accessMode = mode, rootAccessGranted = true))
        }
        assertFalse(ToolApprovalPolicy.shouldAutoApprove("termux_run_command", emptySet(), false, false, false,
            accessMode = ConversationAccessMode.ASK, rootAccessGranted = false))
    }
    @Test fun unattendedRootCallReturnsAnExplicitRefusal() {
        assertNotNull(HeadlessToolApprovalPolicy.refusalEnvelopeFor("run_root_command", headless = true))
        assertNull(HeadlessToolApprovalPolicy.refusalEnvelopeFor("run_root_command", headless = false))
    }
}

