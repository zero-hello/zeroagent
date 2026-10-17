package me.rerere.asr.providers

import kotlinx.serialization.json.*
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.CloudSpeechService
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class CloudSpeechProtocolTest {
    private fun setting(service: CloudSpeechService) = ASRProviderSetting.Cloud(service = service, apiKey = "test-only", language = "zh-TW")
    private fun body(protocol: CloudSpeechProtocol): JsonObject {
        val buffer = Buffer(); protocol.request(byteArrayOf(1, 2)).body!!.writeTo(buffer)
        return Json.parseToJsonElement(buffer.readUtf8()).jsonObject
    }
    @Test fun `Groq uses multipart Whisper audio rather than a fake websocket`() {
        val protocol = CloudSpeechProtocol(setting(CloudSpeechService.GROQ));val request = protocol.request(byteArrayOf(1, 2))
        assertEquals("api.groq.com", request.url.host)
        assertEquals("Bearer test-only", request.header("Authorization"))
        assertTrue(request.body is MultipartBody)
        assertFalse(CloudSpeechService.GROQ.streaming)
        assertEquals("hello", (protocol.parse("""{"text":"hello"}""").single() as CloudSpeechEvent.Transcript).text)
    }
    @Test fun `Deepgram authentication and partial identity follow listen v1`() {
        val protocol = CloudSpeechProtocol(setting(CloudSpeechService.DEEPGRAM));val request = protocol.request()
        assertEquals("Token test-only", request.header("Authorization"));assertEquals("linear16", request.url.queryParameter("encoding"))
        val result = protocol.parse("""{"type":"Results","start":0.0,"is_final":false,"channel":{"alternatives":[{"transcript":"hello"}]}}""").single() as CloudSpeechEvent.Transcript
        assertEquals("0.0", result.key);assertEquals("hello", result.text);assertFalse(result.final)
        assertEquals("CloseStream", Json.parseToJsonElement(protocol.finishMessage()).jsonObject["type"]!!.jsonPrimitive.content)
    }
    @Test fun `Assembly formatted turns replace the same turn instead of duplicating it`() {
        val protocol = CloudSpeechProtocol(setting(CloudSpeechService.ASSEMBLYAI));val request = protocol.request()
        assertEquals("test-only", request.header("Authorization"));assertEquals("16000", request.url.queryParameter("sample_rate"))
        assertEquals("universal-3-6-pro", request.url.queryParameter("speech_model"))
        val partial = protocol.parse("""{"type":"Turn","turn_order":2,"transcript":"hi","end_of_turn":false}""").single() as CloudSpeechEvent.Transcript
        val final = protocol.parse("""{"type":"Turn","turn_order":2,"transcript":"Hi.","end_of_turn":true}""").single() as CloudSpeechEvent.Transcript
        assertEquals(partial.key, final.key);assertTrue(final.final)
        assertTrue(protocol.parse("""{"type":"Termination"}""").single() is CloudSpeechEvent.Complete)
    }
    @Test fun `ElevenLabs commits actual final PCM and advances only after final transcript`() {
        val protocol = CloudSpeechProtocol(setting(CloudSpeechService.ELEVENLABS))
        assertEquals("test-only", protocol.request().header("xi-api-key"))
        val frame = Json.parseToJsonElement(protocol.audioJson(byteArrayOf(1, 2), true)).jsonObject
        assertTrue(frame["commit"]!!.jsonPrimitive.boolean)
        assertArrayEquals(byteArrayOf(1, 2), Base64.getDecoder().decode(frame["audio_base_64"]!!.jsonPrimitive.content))
        val first = protocol.parse("""{"message_type":"partial_transcript","text":"hel"}""").single() as CloudSpeechEvent.Transcript
        val final = protocol.parse("""{"message_type":"committed_transcript","text":"hello"}""").single() as CloudSpeechEvent.Transcript
        val second = protocol.parse("""{"message_type":"partial_transcript","text":"world"}""").single() as CloudSpeechEvent.Transcript
        assertEquals(first.key, final.key);assertNotEquals(first.key, second.key)
        assertTrue(protocol.parse("""{"message_type":"quota_exceeded"}""").single() is CloudSpeechEvent.Error)
    }
    @Test fun `Google REST input explicitly describes PCM and keeps auth out of URL`() {
        val protocol = CloudSpeechProtocol(setting(CloudSpeechService.GOOGLE_CLOUD));val request = protocol.request(byteArrayOf(1, 2));val json = body(protocol)
        assertEquals("test-only", request.header("x-goog-api-key"));assertFalse(request.url.toString().contains("test-only"))
        assertEquals("LINEAR16", json["config"]!!.jsonObject["encoding"]!!.jsonPrimitive.content)
        assertArrayEquals(byteArrayOf(1, 2), Base64.getDecoder().decode(json["audio"]!!.jsonObject["content"]!!.jsonPrimitive.content))
        assertEquals("hello", (protocol.parse("""{"results":[{"alternatives":[{"transcript":"hello"}]}]}""").single() as CloudSpeechEvent.Transcript).text)
    }
    @Test fun `Azure sends WAV with the regional key and uses DisplayText`() {
        val protocol = CloudSpeechProtocol(setting(CloudSpeechService.AZURE));val request = protocol.request(byteArrayOf(1, 2))
        assertEquals("test-only", request.header("Ocp-Apim-Subscription-Key"));assertEquals("zh-TW", request.url.queryParameter("language"))
        val buffer = Buffer();request.body!!.writeTo(buffer);assertEquals("RIFF", buffer.readUtf8(4))
        assertEquals("hello", (protocol.parse("""{"RecognitionStatus":"Success","DisplayText":"hello"}""").single() as CloudSpeechEvent.Transcript).text)
    }
    @Test fun `Gemini transcription excludes thought content and configures the chosen model`() {
        val protocol = CloudSpeechProtocol(setting(CloudSpeechService.GEMINI));val request = protocol.request(byteArrayOf(1, 2));val json = body(protocol)
        assertTrue(request.url.encodedPath.endsWith("models/gemini-2.5-flash:generateContent"))
        assertEquals(0, json["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingBudget"]!!.jsonPrimitive.int)
        val result = protocol.parse("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"internal"},{"text":"spoken words"}]}}]}""").single() as CloudSpeechEvent.Transcript
        assertEquals("spoken words", result.text)
    }
    @Test fun `PCM header matches mono 16k 16bit input`() {
        val wave = pcmWave(byteArrayOf(1, 2));val buffer = ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(46, wave.size);assertEquals(16000, buffer.getInt(24));assertEquals(2, buffer.getInt(40))
    }
    @Test(expected = IllegalArgumentException::class) fun `malformed PCM samples are rejected`() { pcmWave(byteArrayOf(1)) }
    @Test fun `all new provider configurations roundtrip without losing fields`() {
        val json = Json { encodeDefaults = true }
        for (service in CloudSpeechService.entries) {
            val value: ASRProviderSetting = setting(service)
            val restored = json.decodeFromString(ASRProviderSetting.serializer(), json.encodeToString(ASRProviderSetting.serializer(), value))
            assertEquals(value, restored)
            assertEquals(service, (restored.copyProvider(name = "renamed") as ASRProviderSetting.Cloud).service)
        }
    }

    @Test fun `HTTP errors preserve status and machine code without leaking server text`() {
        val text = cloudSpeechHttpError(429, """{"error":{"code":"insufficient_quota","message":"private-server-detail"}}""")
        assertEquals("HTTP 429 insufficient_quota", text)
        assertFalse(text.contains("private-server-detail"))
        assertEquals("HTTP 401", cloudSpeechHttpError(401, "not JSON"))
    }

    @Test fun `partial revisions can be retracted without losing committed words`() {
        val segments = linkedMapOf<String, CloudSpeechEvent.Transcript>()
        assertEquals("hello", updateCloudTranscript(segments, CloudSpeechEvent.Transcript("0", "hello", true)))
        assertEquals("hello wrong", updateCloudTranscript(segments, CloudSpeechEvent.Transcript("1", "wrong", false)))
        assertEquals("hello", updateCloudTranscript(segments, CloudSpeechEvent.Transcript("1", "", true)))
        assertEquals("hello", updateCloudTranscript(segments, CloudSpeechEvent.Transcript("0", "stale partial", false)))
        assertEquals("Hello.", updateCloudTranscript(segments, CloudSpeechEvent.Transcript("0", "Hello.", true)))
    }
}
