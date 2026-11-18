package me.rerere.zeroagent.data.datastore

// Recommendations share the same catalog and never silently enable network requests.
val RECOMMENDED_PROVIDERS = DEFAULT_PROVIDERS.filter { it is me.rerere.ai.provider.ProviderSetting.OpenAI || it is me.rerere.ai.provider.ProviderSetting.Claude || it is me.rerere.ai.provider.ProviderSetting.Google }
