package me.rerere.ai.core

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.providers.openai.openRouterModelFromJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ReasoningCapabilitiesTest {
    @Test fun `model-list effort metadata controls the choices and wire value`() {
        val model = openRouterModelFromJson(Json.parseToJsonElement("""{"id":"custom-model","supported_parameters":["reasoning"],"supported_reasoning_efforts":["none","minimal","high"]}""").jsonObject)!!
        val options = ReasoningCapabilities.forModel(model)
        assertTrue(options.detected)
        assertEquals(listOf(ReasoningLevel.OFF, ReasoningLevel.AUTO, ReasoningLevel.MINIMAL, ReasoningLevel.HIGH), options.levels)
        assertEquals(ReasoningLevel.AUTO, ReasoningCapabilities.effective(model, null, ReasoningLevel.MAX))
        assertEquals("minimal", ReasoningCapabilities.effective(model, null, ReasoningLevel.MINIMAL).effort)
    }
    @Test fun `local thinking is a binary setting rather than invented cloud budgets`() {
        val model = Model(modelId = "Qwen3.gguf", abilities = listOf(ModelAbility.REASONING))
        assertEquals(listOf(ReasoningLevel.OFF, ReasoningLevel.AUTO), ReasoningCapabilities.forModel(model).levels)
        assertEquals(ReasoningLevel.OFF, ReasoningCapabilities.effective(model, null, ReasoningLevel.OFF))
    }
    @Test fun `mandatory reasoning and unknown cloud capabilities are not falsely advertised`() {
        assertEquals(listOf(ReasoningLevel.AUTO), ReasoningCapabilities.forModel(Model(modelId = "DeepSeek-R1-Distill.gguf")).levels)
        val unknown = ReasoningCapabilities.forModel(Model(modelId = "private-cloud", abilities = listOf(ModelAbility.REASONING)))
        assertFalse(unknown.detected)
        assertEquals(listOf(ReasoningLevel.AUTO), unknown.levels)
    }

    @Test fun `missing metadata is not proof that a custom model cannot reason`() {
        val unknown = ReasoningCapabilities.forModel(Model(modelId = "private-model"))
        assertFalse(unknown.detected)
        val explicit = ReasoningCapabilities.forModel(Model(modelId = "plain-model", supportedParameters = listOf("tools")))
        assertTrue(explicit.detected)
        assertEquals(listOf(ReasoningLevel.OFF), explicit.levels)
    }

    @Test fun `real validation feedback refines only the affected provider and model`() {
        val provider = me.rerere.ai.provider.ProviderSetting.OpenAI()
        val model = Model(modelId = "private-thinking-model", abilities = listOf(ModelAbility.REASONING))
        val error = me.rerere.ai.util.HttpException("Unsupported reasoning_effort. Supported values are: 'low', 'medium', 'high'.", 400)
        assertTrue(ReasoningCapabilities.learnFromError(model, provider, error))
        assertEquals(listOf(ReasoningLevel.AUTO, ReasoningLevel.LOW, ReasoningLevel.MEDIUM, ReasoningLevel.HIGH), ReasoningCapabilities.forModel(model, provider).levels)
        assertFalse(ReasoningCapabilities.forModel(model, me.rerere.ai.provider.ProviderSetting.OpenAI()).detected)
        assertFalse(ReasoningCapabilities.learnFromError(model, provider, me.rerere.ai.util.HttpException("unsupported temperature. Supported values are: 'low', 'high'.", 400)))
        assertFalse(ReasoningCapabilities.learnFromError(model, provider, me.rerere.ai.util.HttpException(error.message!!, 429)))
    }
}
