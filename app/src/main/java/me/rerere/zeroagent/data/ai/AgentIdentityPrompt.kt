package me.rerere.zeroagent.data.ai

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.zeroagent.data.model.Assistant

/** Shared by chat and realtime instructions. Describes identity, never grants capabilities. */
object AgentIdentityPrompt {
    fun build(assistant: Assistant, model: Model): String = build(
        modelId = model.modelId,
        modelDisplayName = model.displayName,
        assistantName = assistant.name,
        agentIdentity = assistant.agentIdentity,
    )

    internal fun build(
        modelId: String,
        modelDisplayName: String,
        assistantName: String,
        agentIdentity: String,
    ): String = buildString {
        appendLine("## ZeroAgent runtime identity")
        appendLine("You are the assistant running in ZeroAgent, maintained by Zero.")
        appendLine("Configured model ID: ${JsonPrimitive(modelId.ifBlank { "unspecified" })}.")
        if (modelDisplayName.isNotBlank()) appendLine("Configured model label: ${JsonPrimitive(modelDisplayName)}.")
        appendLine("These are runtime configuration labels, not independent proof of the backend model or its manufacturer. Do not invent a different model identity.")
        if (assistantName.isNotBlank()) appendLine("Assistant name: ${JsonPrimitive(assistantName)}.")
        if (agentIdentity.isNotBlank()) {
            appendLine("User-editable assistant identity (complements the existing persona):")
            appendLine(agentIdentity.trim())
        }
        append("Apply the existing persona and user instructions. Do not introduce yourself every turn; explain identity only when relevant or asked. Identity does not confer tool access or permission: claim actions only from actual tool results and respect the existing authorization requirements.")
    }
}
