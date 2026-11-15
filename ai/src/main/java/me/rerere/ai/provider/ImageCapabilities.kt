package me.rerere.ai.provider

import me.rerere.ai.provider.providers.openai.isRetiredOfficialOpenAIImageModel

/** Shared UI/tool gate: advertised image output plus an implementation for this protocol. */
fun ProviderSetting.supportsImageGeneration(model: Model, editing: Boolean = false): Boolean {
    if (!enabled) return false
    val effective = model.providerOverwrite ?: this
    if (!effective.enabled) return false
    val capabilities = model.withCatalogCapabilities()
    if (Modality.IMAGE !in capabilities.outputModalities) return false
    if (editing && Modality.IMAGE !in capabilities.inputModalities) return false
    return when (effective) {
        is ProviderSetting.OpenAI -> effective.protocol == OpenAIProtocol.OPENAI && !isRetiredOfficialOpenAIImageModel(effective.baseUrl, model.modelId)
        is ProviderSetting.Google, is ProviderSetting.Grok -> !editing
        else -> false // Cohere, Claude, AICore and subscription chat adapters have no image API.
    }
}
