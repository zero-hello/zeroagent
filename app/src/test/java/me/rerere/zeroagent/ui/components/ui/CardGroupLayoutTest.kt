package me.rerere.zeroagent.ui.components.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardGroupLayoutTest {
    @Test fun narrowRowsStackControls() {
        assertTrue(stackCardGroupControls(319f, 1f))
        assertFalse(stackCardGroupControls(320f, 1f))
    }
    @Test fun largeTextStacksEvenOnWideScreens() {
        assertTrue(stackCardGroupControls(600f, 1.3f))
        assertFalse(stackCardGroupControls(600f, 1.29f))
    }
}
