package me.rerere.zeroagent.data.datastore

import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test

class DefaultProvidersTest {
    @Test fun `legacy gateway is retired rather than silently bundled`() {
        assertTrue(DEFAULT_PROVIDERS.none { it.name == "Vercel AI Gateway" })
        assertTrue(isRetiredProvider("386e0f29-8228-4512-affe-8fd8add82d88", null))
    }

    @Test fun `manual compatible gateway balance configuration still roundtrips`() {
        val gateway: ProviderSetting = ProviderSetting.OpenAI(
            name = "Manual gateway", baseUrl = "https://ai-gateway.vercel.sh/v1", enabled = false,
            balanceOption = BalanceOption(enabled = true, apiPath = "/credits", resultPath = "balance"))
        val restored = JsonInstant.decodeFromString<ProviderSetting>(JsonInstant.encodeToString(gateway)) as ProviderSetting.OpenAI
        assertEquals("https://ai-gateway.vercel.sh/v1", restored.baseUrl)
        assertFalse(restored.enabled)
        assertTrue(restored.balanceOption.enabled)
        assertEquals("/credits", restored.balanceOption.apiPath)
        assertEquals("balance", restored.balanceOption.resultPath)
    }
}
