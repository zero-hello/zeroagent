package me.rerere.zeroagent.data.ai.realtime

import kotlinx.serialization.json.*
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Protocol adapters own wire messages; capture/playback and UI never depend on a vendor schema. */
internal interface RealtimeProtocol {
    val inputRate: Int
    val outputRate: Int get() = 24000
    fun setup(model: Model, instructions: String): JsonObject
    fun audio(base64: String): JsonObject
    fun text(text: String): List<JsonObject>
    fun interrupt(item: String?, playedMillis: Int): List<JsonObject>
    fun events(message: JsonObject): List<RealtimeEvent>
}

internal sealed interface RealtimeEvent {
    data object Ready : RealtimeEvent
    data class Audio(val base64: String, val item: String? = null, val rate: Int = 24000) : RealtimeEvent
    data class Transcript(val text: String, val user: Boolean, val delta: Boolean) : RealtimeEvent
    data object SpeechStarted : RealtimeEvent
    data object SpeechStopped : RealtimeEvent
    data object ResponseStarted : RealtimeEvent
    data object ResponseDone : RealtimeEvent
    data class Error(val code: String) : RealtimeEvent
}

internal data class RealtimeConnection(val url: String, val headers: Map<String, String>, val protocol: RealtimeProtocol)

internal fun Model.nativeVoiceModelId() = realtimeVoice.modelId.ifBlank { modelId }.removePrefix("models/")
internal object RealtimeConnections {
    fun protocol(provider: ProviderSetting, requested: RealtimeVoiceProtocol): RealtimeVoiceProtocol =
        if (requested != RealtimeVoiceProtocol.AUTO) requested else when (provider) {
            is ProviderSetting.OpenAI -> RealtimeVoiceProtocol.OPENAI
            is ProviderSetting.Google -> RealtimeVoiceProtocol.GEMINI
            else -> throw IllegalArgumentException("unsupported_protocol")
        }

    fun create(model: Model, provider: ProviderSetting): RealtimeConnection {
        val selected = protocol(provider, model.realtimeVoice.protocol)
        val base = when (provider) {
            is ProviderSetting.OpenAI -> provider.baseUrl
            is ProviderSetting.Google -> {
                require(!provider.vertexAI && !provider.useServiceAccount) { "unsupported_auth" }
                provider.baseUrl
            }
            else -> throw IllegalArgumentException("unsupported_protocol")
        }
        val key = when (provider) {
            is ProviderSetting.OpenAI -> provider.apiKey
            is ProviderSetting.Google -> provider.apiKey
            else -> ""
        }.trim()
        require(key.isNotEmpty()) { "missing_key" }
        require(model.nativeVoiceModelId().isNotBlank()) { "missing_model" }
        val custom = model.realtimeVoice.endpoint.trim()
        val endpoint = if (custom.isNotEmpty()) custom else when (selected) {
            RealtimeVoiceProtocol.GEMINI -> {
                val host = base.toHttpUrl()
                host.newBuilder().encodedPath("/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent").query(null).build().toString()
            }
            else -> base.trimEnd('/') + "/realtime"
        }
        val http = endpoint.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://").toHttpUrl()
        // Never send a configured secret over plaintext, including custom gateways.
        require(http.isHttps && http.username.isEmpty() && http.password.isEmpty()) { "secure_endpoint_required" }
        val url = http.newBuilder().apply {
            if (selected == RealtimeVoiceProtocol.GEMINI) setQueryParameter("key", key)
            else setQueryParameter("model", model.nativeVoiceModelId())
        }.build().toString().replaceFirst("https://", "wss://")
        val textProviderId = model.providerOverwrite?.id ?: provider.id.takeIf { provider.models.any { m -> m.id == model.id } }
        val modelHeaders = if (model.realtimeVoice.providerId.isBlank() || textProviderId == provider.id) model.customHeaders else emptyList()
        val headers = (provider.customHeaders + modelHeaders).associate { it.name to it.value }.toMutableMap()
        if (selected != RealtimeVoiceProtocol.GEMINI) headers["Authorization"] = "Bearer $key"
        return RealtimeConnection(url, headers, if (selected == RealtimeVoiceProtocol.GEMINI) GeminiLiveProtocol else OpenAIRealtimeProtocol)
    }
}

