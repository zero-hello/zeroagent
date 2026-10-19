package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import me.rerere.ai.ui.ImageGenerationItem

internal fun usesGeminiImageEndpoint(model: Model) = !model.modelId.removePrefix("models/").startsWith("imagen", ignoreCase = true)

internal fun geminiImageRequest(params: ImageGenerationParams, safety: GeminiSafetySettings): JsonObject = buildJsonObject {
    putJsonArray("contents") { add(buildJsonObject { put("role", "user"); putJsonArray("parts") { add(buildJsonObject { put("text", params.prompt) }) } }) }
    if (params.model.generation.systemPrompt.isNotBlank()) putJsonObject("systemInstruction") {
        putJsonArray("parts") { add(buildJsonObject { put("text", params.model.generation.systemPrompt) }) }
    }
    putJsonObject("generationConfig") {
        put("responseModalities", buildJsonArray { add("TEXT"); add("IMAGE") })
        putJsonObject("imageConfig") { put("aspectRatio", when (params.aspectRatio) {
            me.rerere.ai.ui.ImageAspectRatio.SQUARE -> "1:1"
            me.rerere.ai.ui.ImageAspectRatio.LANDSCAPE -> "16:9"
            me.rerere.ai.ui.ImageAspectRatio.PORTRAIT -> "9:16"
        }) }
        params.model.generation.temperature?.let { put("temperature", it) }
        params.model.generation.topP?.let { put("topP", it) }
        params.model.generation.topK?.let { put("topK", it) }
        params.model.generation.maxTokens?.let { put("maxOutputTokens", it) }
    }
    if (safety.thresholds.isNotEmpty()) putJsonArray("safetySettings") {
        safety.thresholds.filterKeys { it in GOOGLE_SAFETY_CATEGORIES }.forEach { (category, threshold) -> add(buildJsonObject { put("category", category); put("threshold", threshold.name) }) }
    }
}

internal fun geminiGeneratedImages(root: JsonObject): List<ImageGenerationItem> =
    (root["candidates"] as? JsonArray).orEmpty().flatMap { candidate ->
        ((candidate as? JsonObject)?.get("content") as? JsonObject)?.get("parts").let { it as? JsonArray }.orEmpty().mapNotNull { part ->
            val data = (part as? JsonObject)?.get("inlineData") as? JsonObject ?: (part as? JsonObject)?.get("inline_data") as? JsonObject ?: return@mapNotNull null
            val bytes = data["data"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val mime = (data["mimeType"] ?: data["mime_type"])?.jsonPrimitive?.contentOrNull ?: "image/png"
            if (bytes.isBlank() || !mime.startsWith("image/")) null else ImageGenerationItem(bytes, mime)
        }
    }
