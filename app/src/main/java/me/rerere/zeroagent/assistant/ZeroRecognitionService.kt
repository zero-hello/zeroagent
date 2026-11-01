package me.rerere.zeroagent.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.*
import kotlinx.coroutines.*

/** Real system-recognition bridge. It never delegates to its own component. */
class ZeroRecognitionService : RecognitionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speech: SpeechRecognizer? = null
    private var pending: Job? = null
    private var lease: AssistantMicrophone.Lease? = null
    private var revision = 0
    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        clear(); val epoch = revision
        pending = scope.launch {
            try {
                withTimeout(3000) { lease = AssistantMicrophone.acquire() }
                if (epoch != revision) return@launch
                val offline = recognizerIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                val useLocal = offline && SpeechRecognizer.isOnDeviceRecognitionAvailable(this@ZeroRecognitionService)
                val component = if (!useLocal) externalRecognitionService(this@ZeroRecognitionService) else null
                if (!useLocal && (offline || component == null)) { listener.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE); clear(); return@launch }
                speech = if (useLocal) SpeechRecognizer.createOnDeviceSpeechRecognizer(this@ZeroRecognitionService)
                    else SpeechRecognizer.createSpeechRecognizer(this@ZeroRecognitionService, component)
                speech!!.setRecognitionListener(object : RecognitionListener {
                    private fun relay(action: () -> Unit) { if (epoch == revision) runCatching(action).onFailure { clear() } }
                    override fun onReadyForSpeech(params: Bundle?) = relay { listener.readyForSpeech(params ?: Bundle()) }
                    override fun onBeginningOfSpeech() = relay { listener.beginningOfSpeech() }
                    override fun onRmsChanged(rmsdB: Float) = relay { listener.rmsChanged(rmsdB) }
                    override fun onBufferReceived(buffer: ByteArray?) = relay { if (buffer != null) listener.bufferReceived(buffer) }
                    override fun onEndOfSpeech() = relay { listener.endOfSpeech() }
                    override fun onError(error: Int) { if (epoch != revision) return; relay { listener.error(error) }; clear() }
                    override fun onResults(results: Bundle?) { if (epoch != revision) return; relay { listener.results(results ?: Bundle()) }; clear() }
                    override fun onPartialResults(partialResults: Bundle?) = relay { listener.partialResults(partialResults ?: Bundle()) }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speech!!.startListening(Intent(recognizerIntent))
            } catch (e: CancellationException) { if (epoch == revision) { lease?.close(); lease = null }; throw e }
            catch (_: SecurityException) { runCatching { listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }; clear() }
            catch (_: Exception) { runCatching { listener.error(SpeechRecognizer.ERROR_CLIENT) }; clear() }
        }
    }
    override fun onStopListening(listener: Callback) { runCatching { speech?.stopListening() } }
    override fun onCancel(listener: Callback) = clear()
    private fun clear() {
        revision++; pending?.cancel(); pending = null
        runCatching { speech?.cancel() }; runCatching { speech?.destroy() }; speech = null
        lease?.close(); lease = null
    }
    override fun onDestroy() { clear(); scope.cancel(); super.onDestroy() }
}
