package me.rerere.zeroagent.data.db.dao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePayloadTest {
    @Test
    fun unicodeAcrossByteChunksIsPreserved() = runBlocking {
        val text = "a".repeat(128 * 1024 - 1) + "🌱對話內容".repeat(12_000)
        val bytes = text.encodeToByteArray()
        val reads = mutableListOf<Long>()
        val result = assembleMessagePayload(bytes.size) { offset, length ->
            reads.add(offset)
            val start = (offset - 1).toInt()
            bytes.copyOfRange(start, start + length)
        }
        assertEquals(text, result)
        assertEquals(listOf(1L, 128 * 1024L + 1, 256 * 1024L + 1), reads)
    }

    @Test
    fun truncatedPayloadDoesNotReturnPartialHistory() = runBlocking {
        val result = runCatching {
            assembleMessagePayload(12) { _, _ -> byteArrayOf(1, 2) }
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
