package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.ui.context.LocalSettings
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Real production discovery/rendering against local HTTPS only; no paid-model credentials. */
class VoiceModelDiscoveryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before fun startServer() {
        val certificate = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory()); start() }
        client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .protocols(listOf(Protocol.HTTP_1_1)).build()
    }
    @After fun stopServer() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.close()
    }
    @Test fun geminiKeyLoadsLiveModelsIntoTheSelectableMenu() = checkDiscovery(true)
    @Test fun openAiKeyLoadsRealtimeModelsIntoTheSameMenu() = checkDiscovery(false)

    private fun checkDiscovery(gemini: Boolean) {
        val firstId = if (gemini) "gemini-a-live" else "gpt-realtime-a"
        val secondId = if (gemini) "gemini-b-live" else "gpt-realtime-b"
        val response = if (gemini) "\uFEFF" + """{"models":[
            {"name":"models/$firstId","displayName":"Voice Alpha","supportedGenerationMethods":["bidiGenerateContent"]},
            {"name":"models/$secondId","displayName":"Voice Beta","supportedGenerationMethods":["bidiGenerateContent"]}]}"""
        else """{"data":[{"id":"$firstId","displayName":"Voice Alpha"},{"id":"$secondId","displayName":"Voice Beta"}]}"""
        server.enqueue(MockResponse.Builder().body(response).build())
        val account = if (gemini) ProviderSetting.Google(name = "Gemini local HTTPS", apiKey = "", baseUrl = server.url("/v1beta").toString())
            else ProviderSetting.OpenAI(name = "OpenAI local HTTPS", apiKey = "", baseUrl = server.url("/v1").toString())
        val current = mutableStateOf(Settings(providers = listOf(account)))
        var keyLabel = ""
        var modelLabel = ""
        compose.setContent {
            val resources = LocalResources.current
            keyLabel = resources.getString(R.string.setting_provider_page_api_key)
            modelLabel = resources.getString(R.string.zeroagent8_voice_model)
            MaterialTheme {
                CompositionLocalProvider(LocalSettings provides current.value) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        VoiceConversationSettings(current.value, client = client) { change -> current.value = change(current.value) }
                    }
                }
            }
        }
        compose.onNodeWithText(keyLabel).performScrollTo().performTextReplacement("unit-local-voice-key, unused-local-key")
        compose.waitUntil(15_000) { current.value.voiceConversation.modelId == firstId }
        compose.onNodeWithText(modelLabel).performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("Voice Beta").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(secondId, current.value.voiceConversation.modelId)
            assertEquals(if (gemini) RealtimeVoiceProtocol.GEMINI else RealtimeVoiceProtocol.OPENAI,
                current.value.voiceConversation.protocol)
        }
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals(if (gemini) "unit-local-voice-key" else "Bearer unit-local-voice-key",
            request!!.headers[if (gemini) "x-goog-api-key" else "Authorization"])
    }
}
