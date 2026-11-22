package me.rerere.zeroagent.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyMotionTest {
    @Test
    fun onlyNewTextAnimates() {
        assertEquals(2, trailingRevealStart("第一", "第一段")?.toInt())
    }

    @Test
    fun changingAlreadyVisibleMarkdownDoesNotReplayTheText() {
        assertNull(trailingRevealStart("first *", "first bold"))
        assertNull(trailingRevealStart("same", "same"))
    }

    @Test
    fun aLargeChunkCapsTheTailWithoutSplittingAnEmoji() {
        val next = "x🐾" + "a".repeat(119)
        assertEquals(3, trailingRevealStart("x", next)?.toInt())
    }
}