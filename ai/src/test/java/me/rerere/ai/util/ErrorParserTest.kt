package me.rerere.ai.util

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ErrorParserTest {
    @Test fun `nested errors keep HTTP status and machine code for localization`() {
        val error = Json.parseToJsonElement("""{"error":{"code":"insufficient_quota","message":"Account limit reached"}}""").parseErrorDetail(429)
        assertEquals(429, error.statusCode)
        assertEquals("insufficient_quota", error.errorCode)
        assertEquals("Account limit reached", error.message)
    }
    @Test fun `streamed validation errors expose status without a separate HTTP failure`() {
        val error = Json.parseToJsonElement("""{"type":"invalid_request_error","message":"Unsupported reasoning_effort. Supported values are: 'low', 'high'."}""").parseErrorDetail()
        assertEquals(400, error.statusCode)
        assertEquals("invalid_request_error", error.errorCode)
    }
    @Test fun `null wrapper fields do not hide a useful message`() {
        val error = Json.parseToJsonElement("""{"error":null,"message":"missing model"}""").parseErrorDetail(404)
        assertEquals("missing model", error.message)
        assertEquals(404, error.statusCode)
    }

    @Test fun `a generic error wrapper does not hide the nested provider code`() {
        val error = Json.parseToJsonElement("""{"type":"error","error":{"type":"rate_limit_error","message":"slow down"}}""").parseErrorDetail()
        assertEquals(429, error.statusCode)
        assertEquals("rate_limit_error", error.errorCode)
    }
}
