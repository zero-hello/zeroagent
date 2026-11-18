package me.rerere.zeroagent.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.datastore.migration.PreferenceStoreV6Migration
import org.junit.Assert.*
import org.junit.Test

class ZeroAgentProviderPolicyTest {
    @Test fun `catalog includes configured providers and has no automatic billing path`() {
        assertEquals(setOf("DeepSeek", "Cohere", "OpenAI", "Anthropic", "Google Gemini", "xAI", "OpenRouter", "Groq", "Mistral"), RECOMMENDED_PROVIDERS.map { it.name }.toSet())
        assertTrue(DEFAULT_PROVIDERS.none { it.enabled || it.name == "ZeroAgent" })
        assertTrue(DEFAULT_PROVIDERS.none { isRetiredProvider(it.id.toString(), (it as? ProviderSetting.OpenAI)?.baseUrl) })
    }

    @Test fun `retirement uses domain boundaries and handles malformed addresses`() {
        assertFalse(isRetiredProvider(null, "https://api.deepseek.com/v1"))
        assertTrue(isRetiredProvider(null, "https://api.minimax.io/anthropic/v1"))
        assertFalse(isRetiredProvider(null, "https://not-deepseek.com/v1"))
        assertFalse(isRetiredProvider(null, "https://api.openai.com/v1"))
        assertFalse(isRetiredProvider(null, "not a URL"))
    }

    @Test fun `migration archives retired credentials and preserves other payloads`() = runBlocking {
        val original = """[{"id":"deepseek","baseUrl":"https://api.deepseek.com/v1","apiKey":"deepseek-fixture"},{"id":"old","baseUrl":"https://api.minimax.io/v1","apiKey":"retired-fixture"},{"id":"keep","baseUrl":"https://api.openai.com/v1","extra":{"future":true}}]"""
        val prefs = mutablePreferencesOf(SettingsStore.VERSION to 5, SettingsStore.PROVIDERS to original)
        val migration = PreferenceStoreV6Migration()
        val result = migration.migrate(prefs)
        val retained = Json.parseToJsonElement(result[SettingsStore.PROVIDERS]!!) as JsonArray
        val archived = result[PreferenceStoreV6Migration.RETIRED_PROVIDERS_ARCHIVE]!!
        assertEquals(2, retained.size)
        assertTrue(retained.toString().contains("deepseek"))
        assertTrue(retained.toString().contains("future"))
        assertTrue(archived.contains("retired-fixture"))
        assertFalse(archived.contains("deepseek-fixture"))
        assertEquals(6, result[SettingsStore.VERSION])
        assertFalse(migration.shouldMigrate(result))
    }

    @Test fun `speech migration removes China services and archives them`() = runBlocking {
        val prefs = mutablePreferencesOf(SettingsStore.VERSION to 5,
            SettingsStore.TTS_PROVIDERS to """[{"type":"qwen","apiKey":"fixture"},{"type":"system"}]""",
            me.rerere.zeroagent.data.datastore.migration.PreferenceStoreV6Migration.LEGACY_DICTATION_PROVIDERS to """[{"type":"dashscope"},{"type":"openai_realtime"}]""")
        val result = PreferenceStoreV6Migration().migrate(prefs)
        assertFalse(result[SettingsStore.TTS_PROVIDERS]!!.contains("qwen"))
        assertTrue(result[SettingsStore.TTS_PROVIDERS]!!.contains("system"))
        assertTrue(result[PreferenceStoreV6Migration.RETIRED_TTS_ARCHIVE]!!.contains("fixture"))
        assertFalse(result[me.rerere.zeroagent.data.datastore.migration.PreferenceStoreV6Migration.LEGACY_DICTATION_PROVIDERS]!!.contains("dashscope"))
        assertTrue(result[me.rerere.zeroagent.data.datastore.migration.PreferenceStoreV6Migration.LEGACY_DICTATION_PROVIDERS]!!.contains("openai_realtime"))
    }

    @Test fun `search migration archives self-hosted upstream and China services`() = runBlocking {
        val prefs = mutablePreferencesOf(SettingsStore.VERSION to 5,
            SettingsStore.SEARCH_SERVICES to """[{"type":"rikkahub"},{"type":"zhipu","apiKey":"fixture"},{"type":"tavily"}]""")
        val result = PreferenceStoreV6Migration().migrate(prefs)
        assertEquals("[{\"type\":\"tavily\"}]", result[SettingsStore.SEARCH_SERVICES])
        assertTrue(result[PreferenceStoreV6Migration.RETIRED_SEARCH_ARCHIVE]!!.contains("fixture"))
        assertFalse(me.rerere.search.SearchServiceOptions.TYPES.values.contains("ZeroAgent"))
    }

    @Test fun `migration keeps malformed user data recoverable`() = runBlocking {
        val prefs = mutablePreferencesOf(SettingsStore.PROVIDERS to "malformed JSON")
        assertEquals("malformed JSON", PreferenceStoreV6Migration().migrate(prefs)[SettingsStore.PROVIDERS])
    }

    @Test fun `category reordering cannot erase hidden providers`() {
        val first = ProviderSetting.OpenAI(name = "first")
        val hidden = ProviderSetting.AICore()
        val second = ProviderSetting.OpenAI(name = "second")
        val result = reorderVisibleProviders(listOf(first, hidden, second), listOf(first, second), 0, 1)
        assertEquals(listOf(second.id, hidden.id, first.id), result.map { it.id })
        assertEquals(3, result.size)
    }
}
