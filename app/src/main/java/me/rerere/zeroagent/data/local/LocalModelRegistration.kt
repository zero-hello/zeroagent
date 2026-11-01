package me.rerere.zeroagent.data.local

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.llamacpp.LlamaCppCatalog
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.litert.LiteRtModelMetadata

internal fun enableAfterFirstDownload(provider: ProviderSetting): ProviderSetting = when (provider) {
    is ProviderSetting.LiteRtLocal -> provider.copy(enabled = true)
    is ProviderSetting.LlamaCppLocal -> provider.copy(enabled = true)
    else -> provider
}

internal fun registerInstalledModel(provider: ProviderSetting, model: Model): ProviderSetting {
    val existing = provider.models.firstOrNull { it.modelId == model.modelId }
    return if (existing != null) {
        provider.editModel(model.copy(id = existing.id))
    } else {
        provider.addModel(model)
    }
}

internal fun deriveLocalModelCapabilities(
    runtime: LocalRuntime,
    fileName: String,
): LiteRtModelMetadata.Capabilities = when (runtime) {
    LocalRuntime.LiteRT -> LiteRtModelMetadata.deriveCapabilities(fileName)
    LocalRuntime.LlamaCpp -> {
        val entry = LlamaCppCatalog.ENTRIES.firstOrNull { it.file == fileName }
        val tags = entry?.tags.orEmpty()
        LiteRtModelMetadata.Capabilities(
            inputModalities = listOf(Modality.TEXT),
            abilities = buildList {
                if ("tools" in tags || entry == null) add(ModelAbility.TOOL)
                if ("thinking" in tags) add(ModelAbility.REASONING)
            },
        )
    }
}
