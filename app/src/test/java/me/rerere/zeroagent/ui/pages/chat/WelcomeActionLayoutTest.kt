package me.rerere.zeroagent.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class WelcomeActionLayoutTest {
    @Test fun compactPhonesKeepTwoReadableActionsPerRow() {
        assertEquals(2, welcomeActionColumns(widthDp = 312f, fontScale = 1f))
    }

    @Test fun veryNarrowOrLargeTextUsesSingleColumn() {
        assertEquals(1, welcomeActionColumns(widthDp = 240f, fontScale = 1f))
        assertEquals(1, welcomeActionColumns(widthDp = 312f, fontScale = 1.5f))
        assertEquals(1, welcomeActionColumns(widthDp = 592f, fontScale = 3f))
    }

    @Test fun wideScreensScaleFromFourToTwoColumnsWithText() {
        assertEquals(4, welcomeActionColumns(widthDp = 592f, fontScale = 1f))
        assertEquals(2, welcomeActionColumns(widthDp = 592f, fontScale = 1.5f))
    }
}
