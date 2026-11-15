package me.rerere.zeroagent.service

import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelGenerationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class BackgroundGenerationParamsTest {
    @Test fun backgroundTasksUseTheSelectedModelsGenerationSettings() {
        val model = Model(generation = ModelGenerationSettings(
            temperature = .47f, topP = .76f, topK = 13, maxTokens = 5123, reasoningLevel = ReasoningLevel.HIGH))
        val params = backgroundTextGenerationParams(model, Uuid.random())
        assertEquals(.47f, params.temperature)
        assertEquals(.76f, params.topP)
        assertEquals(13, params.topK)
        assertEquals(5123, params.maxTokens)
        assertEquals(ReasoningLevel.HIGH, params.reasoningLevel)
    }

    @Test fun explicitTaskReasoningOverridesModelReasoningWithoutDroppingSamplingSettings() {
        val model = Model(generation = ModelGenerationSettings(
            temperature = .3f, topP = .8f, topK = 9, maxTokens = 2048, reasoningLevel = ReasoningLevel.HIGH))
        val params = backgroundTextGenerationParams(model, Uuid.random(), ReasoningLevel.OFF)
        assertEquals(ReasoningLevel.OFF, params.reasoningLevel)
        assertEquals(.3f, params.temperature)
        assertEquals(.8f, params.topP)
        assertEquals(9, params.topK)
        assertEquals(2048, params.maxTokens)
    }

    @Test fun unsetSamplingValuesStayUnsetForProviderDefaults() {
        val params = backgroundTextGenerationParams(Model(), Uuid.random())
        assertNull(params.temperature)
        assertNull(params.topP)
        assertNull(params.topK)
        assertNull(params.maxTokens)
    }
}
