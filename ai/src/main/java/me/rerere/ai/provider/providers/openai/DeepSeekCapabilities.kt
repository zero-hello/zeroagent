package me.rerere.ai.provider.providers.openai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility

/** Applied only to IDs actually returned by DeepSeek's /models endpoint.
 * Official thinking_mode and quick_start/pricing documentation, checked 2026-11-12.
 */
internal fun Model.withDeepSeekCapabilities(): Model = when (modelId) {
    "deepseek-flash", "deepseek-v4-pro", "deepseek-v4-flash" -> copy(
        abilities = (abilities + listOf(ModelAbility.REASONING, ModelAbility.TOOL)).distinct(),
        supportedReasoningEfforts = listOf("none", "low", "high", "max"),
    )
    // Legacy models have no advertised effort scale; retain AUTO only.
    "deepseek-reasoner" -> copy(abilities = (abilities + ModelAbility.REASONING).distinct())
    "deepseek-chat" -> copy(abilities = (abilities + ModelAbility.TOOL).distinct())
    else -> this
}
