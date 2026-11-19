package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.ui.state.ToggleableState
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolApprovalSelectionTest {
    private val adjustable = setOf("time_info", "read_file")

    @Test fun `only rules in the adjustable scope count toward the bulk state`() {
        assertEquals(ToggleableState.Off, toolApprovalSelectionState(setOf("workspace_shell", "locked_tool"), adjustable))
        assertEquals(ToggleableState.Indeterminate, toolApprovalSelectionState(setOf("time_info", "locked_tool"), adjustable))
        assertEquals(ToggleableState.On, toolApprovalSelectionState(adjustable + "locked_tool", adjustable))
    }

    @Test fun `empty adjustable scope is never reported as all selected`() {
        assertEquals(ToggleableState.Off, toolApprovalSelectionState(setOf("locked_tool"), emptySet()))
    }
}
