package me.rerere.zeroagent.data.ai.realtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.ai.util.HttpException
import me.rerere.ai.util.parseErrorDetail
import me.rerere.common.http.await
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal data class RealtimeModelOption(val id: String, val name: String, val voices: List<String> = emptyList(), val supportedParameters: List<String> = emptyList())
internal data class RealtimeDiscovery(val protocol: RealtimeVoiceProtocol, val models: List<RealtimeModelOption>)
internal object RealtimeModelCatalog {
    fun parse(root: JsonObject, protocol: RealtimeVoiceProtocol): List<RealtimeModelOption> {
        val data = root[if (protocol == RealtimeVoiceProtocol.GEMINI) "models" else "data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { value ->
            val m = value as? JsonObject ?: return@mapNotNull null
            val id = m.s(if (protocol == RealtimeVoiceProtocol.GEMINI) "name" else "id").removePrefix("models/")
            val methods = m.a("supportedGenerationMethods").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val native = if (protocol == RealtimeVoiceProtocol.GEMINI)
                // Declared bidi method takes precedence; also match known Gemini realtime & live models.
                methods.any { it.equals("bidiGenerateContent", true) } ||
                    id.contains("gemini-2", true) ||
                    id.contains("native-audio", true) ||
                    id.contains("-live", true) ||
                    id.contains("realtime", true)
                else !id.contains("transcrib", true) && (id.contains("realtime", true) ||
                    (m.o("capabilities")["realtime"] as? JsonPrimitive)?.booleanOrNull == true ||
                    m.a("supportedProtocols").any { (it as? JsonPrimitive)?.contentOrNull.equals("realtime", true) })
            if (id.isBlank() || !native) null else RealtimeModelOption(id, m.s("displayName").ifBlank { m.s("name").ifBlank { id } }, m.a("voices").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.distinct(), (m.a("supported_parameters") + m.a("supportedParameters")).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.distinct())
        }.distinctBy { it.id }.sortedBy { it.id }
    }
    private data class Cached(val at: Long, val value: RealtimeDiscovery)
    private val cache = LinkedHashMap<String, Cached>()
    suspend fun discover(client: OkHttpClient, provider: ProviderSetting, requested: RealtimeVoiceProtocol, forceRefresh: Boolean = false): RealtimeDiscovery {
        val credentials = when (provider) {
            is ProviderSetting.Google -> {
                require(!provider.vertexAI && !provider.useServiceAccount) { "unsupported_auth" }
                provider.baseUrl + "\u0000" + provider.apiKey
            }
            is ProviderSetting.OpenAI -> provider.baseUrl + "\u0000" + provider.apiKey
            else -> error("unsupported_protocol")
        }
        val headerIdentity = provider.customHeaders.joinToString("\u0001") { it.name.lowercase() + "\u0000" + it.value }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest((credentials + "\u0000" + headerIdentity).toByteArray()).joinToString("") { "%02x".format(it) }
        val key = "${provider.id}/$requested/$digest"
        synchronized(cache) { cache[key]?.takeIf { !forceRefresh && System.currentTimeMillis() - it.at < 300_000 }?.let { return it.value } }
        val first = RealtimeConnections.protocol(provider, requested)
        val choices = if (requested == RealtimeVoiceProtocol.AUTO) listOf(first, if (first == RealtimeVoiceProtocol.GEMINI) RealtimeVoiceProtocol.OPENAI else RealtimeVoiceProtocol.GEMINI) else listOf(first)
        var failure: Exception? = null
        for (candidate in choices) {
            try {
                val result = RealtimeDiscovery(candidate, load(client, provider, candidate))
                synchronized(cache) {
                    val cached = Cached(System.currentTimeMillis(), result)
                    cache[key] = cached
                    cache["${provider.id}/$candidate/$digest"] = cached
                    while (cache.size > 32) cache.remove(cache.keys.first())
                }
                return result
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                // Auth, rate and network failures cannot be fixed by probing another protocol.
                if (e.message != "catalog_protocol_mismatch" &&
                    !(e is HttpException && e.statusCode in setOf(404, 405))) throw e
                failure = e
            }
        }
        if (failure is HttpException) throw failure
        val defaultModels = when (first) {
            RealtimeVoiceProtocol.GEMINI -> listOf(
                RealtimeModelOption("gemini-2.0-flash-exp", "Gemini 2.0 Flash (Experimental)", voices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")),
                RealtimeModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", voices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede"))
            )
            else -> listOf(
                RealtimeModelOption("gpt-4o-realtime-preview", "GPT-4o Realtime Preview", voices = listOf("alloy", "echo", "shimmer", "ash", "ballad", "coral", "sage", "verse")),
                RealtimeModelOption("gpt-4o-mini-realtime-preview", "GPT-4o Mini Realtime Preview", voices = listOf("alloy", "echo", "shimmer", "ash", "ballad", "coral", "sage", "verse"))
            )
        }
        return RealtimeDiscovery(first, defaultModels)
    }
    suspend fun load(client: OkHttpClient, provider: ProviderSetting, requested: RealtimeVoiceProtocol): List<RealtimeModelOption> = withContext(Dispatchers.IO) {
        val protocol = RealtimeConnections.protocol(provider, requested)
        val (base, key) = when (provider) {
            is ProviderSetting.OpenAI -> provider.baseUrl to provider.apiKey
            is ProviderSetting.Google -> { require(!provider.vertexAI && !provider.useServiceAccount) { "unsupported_auth" }; provider.baseUrl to provider.apiKey }
            else -> throw IllegalArgumentException("unsupported_protocol")
        }
        val apiKey = realtimeApiKey(key)
        val baseUrl = realtimeBaseUrl(base)
        val transport = client.newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }
            .cache(null).followRedirects(false).followSslRedirects(false).callTimeout(25, TimeUnit.SECONDS).build()
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
                header(if (protocol == RealtimeVoiceProtocol.GEMINI) "x-goog-api-key" else "Authorization", if (protocol == RealtimeVoiceProtocol.GEMINI) apiKey else "Bearer $apiKey")
            }.build()
            transport.newCall(request).await().use { response ->
                if (response.body.contentLength() > 2_000_000) throw HttpException("invalid_response", response.code)
                val source = response.body.source()
                source.request(2_000_001)
                if (source.buffer.size > 2_000_000) throw HttpException("invalid_response", response.code)
                // ResponseBody.string handles the HTTP charset/BOM; reading buffer UTF-8 bypasses it.
                val body = response.body.string()
                val parsed = runCatching { Json.parseToJsonElement(body) }.getOrNull()
                if (!response.isSuccessful || (parsed as? JsonObject)?.get("error")?.let { it != JsonNull } == true) {
                    val failure = parsed?.parseErrorDetail(response.code.takeIf { it >= 400 })
                        ?: HttpException(body.take(1000).ifBlank { "voice_model_catalog" }, response.code)
                    val secrets = listOf(key, apiKey) + provider.customHeaders.map { it.value }
                    fun redact(text: String) = secrets.filter { it.isNotBlank() }.fold(text) { result, secret ->
                        result.replace(secret, "[redacted]")
                    }
                    // Redact after JSON decoding too: the service can escape a key in its message.
                    throw HttpException(redact(failure.message.orEmpty()), failure.statusCode, failure.errorCode?.let(::redact))
                }
                val root = parsed as? JsonObject ?: throw HttpException("invalid_response", response.code)
                val expected = if (protocol == RealtimeVoiceProtocol.GEMINI) "models" else "data"
                if (root[expected] !is JsonArray) {
                    val alternate = if (expected == "models") "data" else "models"
                    require(root[alternate] !is JsonArray) { "catalog_protocol_mismatch" }
                    // Protobuf JSON omits empty repeated fields, so an empty ListModelsResponse is {}.
                    if (protocol != RealtimeVoiceProtocol.GEMINI || root.keys.any { it != "nextPageToken" }) {
                        throw HttpException("invalid_response", response.code)
                    }
                }
                models += parse(root, protocol)
                token = root.s("nextPageToken").ifBlank { null }
            }
            if (token == null || protocol != RealtimeVoiceProtocol.GEMINI) return@withContext models.distinctBy { it.id }.sortedBy { it.id }
        }
        error("model_catalog_page_limit")
    }
}

/** Only fixed machine codes reach the UI; provider messages can contain credentials or request URLs. */
internal fun realtimeCatalogDiagnostic(failure: Throwable): String {
    val http = failure as? HttpException
    val code = http?.errorCode?.takeIf { it in setOf(
        "INVALID_ARGUMENT", "UNAUTHENTICATED", "PERMISSION_DENIED", "NOT_FOUND",
        "RESOURCE_EXHAUSTED", "FAILED_PRECONDITION", "INTERNAL", "UNAVAILABLE",
        "API_KEY_INVALID", "API_KEY_EXPIRED", "invalid_api_key", "insufficient_quota",
    ) }
    return listOfNotNull(http?.statusCode?.let { "HTTP $it" }, code).joinToString(" · ")
        .ifEmpty { failure.javaClass.simpleName }
}
