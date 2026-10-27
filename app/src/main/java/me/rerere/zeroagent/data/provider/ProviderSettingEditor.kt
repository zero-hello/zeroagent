package me.rerere.zeroagent.data.provider

import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.datastore.DEFAULT_PROVIDERS
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.reflect.KClass

fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    if (this::class == type) return this

    val apiKey = when (this) {
        is ProviderSetting.OpenAI -> this.apiKey
        is ProviderSetting.Google -> this.apiKey
        is ProviderSetting.Claude -> this.apiKey
        is ProviderSetting.AICore -> "" // on-device, no API key
        is ProviderSetting.LiteRtLocal -> "" // on-device, no API key
        is ProviderSetting.LlamaCppLocal -> "" // on-device, no API key
        is ProviderSetting.Codex -> "" // OAuth, no API key
        is ProviderSetting.Grok -> "" // OAuth, no API key
        is ProviderSetting.GeminiOAuth -> "" // OAuth, no API key
    }
    val sourceBaseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.AICore -> "" // on-device, no base URL
        is ProviderSetting.LiteRtLocal -> "" // on-device, no base URL
        is ProviderSetting.LlamaCppLocal -> "" // on-device, no base URL
        is ProviderSetting.Codex -> "" // OAuth, no base URL
        is ProviderSetting.Grok -> "" // OAuth, no base URL
        is ProviderSetting.GeminiOAuth -> "" // OAuth, no base URL
    }
    val targetDefaultBaseUrl = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().baseUrl
        ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
        ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
        ProviderSetting.AICore::class -> ""
        else -> error("Unsupported provider type: $type")
    }
    val convertedBaseUrl = sourceBaseUrl.convertToTargetBaseUrl(targetDefaultBaseUrl)

    return when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            balanceOption = this.balanceOption, customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )

        ProviderSetting.AICore::class -> ProviderSetting.AICore(
            id = this.id,
            enabled = this.enabled,
            name = this.name,
            models = this.models,
            balanceOption = this.balanceOption,
            builtIn = this.builtIn,
            description = this.description,
            shortDescription = this.shortDescription,
        )

        else -> error("Unsupported provider type: $type")
    }
}

internal fun ProviderSetting.defaultBaseUrlForReset(): String {
    val defaultProvider = DEFAULT_PROVIDERS.find { it.id == id }
    if (defaultProvider != null) {
        when (this) {
            is ProviderSetting.OpenAI -> if (defaultProvider is ProviderSetting.OpenAI) return defaultProvider.baseUrl
            is ProviderSetting.Google -> if (defaultProvider is ProviderSetting.Google) return defaultProvider.baseUrl
            is ProviderSetting.Claude -> if (defaultProvider is ProviderSetting.Claude) return defaultProvider.baseUrl
            is ProviderSetting.AICore -> return "" // on-device, no base URL
            is ProviderSetting.LiteRtLocal -> return "" // on-device, no base URL
            is ProviderSetting.LlamaCppLocal -> return "" // on-device, no base URL
            is ProviderSetting.Codex -> return "" // OAuth, no base URL
            is ProviderSetting.Grok -> return "" // OAuth, no base URL
            is ProviderSetting.GeminiOAuth -> return "" // OAuth, no base URL
        }
    }
    return when (this) {
        is ProviderSetting.OpenAI -> ProviderSetting.OpenAI().baseUrl
        is ProviderSetting.Google -> ProviderSetting.Google().baseUrl
        is ProviderSetting.Claude -> ProviderSetting.Claude().baseUrl
        is ProviderSetting.AICore -> ""
        is ProviderSetting.LiteRtLocal -> ""
        is ProviderSetting.LlamaCppLocal -> ""
        is ProviderSetting.Codex -> ""
        is ProviderSetting.Grok -> ""
        is ProviderSetting.GeminiOAuth -> ""
    }
}

internal fun ProviderSetting.resetBaseUrlToDefault(): ProviderSetting {
    val defaultBaseUrl = defaultBaseUrlForReset()
    return when (this) {
        is ProviderSetting.OpenAI -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.Google -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.Claude -> this.copy(baseUrl = defaultBaseUrl)
        is ProviderSetting.AICore -> this // no base URL to reset
        is ProviderSetting.LiteRtLocal -> this // no base URL to reset
        is ProviderSetting.LlamaCppLocal -> this // no base URL to reset
        is ProviderSetting.Codex -> this // no base URL to reset
        is ProviderSetting.Grok -> this // no base URL to reset
        is ProviderSetting.GeminiOAuth -> this // no base URL to reset
    }
}

internal fun ProviderSetting.isUsingDefaultBaseUrl(): Boolean {
    val baseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.AICore -> return true // no base URL concept
        is ProviderSetting.LiteRtLocal -> return true // no base URL concept
        is ProviderSetting.LlamaCppLocal -> return true // no base URL concept
        is ProviderSetting.Codex -> return true // no base URL concept
        is ProviderSetting.Grok -> return true // no base URL concept
        is ProviderSetting.GeminiOAuth -> return true // no base URL concept
    }
    return baseUrl == defaultBaseUrlForReset()
}

private fun String.convertToTargetBaseUrl(targetDefaultBaseUrl: String): String {
    val sourceUrl = this.toHttpUrlOrNull() ?: return this
    val sourceHost = sourceUrl.host.lowercase()
    if (sourceHost in OFFICIAL_PROVIDER_HOSTS) return targetDefaultBaseUrl
    val targetUrl = targetDefaultBaseUrl.toHttpUrlOrNull() ?: return this
    val convertedPath = sourceUrl.encodedPath.convertToTargetPath(targetUrl.encodedPath)
    return sourceUrl.newBuilder().encodedPath(convertedPath).build().toString()
}

private fun String.convertToTargetPath(targetPath: String): String {
    val source = this.normalizePath()
    val target = targetPath.normalizePath()
    val replaced = when {
        source.lowercase().endsWith(V1_BETA_SUFFIX) -> source.dropLast(V1_BETA_SUFFIX.length) + target
        source.lowercase().endsWith(V1_SUFFIX) -> source.dropLast(V1_SUFFIX.length) + target
        source.isBlank() -> target
        else -> source + target
    }
    return replaced.normalizePath()
}

private fun String.normalizePath(): String {
    val value = this.trim()
    if (value.isEmpty() || value == "/") return ""
    val path = if (value.startsWith("/")) value else "/$value"
    return path.trimEnd('/')
}

internal fun String.isValidProviderBaseUrl(): Boolean = this.toHttpUrlOrNull() != null

private const val OPENAI_OFFICIAL_HOST = "api.openai.com"
private const val GOOGLE_OFFICIAL_HOST = "generativelanguage.googleapis.com"
private const val CLAUDE_OFFICIAL_HOST = "api.anthropic.com"
private const val V1_SUFFIX = "/v1"
private const val V1_BETA_SUFFIX = "/v1beta"
private val OFFICIAL_PROVIDER_HOSTS = setOf(
    OPENAI_OFFICIAL_HOST,
    GOOGLE_OFFICIAL_HOST,
    CLAUDE_OFFICIAL_HOST
)

internal fun googleBaseUrlHasError(value: String): Boolean =
    value.isNotBlank() && (!value.isValidProviderBaseUrl() || !value.endsWith("/v1beta"))
