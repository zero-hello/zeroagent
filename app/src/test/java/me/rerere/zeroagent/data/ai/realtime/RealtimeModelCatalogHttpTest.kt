package me.rerere.zeroagent.data.ai.realtime

import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.ai.util.HttpException
import me.rerere.zeroagent.utils.UserErrorKind
import me.rerere.zeroagent.utils.classifyUserError
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

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
        reply("""{"models":[{"name":"models/gemini-native-audio-test","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"next"}""")
        reply("""{"models":[{"name":"models/gemini-test-live","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-text","supportedGenerationMethods":["generateContent"]}]}""")
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
}
