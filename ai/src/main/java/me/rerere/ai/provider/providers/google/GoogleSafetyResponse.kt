package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.*

/** Shared by REST, SSE, image generation and Code Assist's unwrapped payload. */
fun checkGoogleSafetyResponse(value: JsonObject) {
    val promptReason = (value["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull
    val candidates = (value["candidates"] as? JsonArray).orEmpty()
    val candidateReason = candidates.firstNotNullOfOrNull { item ->
        val reason = (item as? JsonObject)?.get("finishReason")?.jsonPrimitive?.contentOrNull
        reason?.takeIf { it in setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT") }
    }
    val reason = promptReason?.takeUnless { it == "BLOCK_REASON_UNSPECIFIED" } ?: candidateReason ?: return
    val description = when (reason) {
        "SAFETY", "IMAGE_SAFETY" -> "內容觸發安全篩選"
        "RECITATION" -> "內容可能涉及受保護的原文"
        "SPII" -> "內容可能包含敏感個人資料"
        "BLOCKLIST", "PROHIBITED_CONTENT", "IMAGE_PROHIBITED_CONTENT" -> "內容觸發供應商不可覆寫的限制"
        else -> "供應商拒絕產生此內容"
    }
    error("Gemini 未完成回覆：$description（$reason）。請調整輸入後重試；可調整的篩選設定不會取消供應商的安全政策。")
}
