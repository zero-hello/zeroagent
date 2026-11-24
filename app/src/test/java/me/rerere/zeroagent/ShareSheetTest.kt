package me.rerere.zeroagent

import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.ui.components.ui.decodeProviderSetting
import me.rerere.zeroagent.ui.components.ui.encodeForShare
import me.rerere.zeroagent.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64

class ShareSheetTest {
    @Test
    fun `new provider template retains protocol models and endpoint`() {
        val original = ProviderSetting.OpenAI(
            enabled = true,
            name = "Test OpenAI",
            models = listOf(Model(modelId = "test-model")),
            apiKey = "fixture-only-key",
            baseUrl = "https://example.test/v1",
            useResponseApi = true,
            responsesPath = "/response-stream",
        )

        val encoded = original.encodeForShare()
        val decoded = decodeProviderSetting(encoded) as ProviderSetting.OpenAI
        val raw = java.util.Base64.getUrlDecoder().decode(encoded.removePrefix("ZA1:")).decodeToString()

        assertNotEquals(original.id, decoded.id)
        assertFalse(decoded.enabled)
        assertEquals("", decoded.apiKey)
        assertFalse(raw.contains(original.apiKey))
        assertEquals(original.name, decoded.name)
        assertEquals(original.baseUrl, decoded.baseUrl)
        assertEquals(original.responsesPath, decoded.responsesPath)
        assertTrue(decoded.useResponseApi)
        assertEquals(listOf("test-model"), decoded.models.map { it.modelId })
    }

    @Test
    fun `Google and Claude templates preserve public configuration`() {
        val google = ProviderSetting.Google(name = "Test Google", apiKey = "fixture-google-key", baseUrl = "https://google.example.test/v1")
        val claude = ProviderSetting.Claude(name = "Test Claude", apiKey = "fixture-claude-key", baseUrl = "https://claude.example.test/v1")
        val restoredGoogle = decodeProviderSetting(google.encodeForShare()) as ProviderSetting.Google
        val restoredClaude = decodeProviderSetting(claude.encodeForShare()) as ProviderSetting.Claude
        assertEquals(google.baseUrl, restoredGoogle.baseUrl)
        assertEquals(claude.baseUrl, restoredClaude.baseUrl)
        assertEquals("", restoredGoogle.apiKey)
        assertEquals("", restoredClaude.apiKey)
        assertFalse(restoredGoogle.enabled)
        assertFalse(restoredClaude.enabled)
    }

    @Test
    fun `legacy share only imports public configuration`() {
        val original: ProviderSetting = ProviderSetting.OpenAI(
            enabled = true,
            name = "Legacy test",
            apiKey = "fixture-legacy-key",
            baseUrl = "https://legacy.example.test/v1",
            balanceOption = BalanceOption(enabled = true, apiPath = "/credits", resultPath = "value"),
        )
        val raw = JsonInstant.encodeToString(ProviderSetting.serializer(), original).encodeToByteArray()
        val restored = decodeProviderSetting("ai-provider:v1:${Base64.encode(raw)}") as ProviderSetting.OpenAI
        assertEquals(original.name, restored.name)
        assertEquals("https://legacy.example.test/v1", restored.baseUrl)
        assertEquals("", restored.apiKey)
        assertFalse(restored.enabled)
        assertFalse(restored.balanceOption.enabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported provider type cannot be shared`() {
        ProviderSetting.Google(name = "Test Vertex", vertexAI = true).encodeForShare()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `provider cannot share an endpoint that contains its key`() {
        ProviderSetting.OpenAI(
            name = "Test embedded key",
            apiKey = "fixture-embedded-key",
            baseUrl = "https://example.test/fixture-embedded-key/v1",
        ).encodeForShare()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects an invalid prefix`() {
        decodeProviderSetting("invalid-string")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects a different legacy version`() {
        decodeProviderSetting("ai-provider:v2:somedata")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects invalid legacy base64`() {
        decodeProviderSetting("ai-provider:v1:not-valid-base64!!!")
    }
}
