package me.rerere.zeroagent.assistant

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.rerere.asr.ASRProviderSetting
import me.rerere.tts.controller.AudioPlayer
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.zeroagent.assistant.wake.*
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext

internal data class AssistantSetupState(
    val busy: Boolean = false, val ready: Boolean = false, val count: Int = 0, val target: Int = 5,
    val profile: WakeProfile? = null, val recording: Boolean = false, val training: Boolean = false,
    val level: Float = 0f, val quality: SampleProblem? = null, val issue: AssistantIssue? = null,
    val detail: String? = null, val tts: AssistantTtsCapabilities? = null, val probing: Boolean = false,
    val previewing: Boolean = false,
)

internal class VoiceAssistantSettingsVM(application: Application, private val store: SettingsStore, manager: TTSManager) : AndroidViewModel(application) {
    val settings = store.settingsFlow
    private val app = application
    private val profiles = WakeProfileStore(app)
    private val samples = mutableListOf<ShortArray>()
    private val mutable = MutableStateFlow(AssistantSetupState())
    val state = mutable.asStateFlow()
    private var recording: Job? = null
    private var trainingJob: Job? = null
    private var recordRevision = 0
    private var previewRevision = 0
    private var previewJob: Job? = null
    private var probe: Job? = null
    private var settingWrite: Job? = null
    private val previewAudio = AudioPlayer(app)
    private val previewTts = TtsController(app, manager)
    init { viewModelScope.launch { store.settingsFlow.first { !it.init }; reload() } }
    private suspend fun reload() {
        try {
            val data = profiles.data(); val saved = profiles.samples()
            samples.clear(); if (data.phrase == store.settingsFlow.value.voiceAssistant.wakePhrase) samples.addAll(saved)
            mutable.update { it.copy(ready = true, count = samples.size, target = if (samples.size > 5) 10 else 5, profile = data.profile) }
        } catch (e: Throwable) { report(e, AssistantIssue.WAKE_CORRUPT); mutable.update { it.copy(ready = true) } }
    }
    fun update(change: (VoiceAssistantSettings) -> VoiceAssistantSettings) { settingWrite = viewModelScope.launch { store.updateVoiceAssistant(change) } }
    fun permissionDenied(issue: AssistantIssue) { mutable.update { it.copy(issue = issue, detail = null) } }
    fun clearIssue() { mutable.update { it.copy(issue = null, detail = null, quality = null) } }
    fun enable(value: Boolean) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                if (!value) {
                    stopRecording(); stopPreview()
                    store.updateVoiceAssistant { it.copy(enabled = false, wakeEnabled = false) }
                } else {
                    settingWrite?.join()
                    val settings = store.settingsFlow.value; val config = settings.voiceAssistant
                    if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw AssistantFailure(AssistantIssue.MICROPHONE_PERMISSION)
                    assistantModel(settings)
                    if (config.sttProviderId.isBlank()) {
                        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(app) && !config.systemSttNetworkAllowed) throw AssistantFailure(AssistantIssue.STT_LOCAL_UNAVAILABLE)
                        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(app) && externalRecognitionService(app) == null) throw AssistantFailure(AssistantIssue.STT_UNAVAILABLE)
                    } else {
                        val p = settings.asrProviders.firstOrNull { it.id.toString() == config.sttProviderId } ?: throw AssistantFailure(AssistantIssue.STT_UNAVAILABLE)
                        val key = when (p) {
                            is ASRProviderSetting.Cloud -> p.apiKey; is ASRProviderSetting.OpenAIRealtime -> p.apiKey
                            is ASRProviderSetting.DashScope -> p.apiKey; is ASRProviderSetting.DashScopeStreaming -> p.apiKey
                            is ASRProviderSetting.Volcengine -> p.apiKey; is ASRProviderSetting.MiMo -> p.apiKey; is ASRProviderSetting.Step -> p.apiKey
                        }
                        if (key.isBlank()) throw AssistantFailure(AssistantIssue.STT_UNAVAILABLE)
                    }
                    val tts = if (config.ttsProviderId.isBlank()) TTSProviderSetting.SystemTTS() else settings.ttsProviders.firstOrNull { it.id.toString() == config.ttsProviderId }
                        ?: throw AssistantFailure(AssistantIssue.TTS_UNAVAILABLE)
                    if (tts is TTSProviderSetting.SystemTTS) {
                        val cap = queryAssistantTts(app, config.systemTtsEngine, config.ttsTag(app.assistantAppLanguage()))
                        mutable.update { it.copy(tts = cap) }
                        if (!cap.languageSupported) throw AssistantFailure(AssistantIssue.TTS_LANGUAGE)
                        if (config.systemTtsVoice.isNotBlank() && cap.voices.none { it.name == config.systemTtsVoice }) throw AssistantFailure(AssistantIssue.TTS_VOICE)
                    }
                    store.updateVoiceAssistant { it.copy(enabled = true) }; clearIssue()
                }
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { report(e) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun enableWake(value: Boolean) {
        viewModelScope.launch {
            try {
                if (value) AssistantWakeService.enableWake(app, store)
                else { store.updateVoiceAssistant { it.copy(wakeEnabled = false) }; AssistantRuntime.update(WakeStatus.STOPPED) }
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { report(e) }
        }
    }
    fun newRecording(target: Int) {
        if (mutable.value.recording || mutable.value.training) return
        require(target in setOf(5, 10))
        stopPreview()
        samples.clear(); mutable.update { it.copy(count = 0, target = target, quality = null) }
        // The existing committed profile is kept until the new recording is trained or explicitly removed.
    }
    fun record(index: Int? = null) {
        if (mutable.value.recording || mutable.value.training || (index == null && samples.size >= mutable.value.target)) return
        stopPreview(); clearIssue()
        val revision = ++recordRevision
        recording = viewModelScope.launch {
            try {
                store.updateVoiceAssistant { it.copy(wakeEnabled = false) }
                mutable.update { it.copy(recording = true, level = 0f, quality = null) }
                val sample = recordWakeSample(app) { level -> if (revision == recordRevision) mutable.update { it.copy(level = level) } }
                currentCoroutineContext().ensureActive()
                val remaining = if (index != null) samples.filterIndexed { i, _ -> i != index } else samples.toList()
                if (!AcousticWakeEngine.consistent(sample, remaining)) throw WakeSampleException(SampleProblem.INCONSISTENT)
                if (index == null) samples.add(sample) else samples[index] = sample
                val cfg = store.settingsFlow.value.voiceAssistant
                profiles.saveDraft(cfg.wakePhrase, cfg.wakeTag(app.assistantAppLanguage()), samples.toList())
                mutable.update { it.copy(count = samples.size, profile = null) }
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: WakeSampleException) { mutable.update { it.copy(quality = e.problem) } }
            catch (e: Throwable) { report(e) }
            finally { if (revision == recordRevision) mutable.update { it.copy(recording = false, level = 0f) } }
        }
    }
    fun stopRecording() { recordRevision++; recording?.cancel(); recording = null; mutable.update { it.copy(recording = false, level = 0f) } }
    fun train() {
        if (mutable.value.recording || mutable.value.training || samples.size != mutable.value.target) return
        clearIssue(); mutable.update { it.copy(training = true) }
        trainingJob = viewModelScope.launch {
            try {
                store.updateVoiceAssistant { it.copy(wakeEnabled = false) }
                val cfg = store.settingsFlow.value.voiceAssistant
                val engine = WakeEngines.find(cfg.wakeEngine) ?: throw AssistantFailure(AssistantIssue.WAKE_ENGINE)
                val data = samples.map { it.copyOf() }
                val profile = withContext(Dispatchers.Default) { WakeEngines.train(engine, cfg.wakePhrase, cfg.wakeTag(app.assistantAppLanguage()), data) }
                currentCoroutineContext().ensureActive(); profiles.save(profile, data)
                mutable.update { it.copy(profile = profile, quality = null) }
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: WakeSampleException) { mutable.update { it.copy(quality = e.problem) } }
            catch (e: Throwable) { report(e) }
            finally { mutable.update { it.copy(training = false) } }
        }
    }
    fun remove(index: Int? = null) {
        stopRecording(); stopPreview()
        val oldTraining = trainingJob; oldTraining?.cancel()
        viewModelScope.launch {
            try {
                oldTraining?.join()
                store.updateVoiceAssistant { it.copy(wakeEnabled = false) }
                if (index == null) { profiles.remove(); samples.clear() }
                else {
                    samples.removeAt(index)
                    val cfg = store.settingsFlow.value.voiceAssistant
                    profiles.saveDraft(cfg.wakePhrase, cfg.wakeTag(app.assistantAppLanguage()), samples.toList())
                }
                mutable.update { it.copy(count = samples.size, profile = null, quality = null) }
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { report(e) }
        }
    }
    fun changePhrase(phrase: String) {
        if (phrase.isBlank() || phrase.length > 64) return
        stopRecording(); stopPreview()
        val oldTraining = trainingJob; oldTraining?.cancel()
        viewModelScope.launch {
            try {
                oldTraining?.join()
                store.updateVoiceAssistant { it.copy(wakeEnabled = false, wakePhrase = phrase.trim()) }
                samples.clear()
                // Keep old personal data unless the user explicitly removes it; mismatch cannot wake.
                mutable.update { it.copy(count = 0, quality = null) }
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { report(e) }
        }
    }
    fun probeTts() {
        probe?.cancel()
        probe = viewModelScope.launch {
            mutable.update { it.copy(probing = true) }
            try {
                val cfg = store.settingsFlow.value.voiceAssistant
                val cap = queryAssistantTts(app, cfg.systemTtsEngine, cfg.ttsTag(app.assistantAppLanguage()))
                mutable.update { it.copy(tts = cap) }
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { report(e, AssistantIssue.TTS_UNAVAILABLE) }
            finally { mutable.update { it.copy(probing = false) } }
        }
    }
    fun previewSample(index: Int) {
        if (mutable.value.recording || mutable.value.training) return
        stopPreview()
        val data = samples.getOrNull(index)?.copyOf() ?: return
        val revision = previewRevision
        previewJob = viewModelScope.launch {
            var lease: AssistantMicrophone.Lease? = null
            try {
                withTimeout(3000) { lease = AssistantMicrophone.acquire() }
                mutable.update { it.copy(previewing = true) }
                val bytes = ByteArray(data.size * 2)
                data.forEachIndexed { i, s -> bytes[i * 2] = s.toByte(); bytes[i * 2 + 1] = (s.toInt() shr 8).toByte() }
                previewAudio.play(TTSResponse(bytes, AudioFormat.PCM, sampleRate = 16000))
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { report(e) }
            finally { if (revision == previewRevision) { previewAudio.stop(); mutable.update { it.copy(previewing = false) } }; lease?.close() }
        }
    }
    fun previewVoice(text: String) {
        if (mutable.value.recording || mutable.value.training) return
        stopPreview()
        val revision = previewRevision
        previewJob = viewModelScope.launch {
            var lease: AssistantMicrophone.Lease? = null
            try {
                withTimeout(3000) { lease = AssistantMicrophone.acquire() }
                settingWrite?.join()
                val settings = store.settingsFlow.value; val cfg = settings.voiceAssistant
                val base = if (cfg.ttsProviderId.isBlank()) TTSProviderSetting.SystemTTS() else settings.ttsProviders.firstOrNull { it.id.toString() == cfg.ttsProviderId }
                    ?: throw AssistantFailure(AssistantIssue.TTS_UNAVAILABLE)
                val provider = base.assistantVoice(cfg, app.assistantAppLanguage())
                previewTts.setProvider(provider); mutable.update { it.copy(previewing = true) }; previewTts.speak(text)
                combine(previewTts.playbackState, previewTts.isSpeaking, previewTts.error) { playback, speaking, error -> Triple(playback, speaking, error) }
                    .first { (playback, speaking, error) -> if (error != null) throw IllegalStateException(error); assistantPlaybackFinished(playback.status, speaking) }
            } catch (e: TimeoutCancellationException) { report(e) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { report(e) }
            finally { if (revision == previewRevision) { previewTts.stop(); mutable.update { it.copy(previewing = false) } }; lease?.close() }
        }
    }
    fun stopPreview() { previewRevision++; previewJob?.cancel(); previewJob = null; previewAudio.stop(); previewTts.stop(); mutable.update { it.copy(previewing = false) } }
    private fun report(error: Throwable, fallback: AssistantIssue = AssistantIssue.MICROPHONE_BUSY) {
        mutable.update { it.copy(issue = when (error) { is AssistantFailure -> error.issue; is SecurityException -> AssistantIssue.MICROPHONE_PERMISSION; is OutOfMemoryError -> AssistantIssue.MEMORY; else -> fallback },
            detail = if (error is AssistantFailure) null else app.zeroAgentLocalizedContext().localizedErrorMessage(error)) }
    }
    override fun onCleared() { stopRecording(); stopPreview(); previewAudio.release(); previewTts.dispose() }
}
