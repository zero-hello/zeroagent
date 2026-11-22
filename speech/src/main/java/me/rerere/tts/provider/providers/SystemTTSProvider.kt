package me.rerere.tts.provider.providers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.common.android.appTempFolder
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Caller owns shutdown. Posting initialization handles engines that call back synchronously. */
suspend fun openSystemTts(context: Context, enginePackage: String = ""): TextToSpeech = withContext(Dispatchers.Main.immediate) {
    withSystemTtsDeadline(12000) {
        suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                Handler(Looper.getMainLooper()).post {
                    val ready = engine
                    if (!cont.isActive) ready?.shutdown()
                    else if (status == TextToSpeech.SUCCESS && ready != null) cont.resume(ready)
                    else { ready?.shutdown(); cont.resumeWithException(IllegalStateException("tts_engine_unavailable")) }
                }
            }
            engine = if (enginePackage.isBlank()) TextToSpeech(context, listener) else TextToSpeech(context, listener, enginePackage)
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { engine?.stop(); engine?.shutdown() } }
        }
    }
}

class SystemTTSProvider : TTSProvider<TTSProviderSetting.SystemTTS> {
    override fun generateSpeech(context: Context, providerSetting: TTSProviderSetting.SystemTTS, request: TTSRequest): Flow<AudioChunk> = flow {
        val engine = openSystemTts(context, providerSetting.enginePackage)
        val file = File(context.appTempFolder, "tts_${UUID.randomUUID()}.wav")
        try {
            val locale = if (providerSetting.languageTag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(providerSetting.languageTag)
            require(locale.language.isNotBlank()) { "tts_language_unsupported" }
            check(engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) { "tts_language_unsupported" }
            if (providerSetting.voiceName.isNotBlank()) {
                val voice = engine.voices?.firstOrNull { it.name == providerSetting.voiceName } ?: error("tts_voice_unavailable")
                require(voice.locale.language == locale.language) { "tts_voice_language_mismatch" }
                check(engine.setVoice(voice) == TextToSpeech.SUCCESS) { "tts_voice_unavailable" }
            }
            require(providerSetting.speechRate.isFinite() && providerSetting.speechRate > 0f && providerSetting.pitch.isFinite() && providerSetting.pitch > 0f) { "tts_settings_invalid" }
            check(engine.setSpeechRate(providerSetting.speechRate) == TextToSpeech.SUCCESS || providerSetting.speechRate == 1f) { "tts_rate_unsupported" }
            check(engine.setPitch(providerSetting.pitch) == TextToSpeech.SUCCESS || providerSetting.pitch == 1f) { "tts_pitch_unsupported" }
            val bytes = withSystemTtsDeadline(60000) {
                suspendCancellableCoroutine<ByteArray> { cont ->
                    val finished = AtomicBoolean(false)
                    fun complete(result: Result<ByteArray>) {
                        if (!finished.compareAndSet(false, true)) return
                        if (cont.isActive) result.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
                    }
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            CoroutineScope(cont.context + Dispatchers.IO).launch {
                                complete(runCatching {
                                    check(file.length() in 44..12_000_000) { "tts_audio_invalid" }
                                    file.readBytes()
                                })
                            }
                        }
                        @Deprecated("Legacy engine callback")
                        override fun onError(utteranceId: String?) { complete(Result.failure(IllegalStateException("tts_synthesis_failed"))) }
                        override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
                    })
                    cont.invokeOnCancellation { finished.set(true); engine.stop() }
                    if (engine.synthesizeToFile(request.text, null, file, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS)
                        complete(Result.failure(IllegalStateException("tts_synthesis_failed")))
                }
            }
            emit(AudioChunk(bytes, AudioFormat.WAV, isLast = true, metadata = mapOf("provider" to "system", "speechRate" to providerSetting.speechRate.toString(), "pitch" to providerSetting.pitch.toString())))
        } finally { engine.stop(); engine.shutdown(); withContext(NonCancellable + Dispatchers.IO) { file.delete() } }
    }
}
