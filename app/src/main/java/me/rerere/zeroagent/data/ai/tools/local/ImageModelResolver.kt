package me.rerere.zeroagent.data.ai.tools.local

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.supportsImageGeneration
import me.rerere.zeroagent.data.datastore.Settings

/** Both the visible picker and tool surface resolve accounts by actual conversation model UUID. */
internal fun Settings.imageProvidersFor(modelId: String?): List<ProviderSetting> =
    (if (modelId == null) providers else providers.filter { p -> p.models.any { it.id.toString() == modelId } })
        .filter { it.enabled }

internal fun Settings.resolveImageModel(
    callerModelId: String?, requested: String? = null, editing: Boolean = false,
    preferConfigured: Boolean = false,
): Model? {
    val availableProviders = imageProvidersFor(callerModelId)
    val models = availableProviders.flatMap { p -> p.models.filter { p.supportsImageGeneration(it, editing) } }
    val configured = imageGenerationModelId.toString()
    val index = selectImageModelIndex(models.map { ImageModelChoice(it.id.toString(), it.modelId, it.displayName) },
        configuredId = configured, requested = requested,
        currentId = if (preferConfigured && models.any { it.id.toString() == configured }) null else callerModelId,
        allowSingleFallback = callerModelId != null && availableProviders.size == 1)
    return models.getOrNull(index)
}
