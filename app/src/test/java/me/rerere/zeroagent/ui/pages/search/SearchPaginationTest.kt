package me.rerere.zeroagent.ui.pages.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPaginationTest {
    @Test
    fun matchesBeyondTheFirstPageKeepTheDatabaseOrder() {
        val data = (0 until 130).toList().reversed()
        val requestedOffsets = mutableListOf<Int>()
        val results = runBlocking {
            collectMatchingMessages(limit = 2, pageSize = 50, load = { offset ->
                requestedOffsets += offset
                data.drop(offset).take(50)
            }, matches = { it in setOf(17, 8, 2) })
        }
        assertEquals(listOf(17, 8), results)
        assertEquals(listOf(0, 50, 100), requestedOffsets)
    }

    @Test
    fun noMatchesStopsAtTheEndOfTheIndex() {
        val requestedOffsets = mutableListOf<Int>()
        val results = runBlocking {
            collectMatchingMessages(limit = 2, pageSize = 2, load = { offset ->
                requestedOffsets += offset
                listOf(1, 2).drop(offset)
            }, matches = { false })
        }
        assertEquals(emptyList<Int>(), results)
        assertEquals(listOf(0, 2), requestedOffsets)
    }
}
