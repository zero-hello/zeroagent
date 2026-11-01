package me.rerere.zeroagent.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelGenerationSettings
import me.rerere.zeroagent.data.model.Assistant

/** Storage compatibility only: model profiles reuse the existing tool/memory schema. */
fun Settings.assistantForModel(model: Model): Assistant {
    val profile = assistants.firstOrNull { it.id.toString() == model.behaviorProfileId }
        ?: assistants.firstOrNull { it.chatModelId == model.id }
        ?: assistants.firstOrNull { it.id == assistantId }
        ?: assistants.firstOrNull() ?: Assistant()
    return profile.copy(name = "ZeroAgent", chatModelId = model.id, agentIdentity = "",
        systemPrompt = model.generation.systemPrompt, temperature = model.generation.temperature,
        topP = model.generation.topP, maxTokens = model.generation.maxTokens,
        reasoningLevel = model.generation.reasoningLevel, streamOutput = model.generation.streamOutput,
        contextMessageLimit = model.generation.contextMessageLimit, allowConversationSystemPrompt = true,
        presetMessages = emptyList())
}

/** Bind existing tool/memory scopes without duplicating or orphaning stored memories. */
internal fun Settings.withModelProfiles(): Settings {
    if (init) return this
    val profiles = assistants.ifEmpty { listOf(Assistant(name = "ZeroAgent")) }
    val byId = profiles.associateBy { it.id.toString() }
    val byModel = profiles.filter { it.chatModelId != null }.associateBy { it.chatModelId }
    val current = profiles.firstOrNull { it.id == assistantId } ?: profiles.first()
    val migrated = providers.map { provider ->
        provider.copyProvider(models = provider.models.map { model ->
            if (model.behaviorProfileId.isNotBlank() && model.behaviorProfileId in byId) model
            else {
                val original = byModel[model.id] ?: current
                val generation = if (model.generation == ModelGenerationSettings()) ModelGenerationSettings(
                    systemPrompt = original.systemPrompt, temperature = original.temperature, topP = original.topP,
                    maxTokens = original.maxTokens, reasoningLevel = original.reasoningLevel,
                    streamOutput = original.streamOutput, contextMessageLimit = original.contextMessageLimit,
                ) else model.generation
                model.copy(behaviorProfileId = original.id.toString(), generation = generation,
                    customHeaders = (model.customHeaders + original.customHeaders).distinctBy { it.name },
                    customBodies = (model.customBodies + original.customBodies).distinctBy { it.key })
            }
        })
    }
    return copy(providers = migrated, assistants = profiles.map { if (it.name == "ZeroAgent" && it.agentIdentity.isBlank()) it else it.copy(name = "ZeroAgent", agentIdentity = "") })
}
