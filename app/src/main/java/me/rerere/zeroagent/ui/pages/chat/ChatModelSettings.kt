package me.rerere.zeroagent.ui.pages.chat

import me.rerere.ai.core.ReasoningLevel
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.getCurrentChatModel

/** The composer edits model-owned inference settings; tool and memory profiles stay intact. */
internal fun Settings.withCurrentChatModelReasoning(level: ReasoningLevel): Settings {
    val model = getCurrentChatModel() ?: return this
    val updated = model.copy(generation = model.generation.copy(reasoningLevel = level))
    return copy(providers = providers.map { provider -> provider.editModel(updated) })
}
