package me.rerere.zeroagent.data.datastore

// Recommendations share the same catalog and never silently enable network requests.
val RECOMMENDED_PROVIDERS = DEFAULT_PROVIDERS.take(7)
