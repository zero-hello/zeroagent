package me.rerere.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentExcerptReaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun textBudgetDoesNotLeaveHalfOfAnEmoji() {
        val file = temporary.newFile().apply { writeText("ab🐾c") }
        val result = requireNotNull(DocumentExcerptReader.read(file, "text/plain", 3))
        assertEquals("ab", result.text)
        assertTrue(result.truncated)
    }
}
