package me.rerere.zeroagent.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

internal object ChatModelPreflight {
    fun unsupportedMedia(
        model: Model,
        parts: List<UIMessagePart>,
        hasOcrModel: Boolean,
    ): List<Modality> = parts.mapNotNull { part ->
        when (part) {
            is UIMessagePart.Image -> Modality.IMAGE.takeUnless { hasOcrModel && part.url.startsWith("file:") }
            is UIMessagePart.Audio -> Modality.AUDIO
            is UIMessagePart.Video -> Modality.VIDEO
            else -> null
        }
    }.distinct().filterNot(model.inputModalities::contains)

    fun fitsDraft(model: Model, parts: List<UIMessagePart>): Boolean? = model.contextLength
        ?.takeIf { it > 0 }
        ?.let { ContextBudgetPlanner.estimateContextTokens(listOf(UIMessage(role = MessageRole.USER, parts = parts))) < it }

    fun toolsAvailable(model: Model): Boolean = ModelAbility.TOOL in model.abilities
}