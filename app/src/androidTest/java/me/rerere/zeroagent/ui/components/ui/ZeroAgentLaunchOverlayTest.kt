package me.rerere.zeroagent.ui.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ZeroAgentLaunchOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun finishesOnceWithinOneSecond() {
        compose.mainClock.autoAdvance = false
        var finishes = 0
        compose.setContent { MaterialTheme { ZeroAgentLaunchOverlay { finishes++ } } }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(1, finishes) }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(1, finishes) }
    }
}
