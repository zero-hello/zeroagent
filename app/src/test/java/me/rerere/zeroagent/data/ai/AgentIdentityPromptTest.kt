package me.rerere.zeroagent.data.ai

import me.rerere.ai.provider.*
import me.rerere.zeroagent.data.datastore.*
import me.rerere.zeroagent.data.model.Assistant
import org.junit.Assert.*
import org.junit.Test

/** The visible brand must never become an automatically injected model persona. */
class AgentIdentityPromptTest {
    @Test fun `model parameters override retired assistant persona and sampling`() {
        val model = Model(modelId = "fixture-model", displayName = "ZeroAgent", generation = ModelGenerationSettings(systemPrompt = "Use short replies", temperature = .2f, maxTokens = 500))
        val legacy = Assistant(name = "Old persona", systemPrompt = "Old instruction", temperature = .9f, agentIdentity = "Old identity")
        val settings = Settings(init = false, providers = listOf(ProviderSetting.OpenAI(models = listOf(model))), assistants = listOf(legacy), assistantId = legacy.id, chatModelId = model.id)
        val effective = settings.assistantForModel(model)
        assertEquals("ZeroAgent", effective.name)
        assertEquals("Use short replies", effective.systemPrompt)
        assertEquals(.2f, effective.temperature!!)
        assertEquals(500, effective.maxTokens)
        assertEquals("", effective.agentIdentity)
        assertFalse(effective.systemPrompt.contains("ZeroAgent"))
        assertFalse(effective.systemPrompt.contains("Old persona"))
    }
    @Test fun `migration retains memory scope IDs and keeps explicit user prompt`() {
        val first = Model(modelId = "first"); val second = Model(modelId = "second")
        val legacy = Assistant(name = "Legacy", systemPrompt = "Explicit user instruction")
        val settings = Settings(init = false, providers = listOf(ProviderSetting.OpenAI(models = listOf(first, second))), assistants = listOf(legacy), assistantId = legacy.id, chatModelId = first.id)
        val once = settings.withModelProfiles(); val twice = once.withModelProfiles()
        assertEquals(once, twice)
        val models = once.providers.single().models
        assertEquals(legacy.id.toString(), models[0].behaviorProfileId)
        assertEquals(legacy.id.toString(), models[1].behaviorProfileId)
        assertEquals(1, once.assistants.size)
        assertEquals("Explicit user instruction", models[0].generation.systemPrompt)
        assertEquals(first.id, once.getCurrentChatModel()?.id)
    }
}
