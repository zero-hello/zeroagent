package me.rerere.common.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingTest {
    @Test
    fun requestDiagnosticsAreOptIn() {
        val entry = LogEntry.RequestLog(tag = "HTTP", url = "HTTPS", method = "GET", responseCode = 200)
        Logging.clear()
        try {
            Logging.setRequestLoggingEnabled(false)
            Logging.logRequest(entry)
            assertTrue(Logging.getRequestLogs().isEmpty())

            Logging.setRequestLoggingEnabled(true)
            Logging.logRequest(entry)
            assertEquals(listOf(entry), Logging.getRequestLogs())
        } finally {
            Logging.setRequestLoggingEnabled(false)
            Logging.clear()
        }
    }
}