internal fun obj(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(mapOf(*pairs))
internal fun str(value: String): JsonPrimitive = JsonPrimitive(value)
internal fun JsonObject.s(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.o(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.a(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
private fun event(type: String, vararg values: Pair<String, JsonElement>) = obj("type" to str(type), *values)

internal object OpenAIRealtimeProtocol : RealtimeProtocol {
    override val inputRate = 24000
    override fun setup(model: Model, instructions: String) = event("session.update", "session" to obj(
        "type" to str("realtime"), "instructions" to str(instructions),
        "output_modalities" to JsonArray(listOf(str("audio"))),
        "audio" to obj(
            "input" to obj("format" to obj("type" to str("audio/pcm"), "rate" to JsonPrimitive(inputRate)),
                "transcription" to obj("model" to str("gpt-4o-mini-transcribe")),
                "turn_detection" to obj("type" to str("server_vad"), "create_response" to JsonPrimitive(true), "interrupt_response" to JsonPrimitive(true))),
            "output" to obj("format" to obj("type" to str("audio/pcm"), "rate" to JsonPrimitive(outputRate)),
                "voice" to str(model.realtimeVoice.voice.ifBlank { "marin" })))))
    override fun audio(base64: String) = event("input_audio_buffer.append", "audio" to str(base64))
    override fun text(text: String) = listOf(
        event("conversation.item.create", "item" to obj("type" to str("message"), "role" to str("user"),
            "content" to JsonArray(listOf(obj("type" to str("input_text"), "text" to str(text)))))),
        event("response.create"))
    override fun interrupt(item: String?, playedMillis: Int) = buildList {
        add(event("response.cancel"))
        if (item != null) add(event("conversation.item.truncate", "item_id" to str(item),
            "content_index" to JsonPrimitive(0), "audio_end_ms" to JsonPrimitive(playedMillis.coerceAtLeast(0))))
    }
    override fun events(message: JsonObject): List<RealtimeEvent> = listOfNotNull(when (message.s("type")) {
        "session.updated" -> RealtimeEvent.Ready
        "response.created" -> RealtimeEvent.ResponseStarted
        "response.output_audio.delta", "response.audio.delta" -> RealtimeEvent.Audio(message.s("delta"), message.s("item_id").ifEmpty { null })
        "response.output_audio_transcript.delta", "response.audio_transcript.delta" -> RealtimeEvent.Transcript(message.s("delta"), false, true)
        "conversation.item.input_audio_transcription.completed" -> RealtimeEvent.Transcript(message.s("transcript"), true, false)
        "input_audio_buffer.speech_started" -> RealtimeEvent.SpeechStarted
        "input_audio_buffer.speech_stopped" -> RealtimeEvent.SpeechStopped
        "response.done" -> if (message.o("response").s("status") == "failed") RealtimeEvent.Error("model_response_failed") else RealtimeEvent.ResponseDone
        "error" -> message.o("error").let { err ->
            // Cancellation races are normal when server VAD has cancelled first.
            if (err.s("code") in setOf("response_cancel_not_active")) null
            else RealtimeEvent.Error(err.s("code").ifBlank { "protocol_error" })
        }
        else -> null
    })
}

internal object GeminiLiveProtocol : RealtimeProtocol {
    override val inputRate = 16000
    override fun setup(model: Model, instructions: String) = obj("setup" to obj(
        "model" to str("models/" + model.nativeVoiceModelId()),
        "generationConfig" to obj("responseModalities" to JsonArray(listOf(str("AUDIO"))),
            "speechConfig" to obj("voiceConfig" to obj("prebuiltVoiceConfig" to obj("voiceName" to str(model.realtimeVoice.voice.ifBlank { "Kore" }))))),
        "systemInstruction" to obj("parts" to JsonArray(listOf(obj("text" to str(instructions))))),
        "inputAudioTranscription" to obj(), "outputAudioTranscription" to obj(),
        "realtimeInputConfig" to obj("automaticActivityDetection" to obj("disabled" to JsonPrimitive(false)),
            "activityHandling" to str("START_OF_ACTIVITY_INTERRUPTS"))))
    override fun audio(base64: String) = obj("realtimeInput" to obj("audio" to obj("data" to str(base64), "mimeType" to str("audio/pcm;rate=$inputRate"))))
    override fun text(text: String) = listOf(obj("clientContent" to obj("turns" to JsonArray(listOf(obj(
        "role" to str("user"), "parts" to JsonArray(listOf(obj("text" to str(text))))))), "turnComplete" to JsonPrimitive(true))))
    // Automatic activity detection is enabled: continued real mic audio is the server's
    // interruption signal. activityStart is illegal in that mode; do not fabricate it.
    override fun interrupt(item: String?, playedMillis: Int) = emptyList<JsonObject>()
    override fun events(message: JsonObject): List<RealtimeEvent> = buildList {
        if ("setupComplete" in message) add(RealtimeEvent.Ready)
        if ("error" in message) add(RealtimeEvent.Error(message.o("error").s("status").ifBlank { "protocol_error" }))
        val content = message.o("serverContent")
        if (content["interrupted"] == JsonPrimitive(true)) add(RealtimeEvent.SpeechStarted)
        content.o("inputTranscription").s("text").takeIf { it.isNotEmpty() }?.let { add(RealtimeEvent.Transcript(it, true, true)) }
        content.o("outputTranscription").s("text").takeIf { it.isNotEmpty() }?.let { add(RealtimeEvent.Transcript(it, false, true)) }
        content.o("modelTurn").a("parts").forEach { part ->
            val audio = (part as? JsonObject)?.o("inlineData") ?: return@forEach
            if (audio.s("mimeType").startsWith("audio/pcm")) {
                val rate = audio.s("mimeType").substringAfter("rate=", "24000").substringBefore(';').toIntOrNull() ?: 24000
                add(RealtimeEvent.Audio(audio.s("data"), rate = rate))
            }
        }
        if (content["turnComplete"] == JsonPrimitive(true)) add(RealtimeEvent.ResponseDone)
        if ("goAway" in message) add(RealtimeEvent.Error("session_expired"))
    }
}
