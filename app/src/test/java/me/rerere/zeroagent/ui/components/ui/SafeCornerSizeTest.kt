package me.rerere.zeroagent.ui.components.ui
import org.junit.Assert.assertEquals
import org.junit.Test
class SafeCornerSizeTest {
    @Test fun reportedCrashOvershootIsClamped() { assertEquals(0f, safeCornerSize(-0.4058966f), 0f) }
    @Test fun invalidAnimatedValuesCannotCreateAnInvalidShape() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
            assertEquals(0f, safeCornerSize(value), 0f)
    }
    @Test fun validCornersAreUnchanged() {
        for (value in listOf(0f, 24f, 67.5f)) assertEquals(value, safeCornerSize(value), 0f)
    }
}
