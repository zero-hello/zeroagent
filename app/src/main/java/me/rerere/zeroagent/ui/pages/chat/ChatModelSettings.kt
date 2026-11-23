package me.rerere.zeroagent.ui.pages.chat

import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.BuiltInTools
import me.rerere.zeroagent.data.datastore.assistantForModel
import me.rerere.zeroagent.data.datastore.findModelById
import me.rerere.zeroagent.ui.components.ai.SearchMode
import kotlin.uuid.Uuid
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.getCurrentChatModel

/** The composer edits model-owned inference settings; tool and memory profiles stay intact. */
internal fun Settings.withCurrentChatModelReasoning(level: ReasoningLevel): Settings {
    val model = getCurrentChatModel() ?: return this
    return withChatModelReasoning(model.id, level)
}

internal fun Settings.withChatModelReasoning(modelId: Uuid, level: ReasoningLevel): Settings {
    val model = findModelById(modelId) ?: return this
    val updated = model.copy(generation = model.generation.copy(reasoningLevel = level))
    return copy(providers = providers.map { provider -> provider.editModel(updated) })
}

internal fun Settings.withChatModelSearchMode(modelId: Uuid?, mode: SearchMode): Settings {
    val model = findModelById(modelId) ?: return this
    val profile = assistantForModel(model)
    val tools = if (mode == SearchMode.BUILT_IN) model.tools + BuiltInTools.Search else model.tools - BuiltInTools.Search
    return copy(
        assistants = assistants.map { assistant ->
            if (assistant.id == profile.id) assistant.copy(enableWebSearch = mode == SearchMode.LOCAL)
            else assistant
        },
        providers = providers.map { it.editModel(model.copy(tools = tools)) },
    )
}
