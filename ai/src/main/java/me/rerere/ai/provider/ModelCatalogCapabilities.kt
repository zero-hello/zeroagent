package me.rerere.ai.provider

import kotlinx.serialization.json.*
import me.rerere.ai.registry.ModelRegistry

/** Catalog declarations take precedence; known image families repair older text-only defaults. */
fun Model.withCatalogCapabilities(metadata: JsonObject? = null): Model {
    val id = modelId.lowercase()
    val declared = (metadata?.get("output_modalities") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    val image = "image" in declared || id.startsWith("gpt-image") || id.startsWith("dall-e") || id.startsWith("imagen") ||
        (id.startsWith("gemini") && ("image" in id || "nano-banana" in id))
    val output = if (image) { if ("text" in declared || id.startsWith("gemini")) listOf(Modality.TEXT, Modality.IMAGE) else listOf(Modality.IMAGE) } else outputModalities
    val parameters = (metadata?.get("supported_parameters") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: supportedParameters
    return copy(type = if (image && type == ModelType.CHAT) ModelType.IMAGE else type,
        outputModalities = output, inputModalities = if (image && (id.startsWith("gpt-image") || id.startsWith("gemini"))) (inputModalities + Modality.IMAGE).distinct() else inputModalities,
        supportedParameters = parameters)
}
