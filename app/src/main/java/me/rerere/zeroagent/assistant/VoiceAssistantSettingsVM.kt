package me.rerere.zeroagent.assistant

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.rerere.tts.controller.AudioPlayer
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import me.rerere.zeroagent.assistant.wake.*
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.nativeVoiceSelection
import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext

internal data class AssistantSetupState(
    val busy: Boolean = false, val ready: Boolean = false, val count: Int = 0, val target: Int = 5,
    val profile: WakeProfile? = null, val recording: Boolean = false, val training: Boolean = false,
    val level: Float = 0f, val quality: SampleProblem? = null, val issue: AssistantIssue? = null,
    val detail: String? = null, val previewing: Boolean = false,
    val phase: WakeCapturePhase = WakeCapturePhase.LISTENING, val saveFailed: Boolean = false,
    val acceptedSeconds: Float? = null, val acceptedSnr: Float? = null,
)

internal class VoiceAssistantSettingsVM(application: Application, private val store: SettingsStore) : AndroidViewModel(application) {
    val settings = store.settingsFlow
    private val app = application
    private val profiles = WakeProfileStore(app)
    private val enrollment = WakeEnrollment()
    private val samples get() = enrollment.samples
    private val mutable = MutableStateFlow(AssistantSetupState())
    val state = mutable.asStateFlow()
    private var recording: Job? = null
    private var trainingJob: Job? = null
    private var recordRevision = 0
    private var previewRevision = 0
    private var previewJob: Job? = null
    private var settingWrite: Job? = null
    private val previewAudio = AudioPlayer(app)

