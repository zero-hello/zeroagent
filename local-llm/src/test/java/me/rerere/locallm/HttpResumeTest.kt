package me.rerere.locallm

import org.junit.Assert.*
import org.junit.Test

class HttpResumeTest {
    @Test fun `valid range requires known bounds`() {
        assertEquals(HttpResume.Range(8, 15, 16), HttpResume.parseRange("bytes 8-15/16"))
        listOf(null, "bytes 8-7/16", "bytes 0-16/16", "bytes 8-15/*", "bytes 999999999999999999999-2/3")
            .forEach { assertNull(HttpResume.parseRange(it)) }
    }
    @Test fun `only exact range and matching validator may append`() {
        val range = HttpResume.Range(8, 15, 16)
        assertTrue(HttpResume.canAppend(8, range, "\"same\"", "\"same\""))
        assertFalse(HttpResume.canAppend(7, range, "\"same\"", "\"same\""))
        assertFalse(HttpResume.canAppend(8, range, "\"old\"", "\"new\""))
        assertFalse(HttpResume.canAppend(8, range, null, null))
    }
    @Test fun `weak etags cannot be used with If-Range`() {
        assertNull(HttpResume.validator("W/\"hash\"", null))
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", HttpResume.validator("W/\"hash\"", "Wed, 21 Oct 2015 07:28:00 GMT"))
    }
}
