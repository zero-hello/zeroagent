package me.rerere.zeroagent.ui.components.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsActionLayoutTest {
    @Test fun `narrow content stacks the decorative icon to preserve readable text width`() {
        assertTrue(stackSettingsDescription(240f, 1f))
        assertTrue(stackSettingsDescription(279f, 1f))
        assertFalse(stackSettingsDescription(280f, 1f))
    }

    @Test fun `large text stacks even in a wide or landscape window`() {
        assertFalse(stackSettingsDescription(600f, 1.3f))
        assertTrue(stackSettingsDescription(600f, 1.5f))
        assertTrue(stackSettingsDescription(840f, 2f))
    }
}
