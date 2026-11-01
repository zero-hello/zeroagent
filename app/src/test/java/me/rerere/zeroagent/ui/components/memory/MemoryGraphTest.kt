package me.rerere.zeroagent.ui.components.memory

import me.rerere.zeroagent.data.memory.RecalledMemory
import org.junit.Assert.*
import org.junit.Test

class MemoryGraphTest {
    private fun record(id: Long, vector: List<Float>) = RecalledMemory(id, "text", 0, "calm", "c", "USER", vector = vector)

    @Test fun denseSemanticGroupKeepsBoundedUndirectedEdges() {
        val records = (1L..24L).map { record(it, listOf(1f, 0f)) }
        val edges = memoryGraphEdges(records)
        assertTrue(edges.size <= records.size * 2)
        assertEquals(edges.size, edges.distinct().size)
        assertTrue(edges.all { (i, j) -> i < j })
    }

    @Test fun unrelatedAndMissingVectorsNeverInventConnections() {
        assertTrue(memoryGraphEdges(listOf(record(1, listOf(1f, 0f)), record(2, listOf(0f, 1f)), record(3, emptyList()))).isEmpty())
        assertTrue(memoryGraphEdges(emptyList()).isEmpty())
    }

    @Test fun connectionMustPassActualSimilarityThreshold() {
        val edges = memoryGraphEdges(listOf(record(1, listOf(1f, 0f)), record(2, listOf(.9f, .4358899f)), record(3, listOf(-1f, 0f))))
        assertEquals(listOf(0 to 1), edges)
    }
}
