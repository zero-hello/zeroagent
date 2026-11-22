package me.rerere.zeroagent.ui.components.memory

import me.rerere.zeroagent.data.model.AssistantMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphTest {
    @Test
    fun graphUsesSavedAssistantMemoriesAndLinksSharedTopics() {
        val memories = listOf(
            AssistantMemory(1, "Prefers concise Chinese replies"),
            AssistantMemory(2, "Prefers concise Chinese answers"),
            AssistantMemory(3, "Plans to visit Mars next year"),
        )

        assertEquals(listOf(0 to 1), assistantMemoryGraphEdges(memories))
    }

    @Test
    fun chineseTextProducesTermsAndEmptyMemoriesProduceNoEdges() {
        assertTrue(memoryGraphTerms("偏好繁體中文回覆").isNotEmpty())
        assertTrue(assistantMemoryGraphEdges(emptyList()).isEmpty())
        assertTrue(assistantMemoryGraphEdges(listOf(AssistantMemory(1, ""))).isEmpty())
    }

    @Test
    fun similarGroupsKeepAtMostTwoLinksPerMemory() {
        val memories = (1..24).map { AssistantMemory(it, "shared preference topic $it") }
        val edges = assistantMemoryGraphEdges(memories)
        assertTrue(edges.size <= memories.size * 2)
        assertEquals(edges.size, edges.distinct().size)
        assertTrue(edges.all { (left, right) -> left < right })
    }
}
