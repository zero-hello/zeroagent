package me.rerere.zeroagent.assistant

import android.content.Context
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import java.util.Locale

internal fun Context.assistantAppLanguage(): String = zeroAgentLocalizedContext().resources.configuration.locales[0].toLanguageTag()

internal fun me.rerere.tts.provider.TTSProviderSetting.assistantVoice(config: VoiceAssistantSettings, appTag: String): me.rerere.tts.provider.TTSProviderSetting = when (this) {
    is me.rerere.tts.provider.TTSProviderSetting.SystemTTS -> copy(languageTag = config.ttsTag(appTag), enginePackage = config.systemTtsEngine,
        voiceName = config.systemTtsVoice, speechRate = config.speechRate, pitch = config.pitch)
    is me.rerere.tts.provider.TTSProviderSetting.XAI -> {
        val tag = config.ttsTag(appTag); val locale = Locale.forLanguageTag(tag)
        val code = when (locale.language) {
            "es" -> if (locale.country == "MX") "es-MX" else "es-ES"
            "pt" -> if (locale.country == "PT") "pt-PT" else "pt-BR"
            "ar" -> "ar-EG"
            else -> locale.language
        }
        copy(language = code)
    }
    // Other installed adapters choose pronunciation from text/voice and have no language parameter.
    else -> this
}
