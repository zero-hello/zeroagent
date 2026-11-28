package me.rerere.zeroagent.ui.motion

import androidx.compose.ui.text.TextRange
import me.rerere.zeroagent.data.ai.selectPromptMemories
import me.rerere.zeroagent.data.model.AssistantMemory
import me.rerere.zeroagent.ui.components.ai.completion.ChatCompletionContext
import me.rerere.zeroagent.ui.components.ai.completion.slashCompletionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroFlowRegressionTest {
    @Test fun slashMatchesPartialTokenWithoutTouchingOtherText() {
        val text = "前言 /compact 內容"
        assertEquals(TextRange(3, 11), slashCompletionRange(ChatCompletionContext(text, TextRange(7))))
        assertNull(slashCompletionRange(ChatCompletionContext("words ", TextRange(6))))
        assertNull(slashCompletionRange(ChatCompletionContext("https://example.com/path", TextRange(24))))
        assertNull(slashCompletionRange(ChatCompletionContext("/help", TextRange(1, 5))))
    }

    @Test fun replySlicesPreserveSurrogatePairsAndCatchUp() {
        val target = "一二三四🐈這一段應逐步出現"
        var visible = nextReplySlice("", target)
        assertEquals("一二三四🐈", visible)
        repeat(10) { visible = nextReplySlice(visible, target) }
        assertEquals(target, visible)
        assertEquals("更正", nextReplySlice(visible, "更正"))
    }

    @Test fun recallSurfacesOlderRelevantChineseMemoryWithBoundedHistory() {
        val older = AssistantMemory(1, "深度重構時保留原有工作流程")
        val recent = (2..80).map { AssistantMemory(it, "事項 $it") }
        val result = selectPromptMemories(listOf(older) + recent, "我要重構流程")
        assertEquals(older, result.first())
        assertTrue(result.size <= 24)
        assertEquals(8, selectPromptMemories(recent, "").size)
    }
}