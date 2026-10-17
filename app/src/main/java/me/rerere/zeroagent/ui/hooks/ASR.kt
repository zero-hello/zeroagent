package me.rerere.zeroagent.ui.hooks

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import me.rerere.zeroagent.assistant.AssistantMicrophone
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.providers.DashScopeASRController
import me.rerere.asr.providers.DashScopeStreamingASRController
import me.rerere.asr.providers.MiMoASRController
import me.rerere.asr.providers.OpenAIRealtimeASRController
import me.rerere.asr.providers.StepASRController
import me.rerere.asr.providers.VolcengineASRController
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.getSelectedASRProvider
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

@Composable
fun rememberCustomAsrState(): CustomAsrState {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val httpClient = koinInject<OkHttpClient>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    val asrState = remember {
        CustomAsrStateImpl(context.applicationContext, httpClient)
    }

    DisposableEffect(settings.selectedASRProviderId, settings.asrProviders) {
        asrState.updateProvider(settings.getSelectedASRProvider())
        onDispose { }
    }

    DisposableEffect(asrState) {
        onDispose {
            asrState.cleanup()
        }
    }

    return asrState
}

interface CustomAsrState {
    val state: StateFlow<ASRState>
    fun start(onTranscriptChange: (String) -> Unit)
    fun stop()
    fun cleanup()
}

private class CustomAsrStateImpl(
    private val context: Context,
    private val httpClient: OkHttpClient
) : CustomAsrState {
    private var controller: ASRController? = null
    private val micScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var micStart: Job? = null
    private var micWatch: Job? = null
    private var micLease: AssistantMicrophone.Lease? = null
    private var micRevision = 0
    private val idleState = MutableStateFlow(ASRState())

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAcceptsDelayedFocusGain(false)
        .build()

    override val state: StateFlow<ASRState>
        get() = controller?.state ?: idleState

    fun updateProvider(provider: ASRProviderSetting?) {
        stop()
        controller?.dispose()
        controller = provider?.let { createController(it) }
        if (controller == null) {
            idleState.value = ASRState()
        }
    }

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (controller?.state?.value?.isRecording == true) return
        val selected = controller ?: return
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            micStart?.cancel(); val revision = ++micRevision
            micStart = micScope.launch {
                var owned: AssistantMicrophone.Lease? = null
                try {
                    withTimeout(3000) { owned = AssistantMicrophone.acquire() }
                    if (revision != micRevision || controller !== selected) { owned?.close(); return@launch }
                    micLease = owned
                    selected.start(onTranscriptChange)
                    micWatch?.cancel()
                    micWatch = micScope.launch {
                        var recorded = false
                        selected.state.collect { value ->
                            if (revision != micRevision) return@collect
                            if (value.isRecording) recorded = true
                            if (value.status == me.rerere.asr.ASRStatus.Error || (recorded && !value.isRecording)) {
                                micLease?.close(); micLease = null
                                audioManager.abandonAudioFocusRequest(audioFocusRequest)
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    owned?.close(); if (micLease === owned) micLease = null
                    if (revision == micRevision) audioManager.abandonAudioFocusRequest(audioFocusRequest)
                    android.widget.Toast.makeText(context.zeroAgentLocalizedContext(), me.rerere.zeroagent.R.string.za9_error_mic_busy, android.widget.Toast.LENGTH_LONG).show()
                } catch (e: CancellationException) { owned?.close(); if (micLease === owned) micLease = null; throw e }
                catch (_: Exception) {
                    owned?.close(); if (micLease === owned) micLease = null
                    if (revision == micRevision) audioManager.abandonAudioFocusRequest(audioFocusRequest)
                }
            }
        }
    }

    override fun stop() {
        micRevision++; micStart?.cancel(); micWatch?.cancel()
        controller?.stop()
        micLease?.close(); micLease = null
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    override fun cleanup() {
        micRevision++; micStart?.cancel(); micWatch?.cancel()
        controller?.dispose()
        micLease?.close(); micLease = null
        controller = null
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        micScope.cancel()
    }

    private fun createController(provider: ASRProviderSetting): ASRController? = createAsrController(context, httpClient, provider)
}
