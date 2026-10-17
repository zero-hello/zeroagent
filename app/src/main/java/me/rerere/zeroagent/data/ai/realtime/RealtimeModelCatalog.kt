package me.rerere.zeroagent.data.ai.realtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.ai.util.HttpException
import me.rerere.common.http.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

internal data class RealtimeModelOption(val id: String, val name: String)
internal object RealtimeModelCatalog {
    fun parse(root: JsonObject, protocol: RealtimeVoiceProtocol): List<RealtimeModelOption> {
        val data = root[if (protocol == RealtimeVoiceProtocol.GEMINI) "models" else "data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { value ->
            val m = value as? JsonObject ?: return@mapNotNull null
            val id = m.s(if (protocol == RealtimeVoiceProtocol.GEMINI) "name" else "id").removePrefix("models/")
            val methods = m.a("supportedGenerationMethods").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val native = if (protocol == RealtimeVoiceProtocol.GEMINI)
                methods.any { it.equals("bidiGenerateContent", true) } ||
                    (methods.isEmpty() && (id.contains("native-audio") || id.contains("-live")))
                else id.contains("realtime", true) && !id.contains("transcrib", true)
            if (id.isBlank() || !native) null else RealtimeModelOption(id, m.s("displayName").ifBlank { id })
        }.distinctBy { it.id }.sortedBy { it.id }
    }
    suspend fun load(client: OkHttpClient, provider: ProviderSetting, requested: RealtimeVoiceProtocol): List<RealtimeModelOption> = withContext(Dispatchers.IO) {
        val protocol = RealtimeConnections.protocol(provider, requested)
        val (base, key) = when (provider) {
            is ProviderSetting.OpenAI -> provider.baseUrl to provider.apiKey
            is ProviderSetting.Google -> { require(!provider.vertexAI && !provider.useServiceAccount) { "unsupported_auth" }; provider.baseUrl to provider.apiKey }
            else -> throw IllegalArgumentException("unsupported_protocol")
        }
        require(key.isNotBlank()) { "missing_key" }
        val baseUrl = base.trimEnd('/').toHttpUrl()
        require(baseUrl.isHttps && baseUrl.username.isEmpty() && baseUrl.password.isEmpty()) { "secure_endpoint_required" }
        val transport = client.newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }
            .followRedirects(false).followSslRedirects(false).callTimeout(25, TimeUnit.SECONDS).build()
        val models = mutableListOf<RealtimeModelOption>()
        var token: String? = null
        repeat(10) {
            val url = baseUrl.newBuilder().addPathSegment("models").apply {
                if (protocol == RealtimeVoiceProtocol.GEMINI) {
                    setQueryParameter("pageSize", "1000"); token?.let { setQueryParameter("pageToken", it) }
                }
            }.build()
            val request = Request.Builder().url(url).apply {
                provider.customHeaders.forEach { header(it.name, it.value) }
                header(if (protocol == RealtimeVoiceProtocol.GEMINI) "x-goog-api-key" else "Authorization", if (protocol == RealtimeVoiceProtocol.GEMINI) key.trim() else "Bearer ${key.trim()}")
            }.build()
            transport.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw HttpException("voice_model_catalog", response.code)
                require(response.body.contentLength() <= 2_000_000) { "invalid_response" }
                val source = response.body.source()
                source.request(2_000_001)
                require(source.buffer.size <= 2_000_000) { "invalid_response" }
                val root = Json.parseToJsonElement(source.buffer.readUtf8()) as? JsonObject ?: error("invalid_response")
                models += parse(root, protocol)
                token = root.s("nextPageToken").ifBlank { null }
            }
            if (token == null || protocol != RealtimeVoiceProtocol.GEMINI) return@withContext models.distinctBy { it.id }.sortedBy { it.id }
        }
        error("model_catalog_page_limit")
    }
}
