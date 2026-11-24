package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val blockedFinishReasons = setOf(
    "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
    "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT",
)

/** Shared by REST, SSE, image generation and Code Assist's unwrapped payload. */
fun checkGoogleSafetyResponse(value: JsonObject) {
    val feedback = value["promptFeedback"] as? JsonObject
    val promptReason = (feedback?.get("blockReason") as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.isNotBlank() && it != "BLOCK_REASON_UNSPECIFIED" }
    val candidates = (value["candidates"] as? JsonArray).orEmpty()
    val candidateReason = candidates.firstNotNullOfOrNull { item ->
        val candidate = item as? JsonObject
        (candidate?.get("finishReason") as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it in blockedFinishReasons }
    }
    val reason = promptReason ?: candidateReason ?: return
    val stage = if (promptReason != null) "輸入被攔截" else "回覆被中止"
    val description = when (reason) {
        "SAFETY", "IMAGE_SAFETY" -> "觸發安全篩選，API 未說明是否由可調整的設定造成"
        "RECITATION" -> "可能涉及受保護的原文"
        "SPII" -> "可能包含敏感個人資料"
        "BLOCKLIST", "PROHIBITED_CONTENT", "IMAGE_PROHIBITED_CONTENT" -> "觸發供應商的內容限制"
        else -> "API 未提供詳細原因"
    }
    error("Gemini API 回報$stage。$description（$reason）。")
}
