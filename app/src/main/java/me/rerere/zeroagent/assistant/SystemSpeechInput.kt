package me.rerere.zeroagent.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal enum class AssistantIssue {
    DISABLED, MICROPHONE_PERMISSION, NOTIFICATION_PERMISSION, MICROPHONE_BUSY, STT_UNAVAILABLE,
    STT_LOCAL_UNAVAILABLE, STT_LANGUAGE, STT_NETWORK, NO_SPEECH, STT_FAILED, TTS_UNAVAILABLE,
    TTS_LANGUAGE, TTS_VOICE, TTS_FAILED, MODEL_UNAVAILABLE, MODEL_FAILED, APPROVAL, NETWORK,
    WAKE_UNTRAINED, WAKE_CORRUPT, WAKE_PHRASE_CHANGED, ROLE_REQUIRED, LOCKED, SERVICE_STOPPED,
    MEMORY, BUSY, AUDIO_FOCUS, WAKE_ENGINE,
}
internal class AssistantFailure(val issue: AssistantIssue) : IllegalStateException(issue.name)

/** The bridge is excluded explicitly so selecting ZA as assistant never recursively selects itself. */
internal fun externalRecognitionService(context: Context): ComponentName? {
    val configured = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        ?.let(ComponentName::unflattenFromString)?.takeIf { it.packageName != context.packageName }
    val available = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        .map { it.serviceInfo }.filter { it.packageName != context.packageName && it.enabled && it.exported }
    return configured?.takeIf { c -> available.any { it.packageName == c.packageName && it.name == c.className } }
        ?: available.firstOrNull()?.let { ComponentName(it.packageName, it.name) }
}

internal class SystemSpeechInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var revision = 0
    fun start(language: String, networkAllowed: Boolean, onText: (String) -> Unit, onLevel: (Float) -> Unit,
              onFinal: (String) -> Unit, onError: (AssistantIssue) -> Unit) {
        close()
        val epoch = revision
        val local = !networkAllowed && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!local && !networkAllowed) { onError(AssistantIssue.STT_LOCAL_UNAVAILABLE); return }
        val external = if (!local) externalRecognitionService(context) else null
        if (!local && external == null) { onError(AssistantIssue.STT_UNAVAILABLE); return }
        try {
            val speech = if (local) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context, external)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                private fun valid() = epoch == revision
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { if (valid()) onLevel(((rmsdB + 2f) / 14f).coerceIn(0f, 1f)) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (!valid()) return
                    onError(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> AssistantIssue.MICROPHONE_PERMISSION
                        SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> AssistantIssue.MICROPHONE_BUSY
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> AssistantIssue.STT_NETWORK
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> AssistantIssue.STT_LANGUAGE
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> AssistantIssue.NO_SPEECH
                        else -> AssistantIssue.STT_FAILED
                    })
                }
                override fun onResults(results: Bundle?) {
                    if (!valid()) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    if (text.isBlank()) onError(AssistantIssue.NO_SPEECH) else onFinal(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (valid()) onText(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !networkAllowed)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
            })
        } catch (_: SecurityException) { onError(AssistantIssue.MICROPHONE_PERMISSION) }
        catch (_: Exception) { onError(AssistantIssue.STT_UNAVAILABLE) }
    }
    fun finish() { runCatching { recognizer?.stopListening() } }
    fun close() { revision++; runCatching { recognizer?.cancel() }; runCatching { recognizer?.destroy() }; recognizer = null }
}
