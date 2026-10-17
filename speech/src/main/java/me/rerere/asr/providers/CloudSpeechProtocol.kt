package me.rerere.asr.providers

import kotlinx.serialization.json.*
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.CloudSpeechService
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

internal sealed interface CloudSpeechEvent {
    data class Transcript(val key: String, val text: String, val final: Boolean) : CloudSpeechEvent
    data class Error(val code: String) : CloudSpeechEvent
    data object Complete : CloudSpeechEvent
}

internal fun pcmWave(pcm: ByteArray): ByteArray = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
    require(pcm.size % 2 == 0) { "PCM16 requires whole samples" }
    put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray()); putInt(16)
    putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
    put("data".toByteArray()); putInt(pcm.size); put(pcm)
}.array()

internal class CloudSpeechProtocol(private val setting: ASRProviderSetting.Cloud) {
    private var elevenTurn = 0
    private fun encoded(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun jsonBody(body: JsonObject) = body.toString().toRequestBody("application/json".toMediaType())
    fun request(pcm: ByteArray? = null): Request {
        require(setting.apiKey.isNotBlank()) { "authentication required" }
        val raw = setting.endpoint.trim().replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")
        val url = raw.toHttpUrl().newBuilder()
        require(url.build().isHttps) { "unsupported insecure speech endpoint" }
        val language = setting.language.trim()
        val shortLanguage = language.substringBefore('-').lowercase()
        val request = Request.Builder()
        when (setting.service) {
            CloudSpeechService.DEEPGRAM -> {
                request.header("Authorization", "Token ${setting.apiKey}")
                url.setQueryParameter("model", setting.model).setQueryParameter("encoding", "linear16")
                    .setQueryParameter("sample_rate", "16000").setQueryParameter("channels", "1")
                    .setQueryParameter("interim_results", "true").setQueryParameter("smart_format", "true")
                if (language.isNotEmpty()) url.setQueryParameter("language", shortLanguage)
            }
            CloudSpeechService.ASSEMBLYAI -> {
                request.header("Authorization", setting.apiKey)
                url.setQueryParameter("speech_model", setting.model).setQueryParameter("encoding", "pcm_s16le").setQueryParameter("sample_rate", "16000")
                if (shortLanguage.isNotEmpty()) url.setQueryParameter("language_codes", shortLanguage)
                if (setting.model.startsWith("universal-streaming")) url.setQueryParameter("format_turns", "true")
            }
            CloudSpeechService.ELEVENLABS -> {
                request.header("xi-api-key", setting.apiKey)
                url.setQueryParameter("model_id", setting.model).setQueryParameter("audio_format", "pcm_16000")
                    .setQueryParameter("commit_strategy", "manual")
                if (shortLanguage.isNotEmpty()) url.setQueryParameter("language_code", shortLanguage)
            }
            CloudSpeechService.GROQ -> {
                request.header("Authorization", "Bearer ${setting.apiKey}")
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", "speech.wav", pcmWave(checkNotNull(pcm)).toRequestBody("audio/wav".toMediaType()))
                    .addFormDataPart("model", setting.model).addFormDataPart("response_format", "json")
                if (shortLanguage.isNotEmpty()) body.addFormDataPart("language", shortLanguage)
                request.post(body.build())
            }
            CloudSpeechService.GOOGLE_CLOUD -> {
                request.header("x-goog-api-key", setting.apiKey)
                request.post(jsonBody(buildJsonObject {
                    put("config", buildJsonObject { put("encoding", "LINEAR16"); put("sampleRateHertz", 16000)
                        put("languageCode", language.ifBlank { "en-US" }); put("enableAutomaticPunctuation", true)
                        if (setting.model.isNotBlank()) put("model", setting.model) })
                    put("audio", buildJsonObject { put("content", encoded(checkNotNull(pcm))) })
                }))
            }
            CloudSpeechService.AZURE -> {
                request.header("Ocp-Apim-Subscription-Key", setting.apiKey)
                url.setQueryParameter("language", language.ifBlank { "en-US" }).setQueryParameter("format", "simple")
                request.post(pcmWave(checkNotNull(pcm)).toRequestBody("audio/wav; codecs=\"audio/pcm\"; samplerate=16000".toMediaType()))
            }
            CloudSpeechService.GEMINI -> {
                request.header("x-goog-api-key", setting.apiKey)
                url.addPathSegment("models").addPathSegment(setting.model.removePrefix("models/") + ":generateContent")
                request.post(jsonBody(buildJsonObject {
                    put("contents", buildJsonArray { add(buildJsonObject { put("role", "user"); put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", "Transcribe the spoken audio verbatim in its original language. Output only the transcript, with no greeting or explanation. If there is no speech, return an empty string.") })
                        add(buildJsonObject { put("inlineData", buildJsonObject { put("mimeType", "audio/wav"); put("data", encoded(pcmWave(checkNotNull(pcm)))) }) })
                    }) }) })
                    put("generationConfig", buildJsonObject { put("temperature", 0)
                        if (setting.model.startsWith("gemini-2.5-flash")) put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) }) })
                }))
            }
        }
        return request.url(url.build()).build()
    }
    fun audioJson(pcm: ByteArray, commit: Boolean = false): String = buildJsonObject {
        put("message_type", "input_audio_chunk"); put("audio_base_64", encoded(pcm)); put("commit", commit)
    }.toString()
    fun finishMessage(): String = when (setting.service) {
        CloudSpeechService.DEEPGRAM -> """{"type":"CloseStream"}"""
        CloudSpeechService.ASSEMBLYAI -> """{"type":"Terminate"}"""
        else -> error("finalization requires an audio commit")
    }
    fun parse(text: String): List<CloudSpeechEvent> {
        val root = Json.parseToJsonElement(text).jsonObject
        fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
        val type = root.string("type").ifBlank { root.string("message_type") }
        if (type.equals("error", true) || type.endsWith("_error") || type in setOf("quota_exceeded", "rate_limited", "session_time_limit_exceeded")) {
            val kind = when {
                "auth" in type -> "authentication failed"
                "quota" in type -> "quota exceeded"
                "rate" in type -> "rate limit"
                else -> "speech provider error"
            }
            return listOf(CloudSpeechEvent.Error(kind))
        }
        return when (setting.service) {
            CloudSpeechService.DEEPGRAM -> if (type == "Results") {
                val value = (root["channel"] as? JsonObject)?.get("alternatives")?.jsonArray?.firstOrNull()?.jsonObject?.string("transcript").orEmpty()
                listOf(CloudSpeechEvent.Transcript(root.string("start"), value, root["is_final"]?.jsonPrimitive?.booleanOrNull == true))
            } else emptyList()
            CloudSpeechService.ASSEMBLYAI -> when (type) {
                "Turn" -> listOf(CloudSpeechEvent.Transcript(root.string("turn_order"), root.string("transcript"), root["end_of_turn"]?.jsonPrimitive?.booleanOrNull == true))
                "Termination" -> listOf(CloudSpeechEvent.Complete)
                else -> emptyList()
            }
            CloudSpeechService.ELEVENLABS -> when (type) {
                "partial_transcript" -> listOf(CloudSpeechEvent.Transcript(elevenTurn.toString(), root.string("text"), false))
                "committed_transcript", "committed_transcript_with_timestamps" -> listOf(CloudSpeechEvent.Transcript((elevenTurn++).toString(), root.string("text"), true))
                else -> emptyList()
            }
            CloudSpeechService.GROQ -> listOf(CloudSpeechEvent.Transcript("batch", root.string("text"), true))
            CloudSpeechService.GOOGLE_CLOUD -> listOf(CloudSpeechEvent.Transcript("batch", root["results"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonObject["alternatives"]?.jsonArray?.firstOrNull()?.jsonObject?.string("transcript")
            }.joinToString(" "), true))
            CloudSpeechService.AZURE -> {
                val status = root.string("RecognitionStatus")
                if (status.isNotEmpty() && status !in setOf("Success", "NoMatch")) listOf(CloudSpeechEvent.Error("speech recognition failed"))
                else listOf(CloudSpeechEvent.Transcript("batch", root.string("DisplayText"), true))
            }
            CloudSpeechService.GEMINI -> listOf(CloudSpeechEvent.Transcript("batch", root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty().mapNotNull {
                    val part = it as? JsonObject
                    if (part?.get("thought")?.jsonPrimitive?.booleanOrNull == true) null else part?.string("text")
                }.joinToString(""), true))
        }
    }
}

internal fun cloudSpeechHttpError(status: Int, body: String): String {
    val code = runCatching {
        val root = Json.parseToJsonElement(body).jsonObject
        val error = root["error"] as? JsonObject ?: root
        listOf("status", "code", "type").mapNotNull { (error[it] as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { value -> value.length <= 64 && value.any { it.isLetter() } && value.all { it.isLetterOrDigit() || it == '_' || it == '-' } }
    }.getOrNull().orEmpty()
    return "HTTP $status $code".trim()
}

/** A revised/empty partial must not survive a final correction or overwrite committed text. */
internal fun updateCloudTranscript(
    segments: MutableMap<String, CloudSpeechEvent.Transcript>, event: CloudSpeechEvent.Transcript,
): String {
    val previous = segments[event.key]
    if (previous?.final != true || event.final) {
        if (event.text.isBlank()) {
            if (previous?.final != true) segments.remove(event.key)
        } else segments[event.key] = event.copy(text = event.text.trim())
    }
    return segments.values.joinToString(" ") { it.text }
}
