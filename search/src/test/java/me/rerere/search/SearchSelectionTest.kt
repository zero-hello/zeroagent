package me.rerere.search

import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSelectionTest {
    @Test fun addingAndReorderingKeepThePreviouslyChosenService() {
        val a = Uuid.random(); val b = Uuid.random(); val c = Uuid.random()
        assertEquals(2, searchSelectionAfterEdit(listOf(a, b), 1, listOf(c, a, b)))
        assertEquals(0, searchSelectionAfterEdit(listOf(a, b), 1, listOf(b, a)))
    }
    @Test fun deletingOrInvalidSelectionAlwaysLeavesABoundedIndex() {
        val a = Uuid.random(); val b = Uuid.random()
        assertEquals(0, searchSelectionAfterEdit(listOf(a, b), 1, listOf(a)))
        assertEquals(0, searchSelectionAfterEdit(listOf(a), -1, emptyList()))
    }
}
