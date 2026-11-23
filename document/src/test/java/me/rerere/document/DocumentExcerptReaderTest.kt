package me.rerere.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentExcerptReaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun completeReadingPreservesTextOrReportsLimit() {
        val file = temporary.newFile().apply { writeText("測試🐾") }
        assertEquals("測試🐾", DocumentExcerptReader.readComplete(file, "text/plain", 4))
        assertThrows(IllegalArgumentException::class.java) {
            DocumentExcerptReader.readComplete(file, "text/plain", 3)
        }
    }

    @Test
    fun textBudgetDoesNotLeaveHalfOfAnEmoji() {
        val file = temporary.newFile().apply { writeText("ab🐾c") }
        val result = requireNotNull(DocumentExcerptReader.read(file, "text/plain", 3))
        assertEquals("ab", result.text)
        assertTrue(result.truncated)
    }
}
