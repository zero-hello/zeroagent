package me.rerere.zeroagent.ui.pages.chat

import kotlinx.serialization.json.Json
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelGenerationSettings
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.ai.tools.LocalToolOption
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

class ChatModelSettingsTest {
    @Test
    fun reasoningSelectionPersistsOnTheSelectedModelWithoutChangingToolOrMemoryProfiles() {
        val profile = Assistant(
            reasoningLevel = ReasoningLevel.HIGH,
            localTools = listOf(LocalToolOption.Files),
            disabledLocalTools = setOf("file_write"),
            workspaceId = Uuid.random(),
            coldMemoryEnabled = true,
            coldMemoryDir = "/workspace/memory",
        )
        val generation = ModelGenerationSettings(
            systemPrompt = "Keep the user instruction",
            temperature = .3f,
            topP = .8f,
            maxTokens = 4096,
            reasoningLevel = ReasoningLevel.LOW,
        )
        val selected = Model(modelId = "same-api-id", behaviorProfileId = profile.id.toString(), generation = generation)
        // API model names may repeat across providers; the selected UUID is authoritative.
        val other = Model(modelId = selected.modelId, generation = generation.copy(reasoningLevel = ReasoningLevel.MAX))
        val settings = Settings(
            chatModelId = selected.id,
            assistantId = profile.id,
            assistants = listOf(profile),
            providers = listOf(
                ProviderSetting.OpenAI(models = listOf(selected)),
                ProviderSetting.OpenAI(models = listOf(other)),
            ),
        )

        val updated = settings.withCurrentChatModelReasoning(ReasoningLevel.MEDIUM)
        val restored = Json.decodeFromString(Settings.serializer(), Json.encodeToString(Settings.serializer(), updated))

        assertEquals(generation.copy(reasoningLevel = ReasoningLevel.MEDIUM), restored.getCurrentChatModel()!!.generation)
        assertEquals(ReasoningLevel.MEDIUM, restored.getCurrentAssistant().reasoningLevel)
        assertEquals(selected.behaviorProfileId, restored.getCurrentChatModel()!!.behaviorProfileId)
        assertEquals(settings.providers[1], restored.providers[1])
        assertEquals(settings.assistants, restored.assistants)
        assertEquals(profile.workspaceId, restored.getCurrentAssistant().workspaceId)
        assertEquals(profile.disabledLocalTools, restored.getCurrentAssistant().disabledLocalTools)
        assertEquals(profile.coldMemoryDir, restored.getCurrentAssistant().coldMemoryDir)
        assertEquals(profile.id, restored.assistantId)
        assertEquals(selected.id, restored.chatModelId)
    }

    @Test
    fun noSelectedModelDoesNotRewriteLegacyProfilesOrInventAModel() {
        val settings = Settings(providers = emptyList(), assistants = listOf(Assistant(reasoningLevel = ReasoningLevel.HIGH)))
        assertSame(settings, settings.withCurrentChatModelReasoning(ReasoningLevel.LOW))
    }
}
