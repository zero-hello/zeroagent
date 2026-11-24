package me.rerere.zeroagent.data.sync

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.OpenAIProtocol
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.ai.mcp.McpCommonOptions
import me.rerere.zeroagent.data.ai.mcp.McpServerConfig
import me.rerere.zeroagent.data.ai.mcp.serverUrl
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.sync.PortableTemplate.Format
import me.rerere.zeroagent.data.sync.PortableTemplate.Kind

internal fun ProviderSetting.toPortableTemplateOrNull(): PortableTemplate? {
    val modelIds = models.map { it.modelId }.distinct()
    return when (this) {
        is ProviderSetting.OpenAI -> when {
            protocol == OpenAIProtocol.COHERE_V2 -> PortableTemplate(Kind.PROVIDER, name, Format.COHERE_V2, baseUrl, models = modelIds)
            useResponseApi -> PortableTemplate(Kind.PROVIDER, name, Format.OPENAI_RESPONSES, baseUrl, responsesPath, modelIds)
            else -> PortableTemplate(Kind.PROVIDER, name, Format.OPENAI_CHAT, baseUrl, chatCompletionsPath, modelIds)
        }
        is ProviderSetting.Google -> if (vertexAI) null else PortableTemplate(Kind.PROVIDER, name, Format.GOOGLE, baseUrl, models = modelIds)
        is ProviderSetting.Claude -> PortableTemplate(Kind.PROVIDER, name, Format.ANTHROPIC, baseUrl, models = modelIds)
        else -> null
    }
}

internal fun McpServerConfig.toPortableTemplate(): PortableTemplate {
    val format = when (this) {
        is McpServerConfig.SseTransportServer -> Format.MCP_SSE
        is McpServerConfig.StreamableHTTPServer -> Format.MCP_HTTP
    }
    return PortableTemplate(Kind.MCP, commonOptions.name, format, serverUrl)
}

internal fun Settings.encodeTemplate(template: PortableTemplate): String {
    val knownSecrets = buildList {
        for (provider in providers) {
            when (provider) {
                is ProviderSetting.OpenAI -> add(provider.apiKey)
                is ProviderSetting.Google -> { add(provider.apiKey); add(provider.privateKey) }
                is ProviderSetting.Claude -> add(provider.apiKey)
                else -> Unit
            }
            addAll(provider.customHeaders.map { it.value })
            addAll(provider.models.flatMap { model -> model.customHeaders.map { it.value } })
        }
        for (server in mcpServers) {
            addAll(server.commonOptions.headers.map { it.second })
            server.commonOptions.oauth?.let {
                addAll(listOfNotNull(it.clientSecret, it.accessToken, it.refreshToken))
            }
        }
        addAll(assistants.flatMap { assistant -> assistant.customHeaders.map { it.value } })
    }
    return PortableTemplateCodec.encode(template, knownSecrets)
}

internal fun Settings.importTemplate(template: PortableTemplate): Settings = with(template) {
    when (kind) {
        Kind.PROMPT -> copy(assistants = assistants + Assistant(
            name = name, systemPrompt = requireNotNull(prompt), localTools = emptyList(),
        ))
        Kind.MCP -> {
            val options = McpCommonOptions(enable = false, name = name)
            val url = requireNotNull(endpoint)
            val server = when (format) {
                Format.MCP_SSE -> McpServerConfig.SseTransportServer(commonOptions = options, url = url)
                Format.MCP_HTTP -> McpServerConfig.StreamableHTTPServer(commonOptions = options, url = url)
                else -> error("Invalid MCP format")
            }
            copy(mcpServers = mcpServers + server)
        }
        Kind.PROVIDER -> copy(providers = providers + toDisabledProvider())
    }
}

internal fun PortableTemplate.toDisabledProvider(): ProviderSetting {
    require(kind == Kind.PROVIDER)
    val url = requireNotNull(endpoint)
    val importedModels = models.distinct().map { Model(modelId = it, displayName = it) }
    return when (format) {
        Format.OPENAI_CHAT -> ProviderSetting.OpenAI(
            enabled = false, name = name, baseUrl = url, models = importedModels,
            chatCompletionsPath = requireNotNull(requestPath),
        )
        Format.OPENAI_RESPONSES -> ProviderSetting.OpenAI(
            enabled = false, name = name, baseUrl = url, models = importedModels,
            useResponseApi = true, responsesPath = requireNotNull(requestPath),
        )
        Format.COHERE_V2 -> ProviderSetting.OpenAI(
            enabled = false, name = name, baseUrl = url, models = importedModels, protocol = OpenAIProtocol.COHERE_V2,
        )
        Format.GOOGLE -> ProviderSetting.Google(enabled = false, name = name, baseUrl = url, models = importedModels)
        Format.ANTHROPIC -> ProviderSetting.Claude(enabled = false, name = name, baseUrl = url, models = importedModels)
        else -> error("Invalid provider format")
    }
}
