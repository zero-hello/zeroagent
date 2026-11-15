package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.jsonObject
import me.rerere.ai.util.json
import org.junit.Assert.*
import org.junit.Test

class GoogleSafetyResponseTest {
    @Test fun `prompt and candidate safety blocks surface actionable explanations`() {
        listOf("""{"promptFeedback":{"blockReason":"SAFETY"}}""", """{"candidates":[{"finishReason":"IMAGE_SAFETY"}]}""").forEach {
            val error = runCatching { checkGoogleSafetyResponse(json.parseToJsonElement(it).jsonObject) }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertTrue(error!!.message!!.contains("安全"))
        }
    }
    @Test fun `ordinary completion and unspecified block reason remain valid`() {
        checkGoogleSafetyResponse(json.parseToJsonElement("""{"promptFeedback":{"blockReason":"BLOCK_REASON_UNSPECIFIED"},"candidates":[{"finishReason":"STOP"}]}""").jsonObject)
    }
}
