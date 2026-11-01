package me.rerere.zeroagent.data.datastore

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.ReasoningLevel
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesStoreTest {
    @Test
    fun `compression target defaults to one percent of the active context`() {
        val settings = Settings()

        assertEquals(3_720, settings.getContextCompactionTargetTokens(372_000))
    }

    @Test
    fun `compression target uses a safe fallback without model metadata`() {
        val settings = Settings(contextCompactionTargetTokensK = null)

        assertEquals(2_000, settings.getContextCompactionTargetTokens(null))
        assertEquals(2_000, settings.getContextCompactionTargetTokens(0))
    }

    @Test
    fun `token threshold supplies context metadata for the one percent default`() {
        val settings = Settings(
            contextCompactionTargetTokensK = null,
            autoCompactionThresholdMode = AutoCompactionThresholdMode.TOKENS,
            autoCompactionThresholdTokensK = 372,
        )

        assertEquals(372_000, settings.getCompactionContextLength(null))
        assertEquals(3_720, settings.getContextCompactionTargetTokens(
            settings.getCompactionContextLength(null),
        ))
    }

    @Test
    fun `explicit compression target overrides the fixed default`() {
        val settings = Settings(contextCompactionTargetTokensK = 30)

        assertEquals(30_000, settings.getContextCompactionTargetTokens(372_000))
    }

    /**
     * Upstream ZeroAgent added `MAX` (@SerialName "max") to `ReasoningLevel` on 2026-08-14. An
     * assistant JSON exported by upstream (or by this app after the level was added) carries
     * that literal string, so it MUST decode back to [ReasoningLevel.MAX] instead of failing
     * with an unknown-enum-constant error.
     */
    @Test
    fun `assistant decodes a reasoningLevel of max, as exported by upstream ZeroAgent`() {
        val fullJson = JsonInstant.encodeToString(Assistant())
        val withMaxReasoning = JsonObject(
            JsonInstant.parseToJsonElement(fullJson).jsonObject.toMutableMap().apply {
                put("reasoningLevel", JsonPrimitive("max"))
            }
        )

        val decoded = JsonInstant.decodeFromString<Assistant>(withMaxReasoning.toString())

        assertEquals(ReasoningLevel.MAX, decoded.reasoningLevel)
    }
    @Test
    fun `removed visual effects do not discard unrelated saved preferences`() {
        val decoded = JsonInstant.decodeFromString<DisplaySetting>("""{
            "liquidGlassEnabled":true,"liquidGlassOpacity":0.9,"enableBlurEffect":true,
            "dynamicGradientBackground":true,"sendOnEnter":true,"userNickname":"Zero"
        }""")
        assertTrue(decoded.sendOnEnter)
        assertEquals(true, decoded.dynamicGradientBackground)
        assertEquals("Zero", decoded.userNickname)
        val saved = JsonInstant.encodeToString(decoded)
        assertTrue(!saved.contains("liquidGlass") && !saved.contains("enableBlurEffect"))
    }

}
