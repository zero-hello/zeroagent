package me.rerere.zeroagent.data.ai

import org.junit.Assert.*
import org.junit.Test

class AgentIdentityPromptTest {
    @Test fun `identity describes actual configured model and user style without forced introductions`() {
        val prompt = AgentIdentityPrompt.build("model-a", "My model", "Helper", "Use short replies.")
        assertTrue(prompt.contains("model-a")); assertTrue(prompt.contains("ZeroAgent"))
        assertTrue(prompt.contains("Use short replies.")); assertTrue(prompt.contains("not independent proof"))
        assertTrue(prompt.contains("Do not introduce yourself every turn"))
        assertTrue(prompt.contains("respect the existing authorization"))
    }
    @Test fun `changing a model changes the runtime identity`() {
        assertNotEquals(AgentIdentityPrompt.build("model-a", "", "", ""), AgentIdentityPrompt.build("model-b", "", "", ""))
    }
    @Test fun `phone guidance advertises search only when a real search tool is present`() {
        assertEquals("", PhoneAgentGuidance.build(setOf("search_web")))
        assertTrue(PhoneAgentGuidance.build(setOf("tap", "search_web")).contains("use the existing search_web"))
        assertTrue(PhoneAgentGuidance.build(setOf("tap")).contains("No web search tool is available"))
    }
}
