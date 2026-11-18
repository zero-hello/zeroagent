package me.rerere.ai.provider.providers.cohere

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import me.rerere.ai.provider.stream.SSE_BUFFER_CAPACITY
import me.rerere.ai.provider.stream.sendSseChunks
import me.rerere.ai.ui.*
import me.rerere.ai.util.*
import me.rerere.common.http.await
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.*

class CohereProvider(private val client: OkHttpClient) : Provider<ProviderSetting.OpenAI> {
    private val keys = KeyRoulette.default()
    private fun key(setting: ProviderSetting.OpenAI) = keys.next(setting.apiKey, setting.id.toString())
    private fun failure(code: Int): HttpException = HttpException(when (code) {
        401, 403 -> "Cohere 金鑰無效或帳戶沒有此模型的權限。"
        402 -> "Cohere 額度不足，請查看帳戶方案。"
        429 -> "Cohere 已達使用額度或速率限制，請稍後再試或查看帳戶方案。"
        404 -> "Cohere 找不到模型或端點，請重新取得模型並檢查網址。"
        else -> "Cohere 請求失敗（HTTP $code），請檢查模型及參數。"
    }, code)

    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Model>()
        var token: String? = null
        val seen = mutableSetOf<String>()
        do {
            val url = resolveProviderEndpoint(providerSetting.baseUrl, "/v1/models").toHttpUrl().newBuilder()
                .addQueryParameter("endpoint", "chat").apply { token?.let { addQueryParameter("page_token", it) } }.build()
            val request = Request.Builder().url(url).headers(providerSetting.mergeCustomHeaders())
                .header("Authorization", "Bearer ${key(providerSetting)}").build()
            val value = client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw failure(response.code)
                json.parseToJsonElement(response.body.string()).jsonObject
            }
            val models = value["models"] as? JsonArray ?: error("Cohere 未回傳模型清單。")
            models.forEach { item ->
                val model = item.jsonObject
                val name = model["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                if (model["is_deprecated"]?.jsonPrimitive?.booleanOrNull == true) return@forEach
                val features = (model["features"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                val supportsTools = features.any { it in setOf("tools", "tool_use", "tool_calling") } || name in setOf("command-r-08-2024", "command-r-plus-08-2024", "command-a-03-2025")
                result += Model(modelId = name, displayName = name,
                    abilities = if (supportsTools) listOf(ModelAbility.TOOL) else emptyList(),
                    contextLength = model["context_length"]?.jsonPrimitive?.intOrNull)
            }
            token = value["next_page_token"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            check(token == null || seen.add(token!!)) { "Cohere 模型清單的分頁重複。" }
        } while (token != null)
        result.distinctBy { it.modelId }
    }

    private fun request(setting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams, stream: Boolean): Request =
        Request.Builder().url(resolveProviderEndpoint(setting.baseUrl, "/v2/chat"))
            .headers(setting.mergeCustomHeaders(params.customHeaders))
            .header("Authorization", "Bearer ${key(setting)}")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .post(CohereProtocol.request(messages, params, stream).mergeCustomBody(params.customBody).toString()
                .toRequestBody("application/json".toMediaType())).build()

    override suspend fun generateText(providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams): TextGenerationResult =
        withContext(Dispatchers.IO) {
            client.newCall(request(providerSetting, messages, params, false)).await().use { response ->
                if (!response.isSuccessful) throw failure(response.code)
                CohereProtocol.response(json.parseToJsonElement(response.body.string()).jsonObject, params.model)
            }
        }

    override suspend fun streamText(providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams): Flow<StreamChunk> = callbackFlow {
        val decoder = CohereStreamDecoder(params.model.modelId)
        val source = EventSources.createFactory(client).newEventSource(request(providerSetting, messages, params, true), object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (!sendSseChunks(decoder.decode(json.parseToJsonElement(data).jsonObject))) eventSource.cancel()
                    if (decoder.finished) { close(); eventSource.cancel() }
                } catch (error: Exception) { close(error); eventSource.cancel() }
            }
            override fun onClosed(eventSource: EventSource) {
                if (decoder.finished) close() else close(IOException("Cohere 連線在回覆完成前中斷，請重試。"))
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(if (response != null && !response.isSuccessful) failure(response.code)
                    else IOException("Cohere 串流連線失敗，請檢查網路。"))
            }
        })
        awaitClose { source.cancel() }
    }.buffer(SSE_BUFFER_CAPACITY).flowOn(Dispatchers.IO)

    override suspend fun generateImage(providerSetting: ProviderSetting, params: ImageGenerationParams): Flow<ImageGenerationItem> =
        flow { error("Cohere Chat API 不支援圖片生成，請選擇具備圖片生成能力的供應商與模型。") }
}
