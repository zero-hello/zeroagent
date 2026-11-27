package me.rerere.ai.provider.providers.openai

import java.net.URI
import kotlinx.serialization.json.*
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.util.mergeCustomBody

/** Exact documented IDs only. Unknown compatible models receive no guessed optional fields. */
private val GPT_IMAGE_IDS = setOf("gpt-image-1", "gpt-image-1-mini", "gpt-image-1.5", "gpt-image-2", "gpt-image-2.5", "gpt-image-2.6")

fun isRetiredOfficialOpenAIImageModel(baseUrl: String, modelId: String): Boolean =
    runCatching { URI(baseUrl).host.equals("api.openai.com", ignoreCase = true) }.getOrDefault(false) &&
        modelId.lowercase() in setOf("dall-e-2", "dall-e-3", "gpt-image-1")

internal fun standardImageRequestCopies(modelId: String, count: Int): Int =
    if (modelId.equals("dall-e-3", true)) count.coerceIn(1, 10) else 1

internal fun standardImageRequest(baseUrl: String, params: ImageGenerationParams): JsonObject {
    require(!isRetiredOfficialOpenAIImageModel(baseUrl, params.model.modelId)) {
        "此 OpenAI 圖片模型已退役，請重新整理供應商模型清單並選擇目前可用的 GPT Image 模型。"
    }
    val id = params.model.modelId.lowercase()
    val body = buildJsonObject {
        put("model", params.model.modelId)
        put("prompt", params.prompt)
        when {
            id == "dall-e-2" -> {
                require(params.aspectRatio == ImageAspectRatio.SQUARE) { "DALL·E 2 僅支援正方形，請將圖片比例改為正方形。" }
                put("n", params.numOfImages.coerceIn(1, 10))
                put("size", "1024x1024")
                put("response_format", "b64_json")
            }
            id == "dall-e-3" -> {
                put("n", 1) // Count > 1 is fulfilled with separate requests.
                put("size", when (params.aspectRatio) {
                    ImageAspectRatio.SQUARE -> "1024x1024"
                    ImageAspectRatio.LANDSCAPE -> "1792x1024"
                    ImageAspectRatio.PORTRAIT -> "1024x1792"
                })
                put("response_format", "b64_json")
            }
            id in GPT_IMAGE_IDS -> {
                put("n", params.numOfImages.coerceIn(1, 10))
                put("size", when (params.aspectRatio) {
                    ImageAspectRatio.SQUARE -> "1024x1024"
                    ImageAspectRatio.LANDSCAPE -> "1536x1024"
                    ImageAspectRatio.PORTRAIT -> "1024x1536"
                })
                // GPT Image always returns base64; response_format is unsupported.
            }
        }
    }.mergeCustomBody(params.customBody)
    require(body["model"]?.jsonPrimitive?.contentOrNull == params.model.modelId) {
        "請在模型選單選擇實際模型，勿使用自訂參數覆寫 model。"
    }
    if (id in GPT_IMAGE_IDS) require("response_format" !in body) {
        "GPT Image 不支援 response_format；請從模型的自訂參數移除此欄位。"
    }
    if (id == "dall-e-2" || id == "dall-e-3" || id in GPT_IMAGE_IDS) {
        val n = body["n"]?.jsonPrimitive?.intOrNull
        require(n != null && n in 1..10 && (id != "dall-e-3" || n == 1)) { "圖片張數參數無效，請檢查自訂 n。" }
    }
    if (id == "dall-e-2" || id == "dall-e-3") {
        val sizes = if (id == "dall-e-2") setOf("256x256", "512x512", "1024x1024")
            else setOf("1024x1024", "1792x1024", "1024x1792")
        require(body["size"]?.jsonPrimitive?.contentOrNull in sizes) { "此模型不支援自訂 size，請改用其支援的圖片尺寸。" }
        require(body["response_format"]?.jsonPrimitive?.contentOrNull in setOf("url", "b64_json")) { "response_format 必須是 url 或 b64_json。" }
    }
    return body
}
