package me.rerere.zeroagent.data.datastore

import me.rerere.ai.provider.*

/** One speech-service selection, backed by the existing provider account/model store. */
fun Settings.nativeVoiceSelection(): Pair<Model, ProviderSetting>? {
    val config = voiceConversation
    if (!config.enabled || config.modelId.isBlank()) return null
    val provider = providers.firstOrNull { it.id.toString() == config.providerId && it.enabled } ?: return null
    val key = when (provider) {
        is ProviderSetting.Google -> if (provider.vertexAI || provider.useServiceAccount) return null else provider.apiKey
        is ProviderSetting.OpenAI -> provider.apiKey
        else -> return null
    }
    if (key.isBlank()) return null
    val selected = provider.models.firstOrNull { it.modelId.removePrefix("models/") == config.modelId.removePrefix("models/") }
        ?: provider.models.firstOrNull { it.realtimeVoice.enabled && it.realtimeVoice.modelId.removePrefix("models/") == config.modelId.removePrefix("models/") }?.copy(modelId = config.modelId)
        ?: return null
    return selected.copy(realtimeVoice = config) to provider
}