    init { viewModelScope.launch { store.settingsFlow.first { !it.init }; reload() } }
    private suspend fun reload() {
        try {
            val saved = profiles.snapshot()
            enrollment.restore(if (saved.data.phrase == VoiceAssistantSettings.FIXED_WAKE_PHRASE) saved.samples else emptyList())
            mutable.update { it.copy(ready = true, count = samples.size, target = saved.data.target,
                profile = saved.data.profile?.takeIf { p -> p.phrase == VoiceAssistantSettings.FIXED_WAKE_PHRASE }) }
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) { report(error, AssistantIssue.WAKE_CORRUPT); mutable.update { it.copy(ready = true) } }
    }
    fun update(change: (VoiceAssistantSettings) -> VoiceAssistantSettings) {
        settingWrite = viewModelScope.launch {
            try { store.updateVoiceAssistant { change(it).copy(wakePhrase = VoiceAssistantSettings.FIXED_WAKE_PHRASE) }
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { report(error) }
        }
    }
    fun permissionDenied(issue: AssistantIssue) { mutable.update { it.copy(issue = issue, detail = null) } }
    fun clearIssue() { mutable.update { it.copy(issue = null, detail = null, quality = null, saveFailed = false) } }
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
                    if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        throw AssistantFailure(AssistantIssue.MICROPHONE_PERMISSION)
                    if (store.settingsFlow.value.nativeVoiceSelection() == null) throw AssistantFailure(AssistantIssue.MODEL_UNAVAILABLE)
                    store.updateVoiceAssistant { it.copy(enabled = true) }; clearIssue()
                }
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { report(error)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun enableWake(value: Boolean) {
        viewModelScope.launch {
            try {
                if (value) AssistantWakeService.enableWake(app, store)
                else { store.updateVoiceAssistant { it.copy(wakeEnabled = false) }; AssistantRuntime.update(WakeStatus.STOPPED) }
            } catch (timeout: TimeoutCancellationException) { report(timeout)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { report(error) }
        }
    }
    private suspend fun persist(next: List<ShortArray>, target: Int, qualified: QualifiedSample? = null) {
        currentCoroutineContext().ensureActive()
        mutable.update { it.copy(busy = true) }
        try {
            // Once publication starts, finish the small disk/Main transaction even if the page stops.
            withContext(NonCancellable) {
                val config = store.settingsFlow.value.voiceAssistant
                enrollment.commit(next) { profiles.saveDraft(VoiceAssistantSettings.FIXED_WAKE_PHRASE,
                    config.wakeTag(app.assistantAppLanguage()), it, target) }
                mutable.update { it.copy(count = samples.size, target = target, profile = null, quality = null,
                    saveFailed = false, issue = null, detail = null, acceptedSeconds = qualified?.seconds, acceptedSnr = qualified?.snrDb) }
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (quality: WakeSampleException) { throw quality
        } catch (error: Exception) {
            mutable.update { it.copy(saveFailed = true) }; throw error
        } finally { mutable.update { it.copy(busy = false) } }
    }
    fun newRecording(target: Int) {
        if (!mutable.value.ready || mutable.value.busy || mutable.value.recording || mutable.value.training) return
        require(target == 5 || target == 10)
        stopPreview(); mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { store.updateVoiceAssistant { it.copy(wakeEnabled = false) }; persist(emptyList(), target)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { report(error, AssistantIssue.WAKE_CORRUPT)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun record(index: Int? = null) {
        if (!mutable.value.ready || mutable.value.busy || mutable.value.recording || mutable.value.training ||
            (index == null && samples.size >= mutable.value.target) || (index != null && index !in samples.indices)) return
        stopPreview(); clearIssue()
        val revision = ++recordRevision
        mutable.update { it.copy(recording = true, level = 0f, phase = WakeCapturePhase.CALIBRATING) }
        recording = viewModelScope.launch {
            try {
                store.updateVoiceAssistant { it.copy(wakeEnabled = false) }
                val remaining = if (index == null) mutable.value.target - samples.size else 1
                recordWakeSamples(app, remaining * 15_000L + 15_000,
                    onLevel = { level, phase -> withContext(Dispatchers.Main.immediate) {
                        if (revision == recordRevision) mutable.update { it.copy(level = level, phase = phase) }
                    } },
                    onRejected = { problem -> withContext(Dispatchers.Main.immediate) {
                        if (revision == recordRevision) mutable.update { it.copy(quality = problem) }
                    } },
                    onSample = { sample -> withContext(Dispatchers.Main.immediate) {
                        currentCoroutineContext().ensureActive()
                        check(revision == recordRevision)
                        val prior = if (index == null) samples else samples.filterIndexed { i, _ -> i != index }
                        val qualified = withContext(Dispatchers.Default) {
                            val quality = WakeSampleQuality.inspect(sample)
                            if (!AcousticWakeEngine.consistent(sample, prior)) throw WakeSampleException(SampleProblem.INCONSISTENT)
                            quality
                        }
                        val next = samples.toMutableList().apply { if (index == null) add(sample) else this[index] = sample }
                        persist(next, mutable.value.target, qualified)
                        index == null && samples.size < mutable.value.target
                    } })
            } catch (timeout: TimeoutCancellationException) { mutable.update { it.copy(quality = SampleProblem.NO_SPEECH) }
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { report(error, if (mutable.value.saveFailed) AssistantIssue.WAKE_CORRUPT else AssistantIssue.MICROPHONE_BUSY)
            } finally { if (revision == recordRevision) mutable.update { it.copy(recording = false, level = 0f) } }
        }
    }
    fun stopRecording() { recordRevision++; recording?.cancel(); recording = null; mutable.update { it.copy(recording = false, level = 0f) } }
    fun train() {
        if (mutable.value.busy || mutable.value.recording || mutable.value.training || samples.size != mutable.value.target) return
        clearIssue(); mutable.update { it.copy(training = true) }
        trainingJob = viewModelScope.launch {
            try {
                store.updateVoiceAssistant { it.copy(wakeEnabled = false) }
                val config = store.settingsFlow.value.voiceAssistant
                val engine = WakeEngines.find(config.wakeEngine) ?: throw AssistantFailure(AssistantIssue.WAKE_ENGINE)
                val data = samples.map { it.copyOf() }
                val profile = withContext(Dispatchers.Default) { WakeEngines.train(engine, VoiceAssistantSettings.FIXED_WAKE_PHRASE,
                    config.wakeTag(app.assistantAppLanguage()), data) }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) { profiles.save(profile, data); mutable.update { it.copy(profile = profile, quality = null) } }
            } catch (cancel: CancellationException) { throw cancel
            } catch (quality: WakeSampleException) { mutable.update { it.copy(quality = quality.problem) }
            } catch (error: Exception) { report(error, AssistantIssue.WAKE_CORRUPT)
            } finally { mutable.update { it.copy(training = false) } }
        }
    }
    fun remove(index: Int? = null) {
        if (mutable.value.busy) return
        val oldRecording = recording; val oldTraining = trainingJob
        stopRecording(); stopPreview(); oldTraining?.cancel()
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                oldRecording?.join(); oldTraining?.join()
                store.updateVoiceAssistant { it.copy(wakeEnabled = false) }
                if (index == null) withContext(NonCancellable) {
                    enrollment.commit(emptyList()) { profiles.remove() }
                    mutable.update { it.copy(count = 0, profile = null, quality = null) }
                } else persist(samples.filterIndexed { i, _ -> i != index }, mutable.value.target)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { report(error, AssistantIssue.WAKE_CORRUPT)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun previewSample(index: Int) {
        if (mutable.value.busy || mutable.value.recording || mutable.value.training) return
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
            } catch (timeout: TimeoutCancellationException) { report(timeout)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { report(error)
            } finally { if (revision == previewRevision) { previewAudio.stop(); mutable.update { it.copy(previewing = false) } }; lease?.close() }
        }
    }
    fun stopPreview() { previewRevision++; previewJob?.cancel(); previewJob = null; previewAudio.stop(); mutable.update { it.copy(previewing = false) } }
    private fun report(error: Throwable, fallback: AssistantIssue = AssistantIssue.MICROPHONE_BUSY) {
        mutable.update { it.copy(issue = when (error) { is AssistantFailure -> error.issue; is SecurityException -> AssistantIssue.MICROPHONE_PERMISSION; is OutOfMemoryError -> AssistantIssue.MEMORY; else -> fallback },
            detail = if (error is AssistantFailure) null else app.zeroAgentLocalizedContext().localizedErrorMessage(error)) }
    }
    override fun onCleared() { stopRecording(); stopPreview(); previewAudio.release() }
}
