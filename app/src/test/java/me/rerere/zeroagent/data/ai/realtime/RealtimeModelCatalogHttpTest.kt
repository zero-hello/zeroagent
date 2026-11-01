package me.rerere.zeroagent.data.ai.realtime

import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.GeminiSafetySettings
import me.rerere.ai.provider.GeminiSafetyThreshold
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.ai.util.HttpException
import me.rerere.zeroagent.utils.UserErrorKind
import me.rerere.zeroagent.utils.classifyUserError
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okio.ByteString.Companion.encodeUtf8
import okhttp3.Protocol
import okio.Buffer
import okio.GzipSink
import okio.buffer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

class RealtimeModelCatalogHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before fun startHttpsServer() {
        val certificate = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory()); start() }
        client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .protocols(listOf(Protocol.HTTP_1_1)).build()
    }

    @After fun closeHttpsServer() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.close()
    }

    private fun reply(body: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }
    private fun request() = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    @Test fun `Gemini authenticates and follows model pagination`() = runBlocking {
        reply("""{"models":[{"name":"models/gemini-native-audio-test","supportedGenerationMethods":["bidiGenerateContent"]}],"nextPageToken":"next"}""")
        reply("""{"models":[{"name":"models/gemini-test-live","supportedGenerationMethods":["bidiGenerateContent"]},{"name":"models/gemini-text","supportedGenerationMethods":["generateContent"]}]}""")
        val provider = ProviderSetting.Google(apiKey = "unit-gemini-key", baseUrl = server.url("/v1beta").toString())
        val result = RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO)
        assertEquals(RealtimeVoiceProtocol.GEMINI, result.protocol)
        assertEquals(listOf("gemini-native-audio-test", "gemini-test-live"), result.models.map { it.id })
        val first = request()
        assertEquals("unit-gemini-key", first.headers["x-goog-api-key"])
        assertNull(first.headers["Authorization"])
        assertTrue(first.url.toString().contains("pageSize=1000"))
        assertEquals("next", request().url.queryParameter("pageToken"))
    }

    @Test fun `OpenAI cache separates changed credentials headers and manual refresh`() = runBlocking {
        val body = """{"data":[{"id":"gpt-realtime"}],"error":null}"""
        val provider = ProviderSetting.OpenAI(apiKey = "unit-first-key", baseUrl = server.url("/v1").toString())
        reply(body)
        val first = RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO)
        assertEquals(first, RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO))
        assertEquals(1, server.requestCount)
        assertEquals("Bearer unit-first-key", request().headers["Authorization"])
        val changedKey = provider.copy(apiKey = "unit-second-key")
        reply(body)
        RealtimeModelCatalog.discover(client, changedKey, RealtimeVoiceProtocol.AUTO)
        assertEquals("Bearer unit-second-key", request().headers["Authorization"])
        val changedHeaders = changedKey.copy(customHeaders = listOf(CustomHeader("OpenAI-Project", "unit-project")))
        reply(body)
        RealtimeModelCatalog.discover(client, changedHeaders, RealtimeVoiceProtocol.AUTO)
        assertEquals("unit-project", request().headers["OpenAI-Project"])
        reply(body)
        RealtimeModelCatalog.discover(client, changedHeaders, RealtimeVoiceProtocol.AUTO, forceRefresh = true)
        request()
        assertEquals(4, server.requestCount)
    }

    @Test fun `invalid Gemini key preserves auth error and does not send another protocol probe`() = runBlocking {
        val key = "unit-private-key"
        reply("""{"error":{"code":400,"status":"INVALID_ARGUMENT","message":"API key not valid. Please pass a valid API key. $key"}}""", 400)
        val provider = ProviderSetting.Google(apiKey = key, baseUrl = server.url("/v1beta").toString())
        val failure = try {
            RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO)
            error("Expected authentication failure")
        } catch (failure: HttpException) { failure }
        assertEquals(400, failure.statusCode)
        assertEquals(UserErrorKind.AUTH, classifyUserError(failure))
        assertFalse(failure.message.orEmpty().contains(key))
        assertEquals(1, server.requestCount)
    }

    @Test fun `compatible gateway protocol comes from its actual catalog envelope`() = runBlocking {
        val body = """{"data":[{"id":"gpt-realtime"}]}"""
        reply(body); reply(body)
        val provider = ProviderSetting.Google(apiKey = "unit-gateway-key", baseUrl = server.url("/gateway").toString())
        val result = RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO)
        assertEquals(RealtimeVoiceProtocol.OPENAI, result.protocol)
        assertEquals(listOf("gpt-realtime"), result.models.map { it.id })
        assertNotNull(request().headers["x-goog-api-key"])
        assertEquals("Bearer unit-gateway-key", request().headers["Authorization"])
    }

    @Test fun `HTTP success with provider error is not a successful empty catalog`() = runBlocking {
        reply("""{"error":{"type":"invalid_api_key","message":"invalid api key"}}""")
        val provider = ProviderSetting.OpenAI(apiKey = "unit-invalid", baseUrl = server.url("/v1").toString())
        val failure = try {
            RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO)
            error("Expected provider failure")
        } catch (failure: HttpException) { failure }
        assertEquals(UserErrorKind.AUTH, classifyUserError(failure))
        assertEquals(1, server.requestCount)
    }

    @Test fun `plaintext endpoint is rejected before exposing a key`() = runBlocking {
        val provider = ProviderSetting.OpenAI(apiKey = "unit-secret", baseUrl = server.url("/v1").toString().replace("https://", "http://"))
        try {
            RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.OPENAI)
            fail("Expected HTTPS requirement")
        } catch (failure: IllegalArgumentException) {
            assertEquals("secure_endpoint_required", failure.message)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `production transport handles HTTP2 gzip and BOM without chat interceptors`() = runBlocking {
        val body = "\uFEFF" + """{"models":[{"name":"models/gemini-live-test","displayName":"Live 測試","supportedGenerationMethods":["bidiGenerateContent"]}]}"""
        val compressed = Buffer()
        GzipSink(compressed).buffer().use { it.writeUtf8(body) }
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Content-Encoding", "gzip").body(compressed).build())
        val observedProtocol = AtomicReference<Protocol>()
        val productionClient = client.newBuilder().protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .addInterceptor { error("Chat interceptor must never see voice credentials") }
            .addNetworkInterceptor { error("Chat network logging must never see voice credentials") }
            .eventListener(object : EventListener() {
                override fun responseHeadersEnd(call: Call, response: Response) { observedProtocol.set(response.protocol) }
            }).build()
        val provider = ProviderSetting.Google(apiKey = "unit-first,\nunit-second", baseUrl = "  ${server.url("/v1beta///")}  ")
        val result = RealtimeModelCatalog.load(productionClient, provider, RealtimeVoiceProtocol.GEMINI)
        assertEquals(listOf("gemini-live-test"), result.map { it.id })
        assertEquals("Live 測試", result.single().name)
        assertEquals(Protocol.HTTP_2, observedProtocol.get())
        val sent = request()
        assertEquals("/v1beta/models", sent.url.encodedPath)
        assertEquals("unit-first", sent.headers["x-goog-api-key"])
        assertNull(sent.url.queryParameter("key"))
    }

    @Test fun `protobuf empty model list is successful without inventing a live model`() = runBlocking {
        // Empty repeated fields are omitted by protobuf JSON (ListModelsResponse).
        reply("{}")
        val provider = ProviderSetting.Google(apiKey = "unit-empty", baseUrl = server.url("/v1beta").toString())
        assertTrue(RealtimeModelCatalog.load(client, provider, RealtimeVoiceProtocol.GEMINI).isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test fun `escaped credential in Google error is redacted after decoding and status is retained`() = runBlocking {
        reply("""{"error":{"code":403,"status":"PERMISSION_DENIED","message":"API key unit-\u0070rivate is blocked"}}""", 403)
        val provider = ProviderSetting.Google(apiKey = "unit-private", baseUrl = server.url("/v1beta").toString())
        val failure = try {
            RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO)
            error("Expected failure")
        } catch (failure: HttpException) { failure }
        assertEquals(403, failure.statusCode)
        assertEquals("PERMISSION_DENIED", failure.errorCode)
        assertFalse(failure.message.orEmpty().contains("unit-private"))
        assertTrue(failure.message.orEmpty().contains("[redacted]"))
        assertEquals("HTTP 403 · PERMISSION_DENIED", realtimeCatalogDiagnostic(failure))
        assertEquals(1, server.requestCount)
    }

    @Test fun `malformed successful response exposes HTTP status without echoing response body`() = runBlocking {
        reply("<html>unit-private gateway response</html>")
        val provider = ProviderSetting.Google(apiKey = "unit-private", baseUrl = server.url("/v1beta").toString())
        val failure = try {
            RealtimeModelCatalog.load(client, provider, RealtimeVoiceProtocol.GEMINI)
            error("Expected failure")
        } catch (failure: HttpException) { failure }
        assertEquals("invalid_response", failure.message)
        assertEquals("HTTP 200", realtimeCatalogDiagnostic(failure))
        assertEquals(UserErrorKind.FORMAT, classifyUserError(failure))
    }

    @Test fun `diagnostic never displays untrusted exception message or arbitrary error code`() {
        val failure = HttpException("URL contains unit-private", 400, "unit-private")
        assertEquals("HTTP 400", realtimeCatalogDiagnostic(failure))
        assertEquals("IllegalArgumentException", realtimeCatalogDiagnostic(IllegalArgumentException("unit-private")))
    }

    @Test fun `Gemini socket sends official setup and waits for binary setup complete`() {
        // https://ai.google.dev/api/live — setup is first; only setupComplete confirms readiness.
        val setupFrames = LinkedBlockingQueue<String>()
        server.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                setupFrames.offer(text)
                webSocket.send("""{"setupComplete":{}}""".encodeUtf8())
            }
        }).build())
        val thresholds = mapOf(
            "HARM_CATEGORY_HARASSMENT" to GeminiSafetyThreshold.BLOCK_ONLY_HIGH,
            "HARM_CATEGORY_HATE_SPEECH" to GeminiSafetyThreshold.BLOCK_NONE,
            "HARM_CATEGORY_SEXUALLY_EXPLICIT" to GeminiSafetyThreshold.OFF,
            "HARM_CATEGORY_DANGEROUS_CONTENT" to GeminiSafetyThreshold.BLOCK_LOW_AND_ABOVE,
            "HARM_CATEGORY_CIVIC_INTEGRITY" to GeminiSafetyThreshold.BLOCK_NONE,
        )
        val provider = ProviderSetting.Google(apiKey = "unit-first,\nunit-second", baseUrl = server.url("/v1beta").toString(),
            safetySettings = GeminiSafetySettings(thresholds))
        val model = me.rerere.ai.provider.Model(modelId = "gemini-live-test")
        val connection = RealtimeConnections.create(model, provider)
        val ready = AtomicBoolean(false)
        val confirmed = CountDownLatch(1)
        val failures = LinkedBlockingQueue<String>()
        val socket = client.newWebSocket(Request.Builder().url(connection.url).build(), RealtimeSocketListener(
            setup = connection.protocol.setup(model, "test instruction", provider), isTerminal = { false }, isReady = { ready.get() },
            receive = { frame ->
                connection.protocol.events(Json.parseToJsonElement(frame).jsonObject).forEach {
                    if (it == RealtimeEvent.Ready) { ready.set(true); confirmed.countDown() }
                }
            }, fail = { failures.offer(it) },
        ))
        try {
            assertTrue("No setupComplete received", confirmed.await(3, TimeUnit.SECONDS))
            val setup = Json.parseToJsonElement(requireNotNull(setupFrames.poll(3, TimeUnit.SECONDS))).jsonObject.o("setup")
            assertEquals("models/gemini-live-test", setup.s("model"))
            assertEquals(listOf("AUDIO"), setup.o("generationConfig").a("responseModalities").map { it.toString().trim('"') })
            assertEquals("test instruction", setup.o("systemInstruction").a("parts").single().jsonObject.s("text"))
            val capturedSafety = setup.a("safetySettings").map { it.jsonObject }.associate { it.s("category") to it.s("threshold") }
            assertEquals(thresholds.filterKeys { it != "HARM_CATEGORY_CIVIC_INTEGRITY" }.mapValues { it.value.name }, capturedSafety)
            assertEquals(4, capturedSafety.size)
            assertTrue(failures.isEmpty())
            val sent = request()
            assertEquals("/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent", sent.url.encodedPath)
            assertEquals("unit-first", sent.url.queryParameter("key"))
        } finally { socket.cancel() }
    }

    @Test fun `Gemini setup rejection close frame fails immediately with a secret free category`() {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                webSocket.close(1008, "API key unit-private not valid")
            }
        }).build())
        val failures = LinkedBlockingQueue<String>()
        val terminal = AtomicBoolean(false)
        val provider = ProviderSetting.Google(apiKey = "unit-private", baseUrl = server.url("/v1beta").toString())
        val model = me.rerere.ai.provider.Model(modelId = "gemini-live-test")
        val connection = RealtimeConnections.create(model, provider)
        val socket = client.newWebSocket(Request.Builder().url(connection.url).build(), RealtimeSocketListener(
            setup = connection.protocol.setup(model, ""), isTerminal = { terminal.get() }, isReady = { false },
            receive = { error("Rejected setup cannot be ready") },
            fail = { if (terminal.compareAndSet(false, true)) failures.offer(it) },
        ))
        try {
            assertEquals("authentication_failed", failures.poll(3, TimeUnit.SECONDS))
        } finally { socket.cancel() }
        assertEquals("rate_limited", realtimeCloseError(1008, "RESOURCE_EXHAUSTED", false))
        assertEquals("unsupported_endpoint", realtimeCloseError(1008, "model does not support bidiGenerateContent", false))
        assertEquals("connection_closed", realtimeCloseError(1000, "", true))
    }
}
