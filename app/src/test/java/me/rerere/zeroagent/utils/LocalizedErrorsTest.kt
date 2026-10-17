package me.rerere.zeroagent.utils

import me.rerere.ai.util.HttpException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException

class LocalizedErrorsTest {
    @Test fun `status code and nested causes retain actionable error meaning`() {
        assertEquals(UserErrorKind.AUTH, classifyUserError(HttpException("raw service message", 401)))
        assertEquals(UserErrorKind.AUTH, classifyUserError(HttpException("permission denied", 403)))
        assertEquals(UserErrorKind.RATE, classifyUserError(raw = "HTTP 429"))
        assertEquals(UserErrorKind.RATE, classifyUserError(HttpException("raw service message", 429)))
        assertEquals(UserErrorKind.QUOTA, classifyUserError(HttpException("insufficient_quota", 429)))
        assertEquals(UserErrorKind.TIMEOUT, classifyUserError(RuntimeException("request failed", SocketTimeoutException())))
    }
    @Test fun `local model and reasoning failures are not mislabeled as network failures`() {
        assertEquals(UserErrorKind.CONTEXT, classifyUserError(raw = "Input token ids are too long: 18031 >= 4096"))
        assertEquals(UserErrorKind.REASONING, classifyUserError(raw = "unsupported reasoning_effort: max"))
        assertEquals(UserErrorKind.CORRUPT, classifyUserError(raw = "Model file corrupt: bad magic"))
        assertEquals(UserErrorKind.MEMORY, classifyUserError(OutOfMemoryError()))
    }
    @Test fun `arbitrary provider text stays diagnostic rather than becoming a UI translation`() {
        assertEquals(UserErrorKind.UNKNOWN, classifyUserError(raw = "vendor-specific diagnostic"))
        assertEquals(UserErrorKind.PERMISSION, classifyUserError(SecurityException("denied")))
    }

    @Test fun `format and unconfigured features retain their own localized categories`() {
        assertEquals(UserErrorKind.FORMAT, classifyUserError(raw = "Unexpected JSON token"))
        assertEquals(UserErrorKind.CONFIG, classifyUserError(raw = "ZeroAgent update source is not configured"))
    }
}
