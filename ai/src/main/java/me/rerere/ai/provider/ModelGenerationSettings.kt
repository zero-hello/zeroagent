package me.rerere.ai.provider

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel

/** User-owned inference settings belong to the selected model, never to its display label. */
@Serializable
data class ModelGenerationSettings(
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val streamOutput: Boolean = true,
    val contextMessageLimit: Int = 0,
    val maxToolCallsPerTurn: Int? = null,
    val monthlyBudgetUsd: Double? = null,
    val taskBudgetUsd: Double? = null,
    val taskOutputTokenLimit: Int? = null,
    val fallbackModelIds: Set<String> = emptySet(),
    val warmOnChatOpen: Boolean = false,
)

@Serializable
enum class GeminiSafetyThreshold {
    HARM_BLOCK_THRESHOLD_UNSPECIFIED, BLOCK_LOW_AND_ABOVE, BLOCK_MEDIUM_AND_ABOVE,
    BLOCK_ONLY_HIGH, BLOCK_NONE, OFF,
}

/** Empty overrides retain the provider default; unsupported categories are never fabricated. */
@Serializable
data class GeminiSafetySettings(val thresholds: Map<String, GeminiSafetyThreshold> = emptyMap())

object ModelParameterCapabilities {
    fun supports(model: Model, provider: ProviderSetting?, parameter: String): Boolean {
        if (model.type == ModelType.IMAGE) return provider is ProviderSetting.Google && model.modelId.startsWith("gemini", true) && parameter in setOf("system_prompt", "temperature", "top_p", "top_k", "max_tokens")
        if (provider is ProviderSetting.LiteRtLocal && parameter == "max_tokens") return false
        if (parameter == "system_prompt") return model.type == ModelType.CHAT
        if (model.supportedParameters.isNotEmpty()) return parameter in model.supportedParameters ||
            (parameter == "max_tokens" && "max_output_tokens" in model.supportedParameters)
        if (provider is ProviderSetting.OpenAI && (model.realtimeVoice.enabled || "realtime" in model.modelId.lowercase())) return parameter in setOf("system_prompt", "max_tokens")
        return when (provider) {
            is ProviderSetting.Google, is ProviderSetting.GeminiOAuth -> parameter in setOf("temperature", "top_p", "top_k", "max_tokens", "system_prompt")
            is ProviderSetting.LiteRtLocal, is ProviderSetting.LlamaCppLocal -> parameter in setOf("temperature", "top_p", "top_k", "max_tokens", "system_prompt")
            is ProviderSetting.Claude -> parameter in setOf("temperature", "top_p", "top_k", "max_tokens", "system_prompt")
            is ProviderSetting.OpenAI -> parameter in setOf("max_tokens", "system_prompt") ||
                (parameter in setOf("temperature", "top_p") && !model.modelId.lowercase().matches(Regex("^(o[1-9]|gpt-[5-9]).*")))
            is ProviderSetting.AICore -> parameter in setOf("max_tokens", "system_prompt")
            else -> parameter in setOf("max_tokens", "system_prompt")
        }
    }
}
