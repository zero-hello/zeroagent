package me.rerere.zeroagent.data.datastore

import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

val DEFAULT_AUTO_MODEL_ID = Uuid.parse("b7055fb4-39f9-4042-a88a-0d80ed76cf08")
val DEFAULT_CODEX_PROVIDER_ID = Uuid.parse("7ce7e322-b995-4b0c-9d48-42e08dcfcdda")
val DEFAULT_GROK_PROVIDER_ID = Uuid.parse("8f3e1d20-4b6a-4c9e-a1f2-9d5c7e0b3a44")
val DEFAULT_GEMINI_OAUTH_PROVIDER_ID = Uuid.parse("2b6c1f84-73ad-4e35-b0c7-1a9e4d5f8c21")

// Cloud APIs first; optional local engines and account integrations are separate groups.
// No provider is enabled until the user chooses it and supplies credentials.
val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(id = Uuid.parse("a28aacda-a9b8-4db7-874a-cbfa4bbce661"), name = "DeepSeek", baseUrl = "https://api.deepseek.com/v1", enabled = false, builtIn = true),
    ProviderSetting.OpenAI(id = Uuid.parse("c51cf9c6-59d6-4a2e-a1a8-47292a8d201d"), name = "Cohere", baseUrl = "https://api.cohere.com", protocol = me.rerere.ai.provider.OpenAIProtocol.COHERE_V2, enabled = false, builtIn = true),
    ProviderSetting.OpenAI(id = Uuid.parse("1eeea727-9ee5-4cae-93e6-6fb01a4d051e"), name = "OpenAI", baseUrl = "https://api.openai.com/v1", apiKey = "", enabled = false, builtIn = true),
    ProviderSetting.Claude(id = Uuid.parse("485f59be-79f9-53b5-9e6f-6e0d843051da"), name = "Anthropic", baseUrl = "https://api.anthropic.com/v1", apiKey = "", enabled = false, builtIn = true),
    ProviderSetting.Google(id = Uuid.parse("6ab18148-c138-4394-a46f-1cd8c8ceaa6d"), name = "Google Gemini", baseUrl = "https://generativelanguage.googleapis.com/v1beta", apiKey = "", enabled = false, builtIn = true),
    ProviderSetting.OpenAI(id = Uuid.parse("ff3cde7e-0f65-43d7-8fb2-6475c99f5990"), name = "xAI", baseUrl = "https://api.x.ai/v1", apiKey = "", enabled = false, builtIn = true),
    ProviderSetting.OpenAI(id = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"), name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1", apiKey = "", enabled = false, builtIn = true),
    ProviderSetting.OpenAI(id = Uuid.parse("a37fb2bf-78c9-5869-be59-0ffdee351e19"), name = "Groq", baseUrl = "https://api.groq.com/openai/v1", apiKey = "", enabled = false, builtIn = true),
    ProviderSetting.OpenAI(id = Uuid.parse("b2acb0b9-a9f0-5e86-8e0e-eb936365d3b2"), name = "Mistral", baseUrl = "https://api.mistral.ai/v1", apiKey = "", enabled = false, builtIn = true),
    ProviderSetting.AICore(enabled = false, builtIn = true),
    ProviderSetting.LiteRtLocal(enabled = false, builtIn = true),
    ProviderSetting.LlamaCppLocal(enabled = false, builtIn = true),
    ProviderSetting.Codex(id = DEFAULT_CODEX_PROVIDER_ID, name = "OpenAI Codex", enabled = false, builtIn = true),
    ProviderSetting.Grok(id = DEFAULT_GROK_PROVIDER_ID, name = "xAI Grok", enabled = false, builtIn = true),
    ProviderSetting.GeminiOAuth(id = DEFAULT_GEMINI_OAUTH_PROVIDER_ID, name = "Google Gemini OAuth", enabled = false, builtIn = true),
)

fun ProviderSetting.isLocalEngine(): Boolean =
    this is ProviderSetting.AICore || this is ProviderSetting.LiteRtLocal || this is ProviderSetting.LlamaCppLocal
