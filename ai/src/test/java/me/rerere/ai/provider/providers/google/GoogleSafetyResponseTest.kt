package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.jsonObject
import me.rerere.ai.util.json
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSafetyResponseTest {
    @Test fun `prompt and candidate safety blocks name their processing stage`() {
        val blocks = mapOf(
            """{"promptFeedback":{"blockReason":"SAFETY"}}""" to "輸入被攔截",
            """{"candidates":[{"finishReason":"IMAGE_SAFETY"}]}""" to "回覆被中止",
        )
        blocks.forEach { (body, expectedStage) ->
            val failure = runCatching { checkGoogleSafetyResponse(json.parseToJsonElement(body).jsonObject) }
                .exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            val message = failure!!.message.orEmpty()
            assertTrue(message.contains(expectedStage))
            assertTrue(message.contains("安全"))
        }
    }

    @Test fun `ordinary model refusal text is not an API safety block`() {
        val body = """{"promptFeedback":{"blockReason":"BLOCK_REASON_UNSPECIFIED"},"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"無法協助這項要求。"}]}}]}"""
        checkGoogleSafetyResponse(json.parseToJsonElement(body).jsonObject)
    }

    @Test fun `malformed optional reasons are not API safety blocks`() {
        val body = """{"promptFeedback":{"blockReason":{}},"candidates":[{"finishReason":[]}]}"""
        checkGoogleSafetyResponse(json.parseToJsonElement(body).jsonObject)
    }
}
