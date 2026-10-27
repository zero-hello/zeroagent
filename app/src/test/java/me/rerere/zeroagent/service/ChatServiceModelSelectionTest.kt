package me.rerere.zeroagent.service

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.ai.tools.LocalToolOption
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.assistantForModel
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.Conversation
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ChatServiceModelSelectionTest {
    @Test fun ordinaryChatsFollowTheGlobalModelInsteadOfTheLegacyAssistantModel() {
        val legacyModel = Model(modelId = "legacy")
        val selected = Model(modelId = "selected")
        val legacyAssistant = Assistant(chatModelId = legacyModel.id)
        val settings = Settings(chatModelId = selected.id, assistantId = legacyAssistant.id,
            assistants = listOf(legacyAssistant), providers = listOf(ProviderSetting.OpenAI(models = listOf(legacyModel, selected))))
        val conversation = Conversation(assistantId = legacyAssistant.id, messageNodes = emptyList())
        assertEquals(selected, resolveConversationChatModel(settings, conversation))
        assertEquals(legacyModel, resolveConversationChatModel(settings.copy(chatModelId = legacyModel.id), conversation))
    }

    @Test fun explicitConversationOverrideIsPreservedForSubAgents() {
        val global = Model(modelId = "global")
        val child = Model(modelId = "child")
        val settings = Settings(chatModelId = global.id,
            providers = listOf(ProviderSetting.OpenAI(models = listOf(global, child))))
        val conversation = Conversation(assistantId = Uuid.random(), chatModelId = child.id, messageNodes = emptyList())
        assertEquals(child, resolveConversationChatModel(settings, conversation))
    }

    @Test fun staleExplicitOverrideDoesNotSilentlyUseAnotherProvider() {
        val global = Model(modelId = "global")
        val settings = Settings(chatModelId = global.id, providers = listOf(ProviderSetting.OpenAI(models = listOf(global))))
        val conversation = Conversation(assistantId = Uuid.random(), chatModelId = Uuid.random(), messageNodes = emptyList())
        assertNull(resolveConversationChatModel(settings, conversation))
    }

    @Test fun selectedModelSuppliesItsToolAndMemoryProfileWithoutRewritingConversationIds() {
        val legacy = Assistant(localTools = listOf(LocalToolOption.TimeInfo))
        val workspace = Uuid.random()
        val profile = Assistant(localTools = listOf(LocalToolOption.Files), workspaceId = workspace,
            coldMemoryEnabled = true, coldMemoryDir = "/workspace/memory")
        val selected = Model(modelId = "selected", behaviorProfileId = profile.id.toString())
        val settings = Settings(chatModelId = selected.id, assistantId = legacy.id, assistants = listOf(legacy, profile),
            providers = listOf(ProviderSetting.OpenAI(models = listOf(selected))))
        val conversation = Conversation(assistantId = legacy.id, messageNodes = emptyList())
        val model = resolveConversationChatModel(settings, conversation)!!
        val projected = settings.assistantForModel(model)
        assertEquals(listOf(LocalToolOption.Files), projected.localTools)
        assertEquals(workspace, projected.workspaceId)
        assertTrue(projected.coldMemoryEnabled)
        assertEquals("/workspace/memory", projected.coldMemoryDir)
        assertEquals("ZeroAgent", projected.name)
        assertEquals(legacy.id, conversation.assistantId)
        assertNull(conversation.chatModelId)
    }
}
